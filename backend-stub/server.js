// SoftAlarm payment backend (small stub). Needs Node 18+, no npm packages.
//
// What it does:
//   1) POST /webhook/paddle  <- Paddle tells us a payment finished (signature is checked)
//   2) GET  /status?device=  <- the app asks "has this phone paid?"
//   3) POST /restore         <- the app restores a purchase using the Paddle transaction id
//   4) GET  /pro.html        <- the checkout page that opens Paddle
//
// Settings (environment variables):
//   PADDLE_WEBHOOK_SECRET   the "secret key" of your Paddle notification destination
//   PORT                    optional, default 3000
//   DATA_FILE               optional, where purchases are saved (default ./data.json)
//
// NOTE: data.json is a simple file. Many free hosts wipe files on every redeploy, so
// before real customers pay, keep it on a persistent disk or swap save()/load for a database.

const http = require('http');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 3000;
const WEBHOOK_SECRET = process.env.PADDLE_WEBHOOK_SECRET || '';
const DATA_FILE = process.env.DATA_FILE || path.join(__dirname, 'data.json');
const MAX_DEVICES_PER_PURCHASE = 3; // one purchase can restore on up to 3 phones

let db = { devices: {}, purchases: {} }; // devices[deviceId] = txnId, purchases[txnId] = [deviceIds]
try { db = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8')); } catch (e) { /* first run */ }
function save() { fs.writeFileSync(DATA_FILE, JSON.stringify(db)); }

const DEVICE_RE = /^[a-f0-9-]{8,64}$/i;
const TXN_RE = /^txn_[a-z0-9]{10,40}$/i;

// Paddle-Signature header looks like:  ts=1671552777;h1=abcdef...
function verifySignature(rawBody, header) {
  if (!WEBHOOK_SECRET || !header) return false;
  const parts = {};
  header.split(';').forEach(p => { const i = p.indexOf('='); parts[p.slice(0, i)] = p.slice(i + 1); });
  if (!parts.ts || !parts.h1) return false;
  if (Math.abs(Date.now() / 1000 - Number(parts.ts)) > 300) return false; // too old
  const expected = crypto.createHmac('sha256', WEBHOOK_SECRET).update(parts.ts + ':' + rawBody).digest('hex');
  const a = Buffer.from(expected), b = Buffer.from(parts.h1);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', c => { data += c; if (data.length > 1e6) { reject(new Error('too big')); req.destroy(); } });
    req.on('end', () => resolve(data));
    req.on('error', reject);
  });
}

function send(res, code, obj) {
  res.writeHead(code, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(obj));
}

function link(device, txn) {
  db.devices[device] = txn;
  if (!db.purchases[txn]) db.purchases[txn] = [];
  if (!db.purchases[txn].includes(device)) db.purchases[txn].push(device);
  save();
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://localhost');

    if (req.method === 'POST' && url.pathname === '/webhook/paddle') {
      const raw = await readBody(req);
      if (!verifySignature(raw, req.headers['paddle-signature'])) return send(res, 401, { ok: false });
      const event = JSON.parse(raw);
      if (event.event_type === 'transaction.completed') {
        const d = event.data || {};
        const device = d.custom_data && d.custom_data.device_id;
        if (d.id && device && DEVICE_RE.test(device)) link(device, d.id);
      }
      return send(res, 200, { ok: true });
    }

    if (req.method === 'GET' && url.pathname === '/status') {
      const device = url.searchParams.get('device') || '';
      return send(res, 200, { pro: DEVICE_RE.test(device) && !!db.devices[device] });
    }

    if (req.method === 'POST' && url.pathname === '/restore') {
      const body = JSON.parse((await readBody(req)) || '{}');
      const { txn, device } = body;
      if (!TXN_RE.test(txn || '') || !DEVICE_RE.test(device || '')) return send(res, 400, { pro: false });
      const list = db.purchases[txn];
      if (!list) return send(res, 200, { pro: false });
      if (!list.includes(device) && list.length >= MAX_DEVICES_PER_PURCHASE) return send(res, 403, { pro: false });
      link(device, txn);
      return send(res, 200, { pro: true });
    }

    if (req.method === 'GET' && (url.pathname === '/pro.html' || url.pathname === '/')) {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      return res.end(fs.readFileSync(path.join(__dirname, 'public', 'pro.html')));
    }

    send(res, 404, { error: 'not found' });
  } catch (e) {
    send(res, 500, { error: 'server error' });
  }
});

server.listen(PORT, () => console.log('SoftAlarm backend running on port ' + PORT));
