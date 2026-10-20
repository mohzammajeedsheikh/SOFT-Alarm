package com.softalarm.app

import android.app.*
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        
        val notificationIntent = Intent(this, AlarmRingingActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "alarm_channel")
            .setContentTitle("SoftAlarm")
            .setContentText("Time to wake up gently \uD83C\uDF24?")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        // SOFT ALARM: Gentle default alarm tone with gradual volume
        try {
            mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)
            mediaPlayer?.isLooping = true
            mediaPlayer?.setVolume(0.2f, 0.2f) // Start very soft
            mediaPlayer?.start()
            
            // Gradually increase volume to keep it soft but effective
            Thread {
                for (i in 1..10) {
                    Thread.sleep(2000)
                    val vol = 0.2f + (i * 0.08f)
                    mediaPlayer?.setVolume(vol, vol)
                }
            }.start()

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("alarm_channel", "Alarm", NotificationManager.IMPORTANCE_HIGH)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
*3. Path:* `app/src/main/java/com/softalarm/app/AlarmRingingActivity.kt`
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