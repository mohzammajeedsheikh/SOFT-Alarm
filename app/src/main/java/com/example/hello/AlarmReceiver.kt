package com.example.hello

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        val label = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL) ?: ""
        val voice = intent.getStringExtra(AlarmScheduler.EXTRA_VOICE) ?: "default"

        // Start the ringing service (plays the voice mp3 and shows the full-screen alarm)
        val svc = Intent(context, AlarmService::class.java)
            .putExtra(AlarmScheduler.EXTRA_LABEL, label)
            .putExtra(AlarmScheduler.EXTRA_VOICE, voice)
            .putExtra(AlarmScheduler.EXTRA_URI, intent.getStringExtra(AlarmScheduler.EXTRA_URI) ?: "")
        context.startForegroundService(svc)

        // Repeating alarms: schedule the next day. One-time alarms: switch off.
        if (id != AlarmScheduler.TIMER_ID) {
            val list = AlarmStore.load(context)
            val alarm = list.firstOrNull { it.id == id }
            if (alarm != null) {
                if (alarm.days.isEmpty()) {
                    alarm.enabled = false
                    AlarmStore.save(context, list)
                } else {
                    AlarmScheduler.schedule(context, alarm)
                }
            }
        }
    }
}
