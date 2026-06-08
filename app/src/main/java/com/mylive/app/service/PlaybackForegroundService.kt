package com.mylive.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import com.mylive.app.MainActivity
import com.mylive.app.R

class PlaybackForegroundService : Service() {

    companion object {
        const val ACTION_PLAY = "com.mylive.app.ACTION_PLAY"
        const val ACTION_PAUSE = "com.mylive.app.ACTION_PAUSE"
        const val ACTION_STOP = "com.mylive.app.ACTION_STOP"

        const val CHANNEL_ID = "playback_channel"
        const val NOTIFICATION_ID = 1001

        private var activePlayerRef: java.lang.ref.WeakReference<Player>? = null

        var activePlayer: Player?
            get() = activePlayerRef?.get()
            set(value) {
                activePlayerRef = value?.let { java.lang.ref.WeakReference(it) }
            }
        var roomTitle: String = ""
        var anchorName: String = ""
        var platformName: String = ""

        fun start(context: Context, player: Player, title: String, anchor: String, platform: String) {
            activePlayer = player
            roomTitle = title
            anchorName = anchor
            platformName = platform
            val intent = Intent(context, PlaybackForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PlaybackForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        activePlayer?.addListener(playerListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> activePlayer?.play()
            ACTION_PAUSE -> activePlayer?.pause()
            ACTION_STOP -> {
                activePlayer?.pause()
                stopSelf()
            }
        }
        updateNotification()
        return START_NOT_STICKY
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "后台播放控制",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isPlaying = activePlayer?.isPlaying == true

        val playPauseAction = if (isPlaying) {
            val pauseIntent = Intent(this, PlaybackForegroundService::class.java).apply { action = ACTION_PAUSE }
            val pausePending = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause, "暂停", pausePending
            ).build()
        } else {
            val playIntent = Intent(this, PlaybackForegroundService::class.java).apply { action = ACTION_PLAY }
            val playPending = PendingIntent.getService(this, 2, playIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play, "播放", playPending
            ).build()
        }

        val stopIntent = Intent(this, PlaybackForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPending
        ).build()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(roomTitle.ifEmpty { "正在直播" })
            .setContentText("$anchorName | $platformName")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activePlayer?.removeListener(playerListener)
        activePlayer = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
