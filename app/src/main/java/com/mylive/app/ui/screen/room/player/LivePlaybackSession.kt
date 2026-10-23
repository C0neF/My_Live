package com.mylive.app.ui.screen.room.player

import android.content.Context
import com.mylive.app.core.model.LivePlayUrl
import com.mylive.app.service.PlaybackForegroundService
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Owns one live-room playback session: ExoPlayer controller lifetime, ViewModel bind,
 * host lifecycle pause/resume policy, and foreground-service handoff.
 *
 * Callers (Compose) express host lifecycle and user exit intent; they should not
 * re-implement pause-matrix / FGS / dual-ref release ordering.
 */
class LivePlaybackSession private constructor(
    private val appContext: Context,
    hardwareDecodeEnabled: Boolean,
    forceHttps: Boolean,
    private val onPlaybackSourceExhausted: (() -> Boolean)?
) : LivePlaybackEngine {
    private data class PlaybackRequest(
        val urls: List<String>,
        val headers: Map<String, String>?,
        val startIndex: Int,
        val origin: PlaybackRequestOrigin,
        val videoFrameRateHint: Float? = null,
        val startPlayback: Boolean = false,
        val pauseGeneration: Long = 0L
    )

    private data class PausedHostPolicy(
        val allowBackgroundPlayback: Boolean,
        val playerAutoPause: Boolean,
        val roomTitle: String,
        val anchorName: String,
        val platform: String
    )

    private val sourceRecoveryGate = PlaybackSourceRecoveryGate()
    private val controller = PlayerController(
        context = appContext,
        hardwareDecodeEnabled = hardwareDecodeEnabled,
        forceHttps = forceHttps,
        onPlaybackSourceExhausted = ::requestFreshPlaybackSource,
        onPlaybackReady = sourceRecoveryGate::finishRecovery,
        onPlayerReplaced = PlaybackForegroundService::replacePlayer,
        onUserPlaybackSelection = sourceRecoveryGate::finishRecovery
    )

    val playerController: PlayerController
        get() = controller

    /** Session proxy prevents late async play requests from bypassing host policy. */
    val engine: LivePlaybackEngine
        get() = this

    override val state: StateFlow<PlayerState>
        get() = controller.state

    override val playbackPauseGeneration: Long
        get() = controller.playbackPauseGeneration

    override fun beginRoomLoad(startedAtMillis: Long) {
        if (!released) controller.beginRoomLoad(startedAtMillis)
    }

    private var resumePlaybackOnForeground: Boolean = false
    private var foregroundResumePauseGeneration = 0L
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
        origin: PlaybackRequestOrigin
    ) {
        submitPlaybackRequest(PlaybackRequest(urls, headers, startIndex, origin))
    }

    override fun play(source: LivePlayUrl, origin: PlaybackRequestOrigin) {
        submitPlaybackRequest(PlaybackRequest(source.urls, source.headers, 0, origin, source.videoFrameRateHint))
    }

    override fun play(source: LivePlayUrl, origin: PlaybackRequestOrigin, pauseGeneration: Long?) {
        submitPlaybackRequest(
            PlaybackRequest(source.urls, source.headers, 0, origin, source.videoFrameRateHint),
            pauseGeneration
        )
    }

    private fun submitPlaybackRequest(request: PlaybackRequest, requestedPauseGeneration: Long? = null) {
        if (released) return
        // A delivered request means source acquisition completed, even if host policy defers play.
        sourceRecoveryGate.finishRecovery()
        val currentPauseGeneration = controller.playbackPauseGeneration
        val hasCurrentUserSelection = request.origin == PlaybackRequestOrigin.UserSelection &&
            (requestedPauseGeneration == null || requestedPauseGeneration == currentPauseGeneration)
        val pendingRequest = request.copy(
            startPlayback = hasCurrentUserSelection ||
                controller.player?.playWhenReady == true || shouldResumeOnForeground(),
            // Once accepted, a later pause must still invalidate a deferred FGS/host start.
            pauseGeneration = currentPauseGeneration
        )
        val policy = pausedHostPolicy
        when (
            resolveLivePlaybackStartAction(
                hostPaused = hostPaused,
                allowBackgroundPlayback = policy?.allowBackgroundPlayback ?: false,
                playerAutoPause = policy?.playerAutoPause ?: true
            )
        ) {
            LivePlaybackStartAction.StartNow -> {
                deferredPlayRequest = null
                rememberForegroundResume(false)
                playNow(pendingRequest)
            }
            LivePlaybackStartAction.DeferUntilResume -> {
                deferredPlayRequest = pendingRequest
                rememberForegroundResume(pendingRequest.startPlayback)
            }
            LivePlaybackStartAction.StartWithForegroundService -> {
                deferredPlayRequest = pendingRequest
                rememberForegroundResume(pendingRequest.startPlayback)
                if (policy != null) {
                    startForegroundService(policy) {
                        if (deferredPlayRequest === pendingRequest) {
                            deferredPlayRequest = null
                            rememberForegroundResume(false)
                            playNow(pendingRequest)
                        }
                    }
                }
            }
        }
    }

    private fun playNow(request: PlaybackRequest) {
        controller.play(
            urls = request.urls,
            headers = request.headers,
            startIndex = request.startIndex,
            origin = request.origin,
            videoFrameRateHint = request.videoFrameRateHint,
            startPlayback = (request.startPlayback && request.pauseGeneration == controller.playbackPauseGeneration) ||
                controller.player?.playWhenReady == true
        )
    }

    private fun rememberForegroundResume(resume: Boolean) {
        resumePlaybackOnForeground = resume
        foregroundResumePauseGeneration = controller.playbackPauseGeneration
    }

    private fun shouldResumeOnForeground(): Boolean =
        resumePlaybackOnForeground && foregroundResumePauseGeneration == controller.playbackPauseGeneration

    private fun requestFreshPlaybackSource(): Boolean {
        if (released) return false
        val request = onPlaybackSourceExhausted ?: return false
        return sourceRecoveryGate.tryStartRecovery(request)
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

    override fun stopForSourceRecovery() {
        if (released) return
        deferredPlayRequest = null
        // A refresh is still the same playback request. Keep lifecycle resume intent and
        // its pause generation so a newer manual/system pause can invalidate it.
        controller.stop()
    }

    override fun showError(message: String) {
        if (released) return
        sourceRecoveryGate.finishRecovery()
        deferredPlayRequest = null
        resumePlaybackOnForeground = false
        controller.showError(message)
    }

    fun pause() {
        if (released) return
        rememberForegroundResume(false)
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
        platform: String,
        inPictureInPicture: Boolean = false
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
        controller.setVideoOutputExpected(inPictureInPicture)
        if (inPictureInPicture) {
            // The PiP window is still an on-screen video surface, so ON_PAUSE here is not a
            // background transition: never pause and never hand off to the foreground service.
            pausedHostPolicy = null
            resumePlaybackOnForeground = false
            hostPaused = false
            return
        }
        if (hostPaused) return
        hostPaused = true
        val player = controller.player
        val hasPlaybackIntent = player?.playWhenReady == true
        val action = resolveLivePlaybackHostPauseAction(
            allowBackgroundPlayback = allowBackgroundPlayback,
            playerAutoPause = playerAutoPause,
            hasPlaybackIntent = hasPlaybackIntent
        )
        val actionName = when (action) {
            LivePlaybackHostPauseAction.Pause -> "pause"
            LivePlaybackHostPauseAction.StartForegroundService -> "foreground_service"
            LivePlaybackHostPauseAction.NoOp -> "none"
        }
        Timber.tag("LivePlayback").i(
            "Host paused: action=%s, playWhenReady=%s, background=%s, autoPause=%s",
            actionName, hasPlaybackIntent, allowBackgroundPlayback, playerAutoPause
        )
        rememberForegroundResume(shouldResumeLivePlaybackOnForeground(
            lifecyclePausedPlayback = action is LivePlaybackHostPauseAction.Pause,
            wasPlayingBeforePause = hasPlaybackIntent
        ))
        when (action) {
            LivePlaybackHostPauseAction.Pause -> controller.pauseForHost()
            LivePlaybackHostPauseAction.StartForegroundService -> startForegroundService(policy)
            LivePlaybackHostPauseAction.NoOp -> Unit
        }
    }

    /**
     * Hands playback to the foreground service, pausing instead if the platform refuses.
     *
     * Without the service, background audio would keep running under no foreground-service
     * notification, which Android will kill on its own schedule. Pausing makes the outcome
     * predictable and matches what the user gets with background playback disabled.
     */
    private fun startForegroundService(policy: PausedHostPolicy, onReady: (() -> Unit)? = null): Boolean {
        val player = controller.player ?: return false
        val hadPlaybackIntent = player.playWhenReady
        val pauseGeneration = controller.playbackPauseGeneration
        val started = PlaybackForegroundService.start(
            appContext,
            player,
            policy.roomTitle,
            policy.anchorName,
            policy.platform,
            onPause = ::pause
        ) {
            if (!released && hostPaused) {
                if (onReady != null) onReady()
                else if (hadPlaybackIntent && controller.player?.playWhenReady == true &&
                    pauseGeneration == controller.playbackPauseGeneration
                ) controller.resume()
            }
        }
        if (!started) {
            rememberForegroundResume(shouldResumeOnForeground() || controller.player?.playWhenReady == true)
            controller.pauseForHost()
        }
        return started
    }

    /**
     * Host returned to foreground (Activity ON_RESUME).
     * Stops FGS and optionally resumes if we paused for lifecycle.
     */
    fun onHostResume() {
        if (released) return
        if (hostPaused) Timber.tag("LivePlayback").i("Host resumed: resumePlayback=%s", resumePlaybackOnForeground)
        controller.setVideoOutputExpected(true)
        hostPaused = false
        pausedHostPolicy = null
        stopForegroundService()
        val deferred = deferredPlayRequest
        deferredPlayRequest = null
        resumePlaybackOnForeground = shouldResumeOnForeground()
        if (deferred != null) {
            playNow(deferred)
        } else if (resumePlaybackOnForeground) {
            controller.resume()
        }
        resumePlaybackOnForeground = false
    }

    fun stopForegroundService() {
        controller.player?.let { PlaybackForegroundService.stop(appContext, it) }
    }

    /**
     * Tear down controller and detach ViewModel. Safe to call once; further calls no-op.
     * ViewModel must not release the controller itself.
     */
    fun release() {
        if (released) return
        Timber.tag("LivePlayback").d("Playback session released; unbinding engine")
        released = true
        deferredPlayRequest = null
        hostPaused = false
        pausedHostPolicy = null
        stopForegroundService()
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
            onPlaybackSourceExhausted: (() -> Boolean)?
        ): LivePlaybackSession {
            val appContext = context.applicationContext
            return LivePlaybackSession(
                appContext = appContext,
                hardwareDecodeEnabled = hardwareDecodeEnabled,
                forceHttps = forceHttps,
                onPlaybackSourceExhausted = onPlaybackSourceExhausted
            )
        }

    }
}

/** In-flight refresh deduplication only; retry limits are owned by PlayerController. */
internal class PlaybackSourceRecoveryGate {
    private var recoveryInProgress = false

    fun finishRecovery() {
        recoveryInProgress = false
    }

    fun tryStartRecovery(start: () -> Boolean): Boolean {
        if (recoveryInProgress) return false
        // Claim first: a cached/synchronous response may finish recovery inside start().
        recoveryInProgress = true
        var accepted = false
        return try {
            accepted = start()
            accepted
        } finally {
            if (!accepted) recoveryInProgress = false
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
