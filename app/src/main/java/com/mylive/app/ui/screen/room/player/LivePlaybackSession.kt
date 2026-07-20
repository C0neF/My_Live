package com.mylive.app.ui.screen.room.player

import android.content.Context
import com.mylive.app.service.PlaybackForegroundService
import com.mylive.app.ui.screen.room.shouldResumeLivePlaybackOnForeground
import kotlinx.coroutines.flow.StateFlow

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
) : LivePlaybackEngine {
    private data class PlaybackRequest(
        val urls: List<String>,
        val headers: Map<String, String>?,
        val startIndex: Int,
        val resetSourceRefreshAttempt: Boolean
    )

    private data class PausedHostPolicy(
        val allowBackgroundPlayback: Boolean,
        val playerAutoPause: Boolean,
        val roomTitle: String,
        val anchorName: String,
        val platform: String
    )

    val playerController: PlayerController
        get() = controller

    /** Session proxy prevents late async play requests from bypassing host policy. */
    val engine: LivePlaybackEngine
        get() = this

    override val state: StateFlow<PlayerState>
        get() = controller.state

    private var resumePlaybackOnForeground: Boolean = false
    private var attachedBinding: LivePlaybackSessionBinding? = null
    private var pausedHostPolicy: PausedHostPolicy? = null
    private var deferredPlayRequest: PlaybackRequest? = null
    private var hostPaused: Boolean = false
    private var released: Boolean = false

    fun attach(binding: LivePlaybackSessionBinding) {
        if (released) return
        attachedBinding = binding
        binding.bindPlaybackEngine(engine)
        binding.onPlaybackEngineReady()
    }

    override fun play(
        urls: List<String>,
        headers: Map<String, String>?,
        startIndex: Int,
        resetSourceRefreshAttempt: Boolean
    ) {
        if (released) return
        val request = PlaybackRequest(urls, headers, startIndex, resetSourceRefreshAttempt)
        val policy = pausedHostPolicy
        when (
            resolveLivePlaybackStartAction(
                hostPaused = hostPaused,
                allowBackgroundPlayback = policy?.allowBackgroundPlayback ?: false,
                playerAutoPause = policy?.playerAutoPause ?: true
            )
        ) {
            LivePlaybackStartAction.StartNow -> playNow(request)
            LivePlaybackStartAction.DeferUntilResume -> {
                deferredPlayRequest = request
                resumePlaybackOnForeground = true
            }
            LivePlaybackStartAction.StartWithForegroundService -> {
                playNow(request)
                policy?.let(::startForegroundService)
            }
        }
    }

    private fun playNow(request: PlaybackRequest) {
        controller.play(
            urls = request.urls,
            headers = request.headers,
            startIndex = request.startIndex,
            resetSourceRefreshAttempt = request.resetSourceRefreshAttempt
        )
    }

    fun setForceHttps(enabled: Boolean) {
        if (released) return
        controller.setForceHttps(enabled)
    }

    override fun stop() {
        if (released) return
        deferredPlayRequest = null
        resumePlaybackOnForeground = false
        controller.stop()
    }

    override fun showError(message: String) {
        if (released) return
        deferredPlayRequest = null
        resumePlaybackOnForeground = false
        controller.showError(message)
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
        val policy = PausedHostPolicy(
            allowBackgroundPlayback = allowBackgroundPlayback,
            playerAutoPause = playerAutoPause,
            roomTitle = roomTitle,
            anchorName = anchorName,
            platform = platform
        )
        pausedHostPolicy = policy
        if (hostPaused) return
        hostPaused = true
        val player = controller.player
        val hasPlaybackIntent = player?.playWhenReady == true
        val action = resolveLivePlaybackHostPauseAction(
            allowBackgroundPlayback = allowBackgroundPlayback,
            playerAutoPause = playerAutoPause,
            hasPlaybackIntent = hasPlaybackIntent
        )
        resumePlaybackOnForeground = shouldResumeLivePlaybackOnForeground(
            lifecyclePausedPlayback = action is LivePlaybackHostPauseAction.Pause,
            wasPlayingBeforePause = hasPlaybackIntent
        )
        when (action) {
            LivePlaybackHostPauseAction.Pause -> controller.pause()
            LivePlaybackHostPauseAction.StartForegroundService -> startForegroundService(policy)
            LivePlaybackHostPauseAction.NoOp -> Unit
        }
    }

    private fun startForegroundService(policy: PausedHostPolicy) {
        val player = controller.player ?: return
        PlaybackForegroundService.start(
            appContext,
            player,
            policy.roomTitle,
            policy.anchorName,
            policy.platform
        )
    }

    /**
     * Host returned to foreground (Activity ON_RESUME).
     * Stops FGS and optionally resumes if we paused for lifecycle.
     */
    fun onHostResume() {
        if (released) return
        hostPaused = false
        pausedHostPolicy = null
        PlaybackForegroundService.stop(appContext)
        val deferred = deferredPlayRequest
        deferredPlayRequest = null
        if (deferred != null) {
            playNow(deferred)
        } else if (resumePlaybackOnForeground) {
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
        deferredPlayRequest = null
        hostPaused = false
        pausedHostPolicy = null
        PlaybackForegroundService.stop(appContext)
        val binding = attachedBinding
        attachedBinding = null
        binding?.unbindPlaybackEngine(engine)
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

        fun setAutoPipActive(owner: Any, active: Boolean) {
            LivePlaybackHostSignals.setAutoPipOnLeave(owner, active)
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
