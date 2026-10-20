package com.softalarm.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import android.widget.TextView
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var timeText: TextView
    private lateinit var statusText: TextView
    private var selectedHour = 7
    private var selectedMinute = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        timeText = findViewById(R.id.timeText)
        statusText = findViewById(R.id.statusText)
        val setTimeButton: MaterialButton = findViewById(R.id.setTimeButton)
        val setAlarmButton: MaterialButton = findViewById(R.id.setAlarmButton)

        updateTimeDisplay()

        setTimeButton.setOnClickListener {
            showTimePicker()
        }

        setAlarmButton.setOnClickListener {
            setAlarm()
        }
    }

    private fun showTimePicker() {
        TimePickerDialog(this, { _, hour, minute ->
            selectedHour = hour
            selectedMinute = minute
            updateTimeDisplay()
        }, selectedHour, selectedMinute, false).show()
    }

    private fun updateTimeDisplay() {
        val amPm = if (selectedHour >= 12) "PM" else "AM"
        val hour12 = if (selectedHour % 12 == 0) 12 else selectedHour % 12
        timeText.text = String.format("%02d:%02d %s", hour12, selectedMinute, amPm)
    }

    private fun setAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(this, "Please allow exact alarms in settings", Toast.LENGTH_LONG).show()
                return
            }
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        statusText.text = "Alarm set for ${timeText.text} - Soft & Gentle"
        Toast.makeText(this, "Alarm set! Will wake you softly", Toast.LENGTH_SHORT).show()
    }
}