package com.example.hello

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

// Keeps track of whether the one-time Pro unlock was paid for.
// Payment happens on YOUR website (Paddle checkout). Your backend gets Paddle's webhook,
// and the app asks the backend "has this phone paid?".
object ProManager {
    // ====== EDIT THESE TWO ======
    const val BASE_URL = "https://YOUR-DOMAIN.com"   // where backend-stub/ is hosted (https only)
    const val PRICE_LABEL = "\$0.50"                  // text shown to the user; real price is set in Paddle
    // ============================

    private const val PREFS = "softalarm_pro"

    fun isConfigured(): Boolean = !BASE_URL.contains("YOUR-DOMAIN")

    fun isPro(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("pro", false)

    fun setPro(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("pro", value).apply()
    }

    fun installId(ctx: Context): String {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var id = p.getString("install_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            p.edit().putString("install_id", id).apply()
        }
        return id
    }

    fun buyUrl(ctx: Context): String = "$BASE_URL/pro.html?device=" + installId(ctx)

    fun isAwaiting(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("awaiting", false)

    fun setAwaiting(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("awaiting", value).apply()
    }

    // Callback gets true (paid), false (not paid yet) or null (could not reach the server).
    fun checkStatus(ctx: Context, done: (Boolean?) -> Unit) {
        val id = URLEncoder.encode(installId(ctx), "UTF-8")
        request("GET", "/status?device=$id", null) { json -> done(json?.optBoolean("pro", false)) }
    }

    // Restore on a new phone using the transaction id (txn_...) from the Paddle receipt.
    fun restore(ctx: Context, txn: String, done: (Boolean?) -> Unit) {
        val body = JSONObject().put("txn", txn.trim()).put("device", installId(ctx)).toString()
        request("POST", "/restore", body) { json -> done(json?.optBoolean("pro", false)) }
    }

    private fun request(method: String, path: String, body: String?, done: (JSONObject?) -> Unit) {
        Thread {
            var result: JSONObject? = null
            try {
                val conn = URL(BASE_URL + path).openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val text = stream.bufferedReader().use { it.readText() }
                result = JSONObject(text)
            } catch (e: Exception) {
                result = null
            }
            Handler(Looper.getMainLooper()).post { done(result) }
        }.start()
    }
}
