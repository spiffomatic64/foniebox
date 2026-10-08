package com.example.foniebox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import java.util.Collections

class PlaybackService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tracks: List<Mp3Track> = emptyList()
    private var queue: MutableList<Mp3Track> = mutableListOf()
    private var startedAtMs = 0L
    private var playbackMs = 0L
    private var fadeMs = 0L
    private var totalMs = 0L
    private var stopped = true
    private var currentTrackName: String = ""
    private var currentVolume: Float = 0f

    private val timer = object : Runnable {
        override fun run() {
            updateFadeOrStop()
            broadcastStatus()
            if (!stopped) handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startFromIntent(intent)
            ACTION_STOP -> stopPlayback()
            ACTION_STATUS_REQUEST -> {
                broadcastStatus()
                if (stopped) stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    private fun startFromIntent(intent: Intent) {
        val treeUri = intent.getStringExtra(EXTRA_TREE_URI)?.let(Uri::parse) ?: return
        val durationMinutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 30).coerceAtLeast(1)
        val fadeMinutes = intent.getIntExtra(EXTRA_FADE_MINUTES, 10).coerceAtLeast(1)

        tracks = Mp3Scanner.findMp3s(contentResolver, treeUri)
        if (tracks.isEmpty()) {
            stopPlayback()
            return
        }

        stopPlayerOnly()
        stopped = false
        startedAtMs = System.currentTimeMillis()
        playbackMs = durationMinutes * 60_000L
        fadeMs = fadeMinutes * 60_000L
        totalMs = playbackMs + fadeMs
        queue = shuffledTracks()
        currentTrackName = ""
        currentVolume = 1f

        startForeground(NOTIFICATION_ID, buildNotification("Playing shuffled MP3s"))
        broadcastStatus()
        playNext()
        handler.removeCallbacks(timer)
        handler.post(timer)
    }

    private fun playNext() {
        if (stopped || updateFadeOrStop()) return
        if (queue.isEmpty()) queue = shuffledTracks()

        val track = queue.removeAt(0)
        stopPlayerOnly()
        currentTrackName = track.name
        broadcastStatus()

        player = MediaPlayer().apply {
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(applicationContext, track.uri)
            setOnPreparedListener {
                applyCurrentVolume()
                it.start()
            }
            setOnCompletionListener { playNext() }
            setOnErrorListener { _, _, _ ->
                playNext()
                true
            }
            prepareAsync()
        }
    }

    private fun updateFadeOrStop(): Boolean {
        val elapsed = System.currentTimeMillis() - startedAtMs
        if (elapsed >= totalMs) {
            stopPlayback()
            return true
        }
        applyCurrentVolume()
        return false
    }

    private fun applyCurrentVolume() {
        val elapsed = System.currentTimeMillis() - startedAtMs
        currentVolume = if (elapsed < playbackMs) {
            1f
        } else {
            val fadeElapsed = elapsed - playbackMs
            (1f - (fadeElapsed.toFloat() / fadeMs.toFloat())).coerceIn(0f, 1f)
        }
        player?.setVolume(currentVolume, currentVolume)
    }

    private fun shuffledTracks(): MutableList<Mp3Track> {
        return tracks.toMutableList().also { Collections.shuffle(it) }
    }

    private fun stopPlayback() {
        stopped = true
        handler.removeCallbacks(timer)
        stopPlayerOnly()
        currentTrackName = ""
        currentVolume = 0f
        broadcastStatus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun broadcastStatus() {
        val statusIntent = Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_IS_PLAYING, !stopped)
            putExtra(EXTRA_FILE_NAME, currentTrackName)
            putExtra(EXTRA_VOLUME, currentVolume)
            putExtra(EXTRA_MODE, currentMode())
            putExtra(EXTRA_REMAINING_MS, remainingInCurrentModeMs())
        }
        sendBroadcast(statusIntent)
    }

    private fun currentMode(): String {
        if (stopped) return MODE_STOPPED
        val elapsed = System.currentTimeMillis() - startedAtMs
        return if (elapsed < playbackMs) MODE_PLAYBACK else MODE_FADEOUT
    }

    private fun remainingInCurrentModeMs(): Long {
        if (stopped) return 0L
        val elapsed = System.currentTimeMillis() - startedAtMs
        val target = if (elapsed < playbackMs) playbackMs else totalMs
        return (target - elapsed).coerceAtLeast(0L)
    }

    private fun stopPlayerOnly() {
        player?.setOnCompletionListener(null)
        player?.setOnErrorListener(null)
        runCatching {
            if (player?.isPlaying == true) player?.stop()
        }
        player?.release()
        player = null
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Foniebox")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.example.foniebox.START"
        const val ACTION_STOP = "com.example.foniebox.STOP"
        const val ACTION_STATUS = "com.example.foniebox.STATUS"
        const val ACTION_STATUS_REQUEST = "com.example.foniebox.STATUS_REQUEST"
        const val EXTRA_TREE_URI = "tree_uri"
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
        const val EXTRA_FADE_MINUTES = "fade_minutes"
        const val EXTRA_IS_PLAYING = "is_playing"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_VOLUME = "volume"
        const val EXTRA_MODE = "mode"
        const val EXTRA_REMAINING_MS = "remaining_ms"
        const val MODE_PLAYBACK = "playback"
        const val MODE_FADEOUT = "fadeout"
        const val MODE_STOPPED = "stopped"

        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 42
    }
}
