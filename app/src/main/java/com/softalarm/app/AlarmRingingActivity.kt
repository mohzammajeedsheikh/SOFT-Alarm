package com.softalarm.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class AlarmRingingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ringing)

        findViewById<Button>(R.id.dismissButton).setOnClickListener {
            stopService(Intent(this, AlarmService::class.java))
            finish()
        }
    }
}
