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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.documentfile.provider.DocumentFile

/** Picks a random audio file from the chosen folder, plays it, and stops after the set time. */
class PlayerService : Service() {

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground quickly, so do it first and guard it.
        try {
            goForeground("Playing scheduled sound")
        } catch (e: Throwable) {
            report("Could not start playback service", e)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            stopPlayback()

            val seconds = intent?.getIntExtra("seconds", -1)?.takeIf { it > 0 }
                ?: (Prefs.minutes(this) * 60)

            val uriStr = Prefs.folder(this)
            if (uriStr == null) {
                report("No music folder chosen. Tap 'Choose folder' first.", null)
                finish()
                return START_NOT_STICKY
            }
            val dir = DocumentFile.fromTreeUri(this, Uri.parse(uriStr))
            if (dir == null || !dir.canRead()) {
                report("Can't read the music folder. Choose it again.", null)
                finish()
                return START_NOT_STICKY
            }
            val files = dir.listFiles().filter {
                it.isFile && (it.type?.startsWith("audio/") == true ||
                    it.name?.endsWith(".mp3", ignoreCase = true) == true)
            }
            if (files.isEmpty()) {
                report("No mp3 files found in the chosen folder.", null)
                finish()
                return START_NOT_STICKY
            }
            val file = files.random()

            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(this@PlayerService, file.uri)
                setOnCompletionListener { finish() }
                setOnErrorListener { _, what, extra ->
                    report("Player error ($what, $extra) on ${file.name}", null)
                    finish(); true
                }
                prepare()
                start()
            }
            goForeground("Playing: ${file.name}")
            Prefs.setLastError(this, null)
            handler.postDelayed({ finish() }, seconds * 1000L)
        } catch (e: Throwable) {
            report("Playback failed", e)
            finish()
        }
        return START_NOT_STICKY
    }

    private fun goForeground(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Scheduled Sounds")
            .setContentText(text)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, n)
        }
    }

    /** Saves the error for the main screen and shows it as a notification. */
    private fun report(msg: String, e: Throwable?) {
        val full = if (e != null) "$msg\n${e.javaClass.simpleName}: ${e.message}\n" +
            Log.getStackTraceString(e).take(2000) else msg
        try {
            Prefs.setLastError(this, full)
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(ERR_CHANNEL, "Errors", NotificationManager.IMPORTANCE_DEFAULT)
            )
            nm.notify(
                2, Notification.Builder(this, ERR_CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle("Scheduled Sounds problem")
                    .setContentText(msg)
                    .setStyle(Notification.BigTextStyle().bigText(full.take(500)))
                    .build()
            )
        } catch (_: Throwable) {
        }
    }

    private fun stopPlayback() {
        handler.removeCallbacksAndMessages(null)
        try { player?.release() } catch (_: Throwable) {}
        player = null
    }

    private fun finish() {
        stopPlayback()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
        stopSelf()
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "playback"
        private const val ERR_CHANNEL = "errors"
    }
}
