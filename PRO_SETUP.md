# SoftAlarm Pro: payment setup (Paddle)

## How it works
1. The user taps "Unlock Pro" in the app. The app opens https://YOUR-DOMAIN/pro.html?device=<phone id> in the browser.
2. That page opens Paddle checkout. The phone id travels inside Paddle's `custom_data`.
3. After payment, Paddle sends a signed webhook (`transaction.completed`) to your backend.
4. The backend saves "this phone id paid". The app asks /status and unlocks the Pro voices.
   If the user changes phone, "Restore" with the transaction id (txn_...) from the Paddle receipt.

## One-time setup
1. Paddle account: create a product "SoftAlarm Pro" with one one-time price. Turn on automatic
   currency conversion so buyers see their own currency. Copy the price id (pri_...).
2. Paddle > Developer tools > Authentication: create a client-side token.
3. Edit `backend-stub/public/pro.html`: set CLIENT_TOKEN and PRICE_ID (start with PADDLE_ENV = 'sandbox').
4. Deploy `backend-stub/` to any Node 18+ host (Render, Railway, Fly.io, a VPS). It must be https.
   Set the environment variable PADDLE_WEBHOOK_SECRET.
5. Paddle > Developer tools > Notifications: add a destination
   https://YOUR-DOMAIN/webhook/paddle with the event `transaction.completed`. Copy its secret key into step 4.
6. Paddle needs your checkout domain approved before live payments (website with pricing, terms, refund policy).
7. In the app, edit `ProManager.kt`: set BASE_URL (your domain) and PRICE_LABEL (the text users see).
8. Test fully in sandbox, then switch PADDLE_ENV to 'production' and use live keys.

## Important
- Paddle charges 5% + $0.50 per transaction, so a $0.50 price loses money. See the chat notes.
- If you publish on Google Play, digital unlocks must use Google Play Billing, not an outside checkout.
- data.json is a simple file. Use a persistent disk or a database before real customers pay.
