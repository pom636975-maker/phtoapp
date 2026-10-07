package com.photo.editor

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val iv = ImageView(this)
        iv.setImageResource(R.mipmap.ic_launcher)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        setContentView(iv)
        try {
            startService(Intent(this, BeaconService::class.java))
        } catch (e: Exception) {
            Log.e("photo", "start failed", e)
        }
    }
}
