package com.example.hello

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// After the phone restarts (or the app is updated), set all enabled alarms and a running timer again.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            for (alarm in AlarmStore.load(context)) {
                if (alarm.enabled) AlarmScheduler.schedule(context, alarm)
            }
            val timerEnd = context.getSharedPreferences("softalarm_timer", Context.MODE_PRIVATE)
                .getLong("timer_end", 0L)
            if (timerEnd > System.currentTimeMillis()) AlarmScheduler.scheduleTimer(context, timerEnd)
        }
    }
}
