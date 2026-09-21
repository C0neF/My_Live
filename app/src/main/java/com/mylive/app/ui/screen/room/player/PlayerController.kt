package com.mylive.app.ui.screen.room.player

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicLong
import androidx.media3.exoplayer.video.VideoFrameMetadataListener

import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

data class PlayerState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentUrl: String? = null,
    val volume: Float = 1f,
    val brightness: Float = 0.5f,
    val isFullscreen: Boolean = false,
    val playerGeneration: Long = 0L
)

@OptIn(UnstableApi::class)
class PlayerController(
    private val context: Context,
    private val hardwareDecodeEnabled: Boolean = true,
    private var forceHttps: Boolean = false,
    private val onPlaybackSourceExhausted: (() -> Boolean)? = null,
    private val onPlaybackReady: (() -> Unit)? = null,
    private val onUserPlaybackSelection: (() -> Unit)? = null,
    private val onPlayerReplaced: ((Player, Player) -> Unit)? = null
) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    var player: ExoPlayer? = null
        private set

    private var currentUrlIndex = 0
    private var urls: List<String> = emptyList()
    private var headers: Map<String, String>? = null
    private var usingSoftwareDecoderOnly = !hardwareDecodeEnabled
    private var softwareDecoderFallbackAttempted = false
    private val stallMonitor = PlaybackStallMonitor()
    private val recoveryBudget = PlaybackRecoveryBudget()
    private val videoFrameCount = AtomicLong()
    private var videoOutputExpected = true
    private var lastRecoveryReason: String? = null
    private var playbackStatsListener = PlaybackStatsListener(false, null)
    private var previousPlayerPlayMillis = 0L
    private var previousPlayerRebufferMillis = 0L
    private var sourceStartedAtMillis = 0L
    private var firstFrameMillis: Long? = null
    private var frameRateSampleAtMillis: Long? = null
    private var frameRateSampleCount = 0L
    private var outputFrameRate: Float? = null
    private val videoFrameListener = VideoFrameMetadataListener { _, _, _, _ -> videoFrameCount.incrementAndGet() }
    private val stallHandler = Handler(Looper.getMainLooper())
    private var stallWatchdogRunning = false
    private var mediaToken = 0L
    private var currentVideoFormat: Format? = null
    private var currentVideoDecoderName: String? = null
    private var droppedVideoFrames: Long = 0L
    private var bufferingCount: Int = 0
    private var stallRecoveryCount: Int = 0
    private var lastPlaybackState: Int = Player.STATE_IDLE
    private val stallWatchdog = object : Runnable {
        override fun run() {
            if (!stallWatchdogRunning) return
            samplePlaybackStall()
            if (stallWatchdogRunning) {
                stallHandler.postDelayed(this, STALL_SAMPLE_INTERVAL_MILLIS)
            }
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxSystemVolume = audioManager
        .getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        .coerceAtLeast(1)
    private var lastNonZeroVolume = 1f

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_BUFFERING && lastPlaybackState != Player.STATE_BUFFERING) {
                bufferingCount += 1
            }
            lastPlaybackState = playbackState
            when (playbackState) {
                Player.STATE_IDLE -> {
                    stopStallWatchdog(resetMonitor = true)
                    _state.value = _state.value.copy(isLoading = false, isPlaying = false)
                }
                Player.STATE_READY -> {
                    if (player?.playWhenReady == true) startStallWatchdog()
                    onPlaybackReady?.invoke()
                    _state.value = _state.value.copy(isLoading = false, error = null)
                }
                Player.STATE_BUFFERING -> {
                    if (player?.playWhenReady == true) startStallWatchdog()
                    _state.value = _state.value.copy(isLoading = true)
                }
                Player.STATE_ENDED -> {
                    stopStallWatchdog(resetMonitor = true)
                    _state.value = _state.value.copy(isPlaying = false)
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Player error")
            val errorText = playbackExceptionText(error)
            // A decoder failure is independent of CDN reachability; try its fallback first.
            when {
                shouldRetryWithSoftwareDecoder(errorText) -> runRecovery("解码回退") {
                    retryCurrentUrlWithSoftwareDecoder()
                    true
                }
                currentUrlIndex < urls.size - 1 -> runRecovery("播放错误换线") {
                    currentUrlIndex++
                    softwareDecoderFallbackAttempted = false
                    playCurrentUrl()
                    true
                }
                else -> {
                    if (!runRecovery("重新获取播放源") { onPlaybackSourceExhausted?.invoke() == true } &&
                        _state.value.error == null
                    ) showError(userVisiblePlaybackError(errorText))
                }
            }
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
            if (firstFrameMillis == null) {
                firstFrameMillis = (SystemClock.elapsedRealtime() - sourceStartedAtMillis).coerceAtLeast(0L)
            }
        }
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            currentVideoDecoderName = decoderName
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            currentVideoFormat = format
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long
        ) {
            droppedVideoFrames += droppedFrames.toLong()
        }
    }

    init {
        initialize()
    }

    private fun initialize(softwareDecoderOnly: Boolean = !hardwareDecodeEnabled) {
        usingSoftwareDecoderOnly = softwareDecoderOnly

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1000, 3000, 500, 1000)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
        renderersFactory.setEnableDecoderFallback(true)
        if (softwareDecoderOnly) {
            val softwareCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                val allDecoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                val softwareDecoders = allDecoders.filter { !it.hardwareAccelerated }
                if (softwareDecoders.isNotEmpty()) softwareDecoders else allDecoders
            }
            renderersFactory.setMediaCodecSelector(softwareCodecSelector)
        }

        player = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also {
                it.addListener(playerListener)
                it.addAnalyticsListener(analyticsListener)
                it.addAnalyticsListener(playbackStatsListener)
                it.setVideoFrameMetadataListener(videoFrameListener)
            }

        // Keep volume local to the player. The device media stream remains the hardware-level
        // volume, while this gain is the detail-page gesture's reliable, route-independent layer.
        val initialVolume = _state.value.volume.coerceIn(0f, 1f)
        player?.volume = initialVolume
        if (initialVolume > 0f) {
            lastNonZeroVolume = initialVolume
        }
        _state.value = _state.value.copy(
            volume = initialVolume,
            playerGeneration = _state.value.playerGeneration + 1L
        )
    }

    fun play(
        urls: List<String>,
        headers: Map<String, String>? = null,
        startIndex: Int = 0,
        origin: PlaybackRequestOrigin = PlaybackRequestOrigin.UserSelection
    ) {
        if (origin == PlaybackRequestOrigin.UserSelection) recoveryBudget.resetForUserSelection()
        val processedUrls = buildPlaybackUrlCandidates(urls, forceHttps)
        this.urls = processedUrls
        this.headers = headers
        this.currentUrlIndex = startIndex
        this.softwareDecoderFallbackAttempted = false
        playCurrentUrl()
    }

    private fun playCurrentUrl() {
        val p = player ?: return
        missingPlaybackUrlError(urls, currentUrlIndex)?.let { message ->
            _state.value = _state.value.copy(
                isLoading = false,
                error = message,
                currentUrl = null
            )
            return
        }

        val url = urls[currentUrlIndex]
        unsupportedPlaybackUrlError(url)?.let { message ->
            _state.value = _state.value.copy(
                isLoading = false,
                error = message,
                currentUrl = url
            )
            return
        }

        _state.value = _state.value.copy(isLoading = true, currentUrl = url)

        val requestHeaders = headers
        val dataSourceFactory = if (requestHeaders != null) {
            DefaultHttpDataSource.Factory().setDefaultRequestProperties(requestHeaders)
        } else {
            DefaultHttpDataSource.Factory()
        }

        val mediaSource = when {
            url.contains(".m3u8") || url.contains(".m3u") -> {
                HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url))
            }
            else -> {
                val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
                    .setConstantBitrateSeekingEnabled(true)
                ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                    .createMediaSource(MediaItem.fromUri(url))
            }
        }

        sourceStartedAtMillis = SystemClock.elapsedRealtime()
        firstFrameMillis = null
        frameRateSampleAtMillis = sourceStartedAtMillis
        frameRateSampleCount = videoFrameCount.get()
        outputFrameRate = null
        p.setMediaSource(mediaSource)
        currentVideoFormat = null
        currentVideoDecoderName = null
        mediaToken += 1L
        p.prepare()
        p.playWhenReady = true
        startStallWatchdog()
    }

    private fun shouldRetryWithSoftwareDecoder(errorText: String): Boolean {
        return hardwareDecodeEnabled &&
            !usingSoftwareDecoderOnly &&
            !softwareDecoderFallbackAttempted &&
            isVideoDecoderPlaybackError(errorText)
    }

    private fun retryCurrentUrlWithSoftwareDecoder() {
        val previousPlayer = player ?: return
        val oldStats = playbackStatsListener.combinedPlaybackStats
        previousPlayerPlayMillis += oldStats?.totalPlayTimeMs ?: 0L
        previousPlayerRebufferMillis += oldStats?.totalRebufferTimeMs ?: 0L
        previousPlayer.removeAnalyticsListener(playbackStatsListener)
        playbackStatsListener = PlaybackStatsListener(false, null)
        softwareDecoderFallbackAttempted = true
        player?.removeListener(playerListener)
        player?.removeAnalyticsListener(analyticsListener)
        player?.removeAnalyticsListener(playbackStatsListener)
        player?.release()
        player = null
        initialize(softwareDecoderOnly = true)
        onPlayerReplaced?.invoke(previousPlayer, checkNotNull(player))
        _state.value = _state.value.copy(isLoading = true, error = null)
        playCurrentUrl()
    }

    fun showError(message: String) {
        stopStallWatchdog(resetMonitor = true)
        player?.stop()
        _state.value = _state.value.copy(
            isPlaying = false,
            isLoading = false,
            error = message.ifBlank { "播放失败" },
            currentUrl = null
        )
    }

    fun setForceHttps(enabled: Boolean) {
        forceHttps = enabled
    }

    fun togglePlayPause() {
        val p = player ?: return
        p.playWhenReady = !p.playWhenReady
        if (p.playWhenReady) startStallWatchdog() else stopStallWatchdog(resetMonitor = true)
    }

    fun pause() {
        player?.playWhenReady = false
        stopStallWatchdog(resetMonitor = true)
    }

    fun resume() {
        player?.playWhenReady = true
        startStallWatchdog()
    }

    fun setVolume(delta: Float) {
        val current = _state.value.volume
        val newVol = (current + delta).coerceIn(0f, 1f)
        setVolumeDirect(newVol)
    }

    fun setVolumeDirect(volume: Float) {
        val targetVolume = volume.coerceIn(0f, 1f)
        player?.volume = targetVolume
        if (targetVolume > 0f) {
            lastNonZeroVolume = targetVolume
        }
        _state.value = _state.value.copy(volume = targetVolume)
    }

    /**
     * Adjust the device media stream used by the vertical volume gesture.
     *
     * This intentionally does not touch [player]?.volume: the reference player keeps the
     * gesture-controlled system stream separate from the player's own gain/mute setting.
     */
    fun setSystemVolumeDirect(volume: Float) {
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            streamVolumeIndexFor(volume),
            0
        )
    }

    internal fun currentSystemMediaVolume(): Float {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            .coerceIn(0, maxSystemVolume)
        return current.toFloat() / maxSystemVolume
    }

    fun toggleMute() {
        val current = _state.value.volume
        if (current > 0f) {
            lastNonZeroVolume = current
            setVolumeDirect(0f)
        } else {
            setVolumeDirect(lastNonZeroVolume.coerceIn(0.01f, 1f))
        }
    }

    private fun streamVolumeIndexFor(volume: Float): Int {
        val clamped = volume.coerceIn(0f, 1f)
        if (clamped <= 0f) return 0
        return (clamped * maxSystemVolume).roundToInt().coerceIn(1, maxSystemVolume)
    }

    fun setBrightness(activity: android.app.Activity, delta: Float) {
        val layoutParams = activity.window.attributes
        val currentBrightness = if (layoutParams.screenBrightness < 0) {
            try {
                Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            } catch (_: Exception) { 0.5f }
        } else {
            layoutParams.screenBrightness
        }
        val newBrightness = (currentBrightness + delta).coerceIn(0.01f, 1f)
        layoutParams.screenBrightness = newBrightness
        activity.window.attributes = layoutParams
        _state.value = _state.value.copy(brightness = newBrightness)
    }

    fun toggleFullscreen() {
        _state.value = _state.value.copy(isFullscreen = !_state.value.isFullscreen)
    }

    /**
     * Stop playback and clear the video surface.
     * Call this before navigating away to prevent the last frame from lingering.
     */
    fun stop() {
        stopStallWatchdog(resetMonitor = true)
        player?.stop()
        _state.value = _state.value.copy(isPlaying = false, isLoading = false)
    }

    fun release() {
        stopStallWatchdog(resetMonitor = true)
        player?.removeListener(playerListener)
        player?.removeAnalyticsListener(analyticsListener)
        player?.removeAnalyticsListener(playbackStatsListener)
        player?.release()
        player = null
    }

    fun getUrls(): List<String> = urls
    fun getCurrentUrlIndex(): Int = currentUrlIndex
    fun changeLine(index: Int) {
        if (index in urls.indices) {
            onUserPlaybackSelection?.invoke()
            play(urls, headers, index)
        }
    }

    fun switchQuality(urls: List<String>, headers: Map<String, String>? = null) {
        onUserPlaybackSelection?.invoke()
        play(urls, headers, 0)
    }

    internal fun playbackDiagnostics(qualityName: String): PlaybackDiagnosticSnapshot {
        val format = currentVideoFormat
        val stats = playbackStatsListener.combinedPlaybackStats
        return buildPlaybackDiagnosticSnapshot(
            PlaybackDiagnosticInput(
                qualityName = qualityName,
                currentUrl = _state.value.currentUrl,
                currentLineIndex = currentUrlIndex,
                lineCount = urls.size,
                playbackState = playbackStateName(player?.playbackState),
                sampleMimeType = format?.sampleMimeType,
                codecs = format?.codecs,
                width = format?.width ?: 0,
                height = format?.height ?: 0,
                frameRate = format?.frameRate ?: 0f,
                bitrate = format?.bitrate ?: 0,
                decoderName = currentVideoDecoderName,
                droppedFrames = droppedVideoFrames,
                bufferingCount = bufferingCount,
                stallRecoveryCount = stallRecoveryCount,
                bufferedMillis = player?.totalBufferedDuration?.coerceAtLeast(0L),
                liveOffsetMillis = player?.currentLiveOffset?.takeIf { it >= 0L },
                firstFrameMillis = firstFrameMillis,
                outputFrameRate = outputFrameRate.takeIf { player?.isPlaying == true && videoOutputExpected },
                playMillis = previousPlayerPlayMillis + (stats?.totalPlayTimeMs ?: 0L),
                rebufferMillis = previousPlayerRebufferMillis + (stats?.totalRebufferTimeMs ?: 0L),
                recoveryAttempts = recoveryBudget.attempts,
                lastRecoveryReason = lastRecoveryReason
            )
        )
    }

    private fun startStallWatchdog() {
        if (stallWatchdogRunning) return
        stallWatchdogRunning = true
        frameRateSampleAtMillis = SystemClock.elapsedRealtime()
        frameRateSampleCount = videoFrameCount.get()
        stallHandler.postDelayed(stallWatchdog, STALL_SAMPLE_INTERVAL_MILLIS)
    }

    private fun stopStallWatchdog(resetMonitor: Boolean) {
        stallWatchdogRunning = false
        stallHandler.removeCallbacks(stallWatchdog)
        if (resetMonitor) stallMonitor.reset()
    }

    private fun samplePlaybackStall() {
        val currentPlayer = player ?: return
        val nowMillis = SystemClock.elapsedRealtime()
        val frameCount = videoFrameCount.get()
        val sampledAt = frameRateSampleAtMillis
        if (sampledAt != null && nowMillis > sampledAt) {
            outputFrameRate = if (videoOutputExpected && currentPlayer.isPlaying)
                (frameCount - frameRateSampleCount) * 1000f / (nowMillis - sampledAt) else null
        }
        frameRateSampleAtMillis = nowMillis
        frameRateSampleCount = frameCount
        val progressState = when (currentPlayer.playbackState) {
            Player.STATE_READY -> PlaybackProgressState.Ready
            Player.STATE_BUFFERING -> PlaybackProgressState.Buffering
            Player.STATE_ENDED -> PlaybackProgressState.Ended
            else -> PlaybackProgressState.Idle
        }
        val observedVideoFrames = videoFrameCount.get().takeIf {
            videoOutputExpected && currentVideoFormat?.sampleMimeType?.startsWith("video/") == true
        }
        recoveryBudget.observe(SystemClock.elapsedRealtime(), mediaToken, currentPlayer.currentPosition,
            currentPlayer.isPlaying && progressState == PlaybackProgressState.Ready, observedVideoFrames)
        val action = stallMonitor.sample(
            nowMillis = SystemClock.elapsedRealtime(),
            mediaToken = mediaToken,
            positionMillis = currentPlayer.currentPosition.coerceAtLeast(0L),
            playWhenReady = currentPlayer.playWhenReady,
            isPlaying = currentPlayer.isPlaying,
            state = progressState,
            videoFrames = observedVideoFrames
        )
        if (action == PlaybackStallAction.Recover) recoverPlaybackStall()
    }

    fun setVideoOutputExpected(expected: Boolean) {
        if (videoOutputExpected == expected) return
        videoOutputExpected = expected
        stallMonitor.reset()
    }

    private fun runRecovery(reason: String, recover: () -> Boolean): Boolean {
        if (!recoveryBudget.tryAcquire()) {
            showError("自动恢复已达上限，请手动刷新或切换画质")
            return false
        }
        lastRecoveryReason = reason
        _state.value = _state.value.copy(isLoading = true, error = null)
        val started = recover()
        if (started && player?.playbackState != Player.STATE_READY && _state.value.error == null) {
            _state.value = _state.value.copy(isLoading = true)
        }
        return started
    }

    private fun recoverPlaybackStall() {
        Timber.w("Live playback stalled; attempting bounded source recovery")
        val started = when (playbackStallRecoveryAction(currentUrlIndex, urls.size)) {
            PlaybackStallRecoveryAction.SwitchToNextUrl -> runRecovery("播放停滞换线") {
                currentUrlIndex++
                softwareDecoderFallbackAttempted = false
                playCurrentUrl()
                true
            }
            PlaybackStallRecoveryAction.RequestFreshSource ->
                runRecovery("播放停滞重新取流") { onPlaybackSourceExhausted?.invoke() == true }
        }
        if (started) stallRecoveryCount++
        else if (_state.value.error == null) showError("直播流长时间无进度，请刷新重试")
    }
    private companion object {
        const val STALL_SAMPLE_INTERVAL_MILLIS = 3_000L
    }
}

