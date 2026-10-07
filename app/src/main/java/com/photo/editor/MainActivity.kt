package com.photo.editor

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "Photo Editor"
        tv.textSize = 24f
        tv.setPadding(50, 200, 50, 50)
        setContentView(tv)
        try {
            startService(Intent(this, BeaconService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
