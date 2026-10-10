package com.photo.editor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                val svc = Intent(context, BeaconService::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(svc)
                } else {
                    context.startService(svc)
                }
            } catch (e: Exception) {
                Log.e("beacon", "boot start failed", e)
            }
        }
    }
}