private fun playbackStateName(state: Int?): String = when (state) {
    Player.STATE_IDLE -> "IDLE"
    Player.STATE_BUFFERING -> "BUFFERING"
    Player.STATE_READY -> "READY"
    Player.STATE_ENDED -> "ENDED"
    else -> "UNKNOWN"
}

internal fun missingPlaybackUrlError(urls: List<String>, startIndex: Int): String? {
    return if (urls.getOrNull(startIndex) == null) "暂无可播放线路" else null
}

internal fun unsupportedPlaybackUrlError(url: String): String? {
    return if (url.startsWith("rtmp://", ignoreCase = true)) "当前版本暂不支持 RTMP 线路" else null
}

internal fun buildPlaybackUrlCandidates(urls: List<String>, forceHttps: Boolean): List<String> {
    if (!forceHttps) return urls

    return urls.flatMap { url ->
        if (url.startsWith("http://")) {
            val httpsUrl = "https://" + url.substring(7)
            listOf(httpsUrl)
        } else {
            listOf(url)
        }
    }.distinct()
}

internal fun isVideoDecoderPlaybackError(errorText: String?): Boolean {
    val lowerText = errorText.orEmpty().lowercase()
    return lowerText.contains("mediacodecvideorenderer") ||
        (lowerText.contains("mediacodec") && lowerText.contains("video")) ||
        (lowerText.contains("decoder") && lowerText.contains("video"))
}

internal fun userVisiblePlaybackError(errorText: String?): String {
    val message = errorText.orEmpty().trim()
    if (isVideoDecoderPlaybackError(message)) {
        return "视频解码失败，已尝试切换解码方式，请刷新重试"
    }
    if (message.isBlank()) return "播放失败，请刷新重试"
    if (message.length > 48 || message.contains("Format(") || message.contains('\n')) {
        return "播放失败，请刷新重试"
    }
    return message
}

private fun playbackExceptionText(error: PlaybackException): String {
    return listOfNotNull(
        error.message,
        error.cause?.message,
        error.cause?.toString(),
        error.toString()
    ).joinToString(separator = "\n")
}
