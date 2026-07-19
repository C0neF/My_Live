package com.mylive.app.ui.screen.room.player

import android.content.Context
import com.mylive.app.service.PlaybackForegroundService
import com.mylive.app.ui.screen.room.LiveRoomViewModel
import com.mylive.app.ui.screen.room.shouldResumeLivePlaybackOnForeground

/**
 * Owns one live-room playback session: ExoPlayer controller lifetime, ViewModel bind,
 * host lifecycle pause/resume policy, and foreground-service handoff.
 *
 * Callers (Compose) express host lifecycle and user exit intent; they should not
 * re-implement pause-matrix / FGS / dual-ref release ordering.
 */
class LivePlaybackSession private constructor(
    private val appContext: Context,
    private val controller: PlayerController
) {
    val playerController: PlayerController
        get() = controller

    /** Narrow port for ViewModel — no ExoPlayer escape hatch. */
    val engine: LivePlaybackEngine
        get() = controller

    private var resumePlaybackOnForeground: Boolean = false
    private var attachedViewModel: LiveRoomViewModel? = null
    private var released: Boolean = false

    fun attach(viewModel: LiveRoomViewModel) {
        if (released) return
        attachedViewModel = viewModel
        viewModel.bindPlaybackEngine(engine)
        viewModel.onPlayerControllerReady()
    }

    fun setForceHttps(enabled: Boolean) {
        if (released) return
        controller.setForceHttps(enabled)
    }

    fun stop() {
        if (released) return
        controller.stop()
    }

    fun pause() {
        if (released) return
        controller.pause()
    }

    fun resume() {
        if (released) return
        controller.resume()
    }

    fun toggleFullscreen() {
        if (released) return
        controller.toggleFullscreen()
    }

    /**
     * Host moved to background (Activity ON_PAUSE).
     * Encodes allow-background × auto-pause × currently-playing into pause vs FGS.
     */
    fun onHostPause(
        allowBackgroundPlayback: Boolean,
        playerAutoPause: Boolean,
        roomTitle: String,
        anchorName: String,
        platform: String
    ) {
        if (released) return
        val player = controller.player
        val isPlaying = player?.isPlaying == true
        val action = resolveLivePlaybackHostPauseAction(
            allowBackgroundPlayback = allowBackgroundPlayback,
            playerAutoPause = playerAutoPause,
            isPlaying = isPlaying
        )
        resumePlaybackOnForeground = shouldResumeLivePlaybackOnForeground(
            lifecyclePausedPlayback = action is LivePlaybackHostPauseAction.Pause,
            wasPlayingBeforePause = isPlaying
        )
        when (action) {
            LivePlaybackHostPauseAction.Pause -> controller.pause()
            LivePlaybackHostPauseAction.StartForegroundService -> {
                if (player != null) {
                    PlaybackForegroundService.start(
                        appContext,
                        player,
                        roomTitle,
                        anchorName,
                        platform
                    )
                }
            }
            LivePlaybackHostPauseAction.NoOp -> Unit
        }
    }

    /**
     * Host returned to foreground (Activity ON_RESUME).
     * Stops FGS and optionally resumes if we paused for lifecycle.
     */
    fun onHostResume() {
        if (released) return
        PlaybackForegroundService.stop(appContext)
        if (resumePlaybackOnForeground) {
            controller.resume()
        }
        resumePlaybackOnForeground = false
    }

    fun stopForegroundService() {
        PlaybackForegroundService.stop(appContext)
    }

    /**
     * Tear down controller and detach ViewModel. Safe to call once; further calls no-op.
     * ViewModel must not release the controller itself.
     */
    fun release() {
        if (released) return
        released = true
        PlaybackForegroundService.stop(appContext)
        val viewModel = attachedViewModel
        attachedViewModel = null
        viewModel?.unbindPlaybackEngine(engine)
        controller.release()
    }

    companion object {
        fun create(
            context: Context,
            hardwareDecodeEnabled: Boolean,
            forceHttps: Boolean,
            onPlaybackSourceExhausted: (() -> Unit)?
        ): LivePlaybackSession {
            val appContext = context.applicationContext
            val controller = PlayerController(
                context = appContext,
                hardwareDecodeEnabled = hardwareDecodeEnabled,
                forceHttps = forceHttps,
                onPlaybackSourceExhausted = onPlaybackSourceExhausted
            )
            return LivePlaybackSession(appContext, controller)
        }

        /**
         * Host PiP eligibility used by [com.mylive.app.MainActivity.onUserLeaveHint]
         * via [LivePlaybackHostSignals].
         */
        fun setAutoPipActive(active: Boolean) {
            LivePlaybackHostSignals.setAutoPipOnLeave(active)
        }
    }
}

/** True when host lifecycle should pause the player instead of keeping audio alive. */
internal fun shouldLifecyclePauseLivePlayback(
    allowBackgroundPlayback: Boolean,
    playerAutoPause: Boolean
): Boolean = !allowBackgroundPlayback || playerAutoPause

/**
 * True when background keep-alive should promote the player into the foreground service.
 * Mutually exclusive with [shouldLifecyclePauseLivePlayback] when isPlaying is considered.
 */
internal fun shouldStartPlaybackForegroundService(
    lifecyclePausesPlayback: Boolean,
    isPlaying: Boolean
): Boolean = !lifecyclePausesPlayback && isPlaying
