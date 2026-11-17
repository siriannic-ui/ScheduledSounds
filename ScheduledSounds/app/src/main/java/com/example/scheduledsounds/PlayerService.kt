package com.example.scheduledsounds

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.documentfile.provider.DocumentFile

/** Picks a random audio file from the chosen folder, plays it, and stops after the set time. */
class PlayerService : Service() {

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Playing scheduled sound")
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        stopPlayback()

        val seconds = intent?.getIntExtra("seconds", -1)?.takeIf { it > 0 }
            ?: (Prefs.minutes(this) * 60)

        val file = pickFile()
        if (file == null) {
            finish()
            return START_NOT_STICKY
        }

        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setWakeMode(this@PlayerService, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(this@PlayerService, file.uri)
                setOnCompletionListener { finish() }
                setOnErrorListener { _, _, _ -> finish(); true }
                prepare()
                start()
            }
            handler.postDelayed({ finish() }, seconds * 1000L)
        } catch (e: Exception) {
            finish()
        }
        return START_NOT_STICKY
    }

    private fun pickFile(): DocumentFile? {
        val uriStr = Prefs.folder(this) ?: return null
        val dir = DocumentFile.fromTreeUri(this, Uri.parse(uriStr)) ?: return null
        return dir.listFiles()
            .filter {
                it.isFile && (it.type?.startsWith("audio/") == true ||
                    it.name?.endsWith(".mp3", ignoreCase = true) == true)
            }
            .randomOrNull()
    }

    private fun stopPlayback() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }

    private fun finish() {
        stopPlayback()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "playback"
    }
}
