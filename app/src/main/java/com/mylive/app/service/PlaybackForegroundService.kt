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
import com.mylive.app.core.common.CoreLog

class PlaybackForegroundService : Service() {

    companion object {
        const val ACTION_PLAY = "com.mylive.app.ACTION_PLAY"
        const val ACTION_PAUSE = "com.mylive.app.ACTION_PAUSE"
        const val ACTION_STOP = "com.mylive.app.ACTION_STOP"

        const val CHANNEL_ID = "playback_channel"
        const val NOTIFICATION_ID = 1001

        private var activePlayerRef: java.lang.ref.WeakReference<Player>? = null
        private var instance: PlaybackForegroundService? = null
        private var pendingStart: (() -> Unit)? = null
        private var notificationPause: (() -> Unit)? = null

        /** Rebind only the session currently handed off to this service. */
        fun replacePlayer(previous: Player, replacement: Player) {
            if (activePlayer !== previous) return
            val pause = notificationPause
            activePlayer = replacement
            notificationPause = pause
            instance?.let { service ->
                service.bindPlayer(replacement)
                service.updateNotification()
            }
        }

        var activePlayer: Player?
            get() = activePlayerRef?.get()
            set(value) {
                if (activePlayer !== value) notificationPause = null
                activePlayerRef = value?.let { java.lang.ref.WeakReference(it) }
            }
        var roomTitle: String = ""
        var anchorName: String = ""
        var platformName: String = ""

        /**
         * Hands playback off to a foreground service.
         *
         * Called from the room's `ON_PAUSE`, which is exactly the transition Android 12+ polices:
         * if the process is no longer considered foreground by the time the call lands, the
         * platform throws [android.app.ForegroundServiceStartNotAllowedException]. Background
         * playback is a convenience, so a refused hand-off degrades to "playback stops when you
         * leave" instead of crashing the app.
         *
         * @return true when the service was asked to start.
         */
        fun start(
            context: Context,
            player: Player,
            title: String,
            anchor: String,
            platform: String,
            onReady: (() -> Unit)? = null
        ): Boolean = start(context, player, title, anchor, platform, onPause = null, onReady = onReady)

        /** Session-owned handoff; the original overload remains available to raw Player callers. */
        fun start(
            context: Context,
            player: Player,
            title: String,
            anchor: String,
            platform: String,
            onPause: (() -> Unit)?,
            onReady: (() -> Unit)?
        ): Boolean {
            activePlayer = player
            roomTitle = title
            anchorName = anchor
            platformName = platform
            pendingStart = onReady
            notificationPause = onPause
            val intent = Intent(context, PlaybackForegroundService::class.java)
            return try {
                context.startForegroundService(intent)
                true
            } catch (e: Exception) {
                // IllegalStateException on API 26-30, ForegroundServiceStartNotAllowedException
                // (a subclass) on API 31+. Neither is recoverable here.
                CoreLog.w("PlaybackForegroundService: background playback hand-off refused: ${e.message}")
                activePlayer = null
                pendingStart = null
                false
            }
        }

        fun stop(context: Context) {
            pendingStart = null
            notificationPause = null
            val intent = Intent(context, PlaybackForegroundService::class.java)
            context.stopService(intent)
        }

        /** Outgoing room cleanup may arrive after another room has taken over the service. */
        fun stop(context: Context, player: Player) {
            if (activePlayer !== player) return
            stop(context)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateNotification()
        }
    }

    /** Set by ACTION_STOP so late player callbacks cannot re-promote a dismissed service. */
    @Volatile
    private var stopping = false

    /** The player this instance attached its listener to, so teardown cannot detach another. */
    private var listeningTo: Player? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        bindPlayer(activePlayer)
    }

    private fun bindPlayer(player: Player?) {
        if (listeningTo === player) return
        listeningTo?.removeListener(playerListener)
        listeningTo = player
        player?.addListener(playerListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (stopping) return START_NOT_STICKY
        bindPlayer(activePlayer)
        when (intent?.action) {
            ACTION_PAUSE -> {
                pauseFromNotification()
            }
            ACTION_STOP -> {
                stopping = true
                pauseFromNotification()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                // Return before updateNotification(): falling through re-promoted the service to
                // the foreground immediately after the user asked it to stop.
                return START_NOT_STICKY
            }
        }
        updateNotification()
        // API 35+ requires a foreground app or an active FGS before requesting audio focus.
        // Execute the session's still-current request only after startForeground completes.
        if (intent?.action == ACTION_PLAY) activePlayer?.play()
        if (intent?.action == null) {
            val ready = pendingStart
            pendingStart = null
            ready?.invoke()
        }
        return START_NOT_STICKY
    }

    private fun pauseFromNotification() {
        pendingStart = null
        // Session.pause also invalidates older deferred starts, including a request whose
        // player is already paused. Keep direct Player support for callers without a Session.
        val pause = notificationPause
        if (pause != null) pause() else activePlayer?.pause()
    }

    private fun updateNotification() {
        // A late Player callback can land after ACTION_STOP; re-promoting the service then would
        // resurrect a notification the user just dismissed.
        if (stopping) return

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "后台播放控制",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            // Resume the activity that owns this room and its navigation state.
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
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
            .setSmallIcon(R.drawable.ic_notification_playback)
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
        if (instance === this) {
            instance = null
        }
        // Detach from the player this instance actually attached to, and only clear the static
        // reference if it still points there: a new room may already have published its own
        // player while this instance was being torn down.
        val attached = listeningTo
        listeningTo = null
        attached?.removeListener(playerListener)
        if (attached != null && activePlayer === attached) {
            activePlayer = null
            pendingStart = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
