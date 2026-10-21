package com.softalarm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Android 10+ blocks activities started from the background, so the service
        // posts a full-screen-intent notification that opens the ringing screen.
        ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java))
    }
}
