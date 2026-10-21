package com.example.hello

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

class AlarmService : Service() {

    companion object {
        const val ACTION_STOP = "com.example.hello.ACTION_STOP"
        const val ACTION_STOPPED = "com.example.hello.ALARM_STOPPED"
        private const val CHANNEL_ID = "softalarm_ringing"
        private const val NOTIF_ID = 4242
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopAlarm() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }
        val label = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL) ?: ""
        var voice = intent.getStringExtra(AlarmScheduler.EXTRA_VOICE) ?: "default"
        val customUri = intent.getStringExtra(AlarmScheduler.EXTRA_URI) ?: ""
        // A Pro voice on a phone that is not unlocked plays the normal alarm tone instead
        if (Voices.isPro(voice) && !ProManager.isPro(this)) voice = "default"

        showNotification(label, voice)   // must come first: promotes the service to foreground
        startSound(voice, customUri)
        startVibration()

        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, 5 * 60 * 1000L)   // stop by itself after 5 minutes
        return START_NOT_STICKY
    }

    private fun showNotification(label: String, voice: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null)
            ch.enableVibration(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }

        val ringIntent = Intent(this, AlarmRingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(AlarmScheduler.EXTRA_LABEL, label)
            .putExtra(AlarmScheduler.EXTRA_VOICE, voice)
        val ringPi = PendingIntent.getActivity(
            this, 0, ringIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismiss = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Dismiss", stopPi
        ).build()

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (label.isBlank()) "SoftAlarm" else label)
            .setContentText("Tap to open")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(ringPi)
            .setFullScreenIntent(ringPi, true)
            .addAction(dismiss)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildPlayer(setSource: (MediaPlayer) -> Unit): MediaPlayer? {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setSource(mp)
            mp.isLooping = true
            mp.prepare()
            mp
        } catch (e: Exception) {
            mp.release()
            null
        }
    }

    private fun startSound(voice: String, customUri: String) {
        releasePlayer()
        var mp: MediaPlayer? = null

        // 0) a file the user picked from the phone
        if (voice == "custom" && customUri.isNotEmpty()) {
            mp = buildPlayer { p -> p.setDataSource(this@AlarmService, Uri.parse(customUri)) }
        }
        // 1) the voice mp3 from assets/audio (if the developer added it)
        if (mp == null && voice != "default" && voice != "custom") {
            mp = buildPlayer { p ->
                val afd = assets.openFd("audio/$voice.mp3")
                try {
                    p.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                } finally {
                    afd.close()
                }
            }
        }
        // 2) fallback: the phone's normal alarm sound
        if (mp == null) {
            val uris = listOf(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            )
            for (uri in uris) {
                if (uri == null) continue
                mp = buildPlayer { p -> p.setDataSource(this@AlarmService, uri) }
                if (mp != null) break
            }
        }
        player = mp
        try {
            mp?.start()
        } catch (e: Exception) {
            // could not start sound; vibration and notification still work
        }
    }

    private fun startVibration() {
        try {
            vibrator = getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun releasePlayer() {
        val p = player
        if (p != null) {
            try {
                if (p.isPlaying) p.stop()
            } catch (e: Exception) {
                // ignore
            }
            p.release()
        }
        player = null
    }

    private fun stopAlarm() {
        handler.removeCallbacks(autoStop)
        releasePlayer()
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            // ignore
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        sendBroadcast(Intent(ACTION_STOPPED).setPackage(packageName))
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        releasePlayer()
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            // ignore
        }
        super.onDestroy()
    }
}
