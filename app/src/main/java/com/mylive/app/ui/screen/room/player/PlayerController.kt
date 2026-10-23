package com.mylive.app.ui.screen.room.player

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
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
import androidx.media3.exoplayer.audio.AudioSink
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
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import com.mylive.app.core.common.CoreLog

data class PlayerState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentUrl: String? = null,
    val volume: Float = 1f,
    val brightness: Float = 0.5f,
    val isFullscreen: Boolean = false,
    val playerGeneration: Long = 0L,
    val firstFrameRendered: Boolean = false,
    val playWhenReady: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val currentLineIndex: Int = 0,
    val urls: List<String> = emptyList()
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
    private val startupTiming = PlaybackStartupTiming()
    private var frameRateSampleAtMillis: Long? = null
    private var frameRateSampleCount = 0L
    private var outputFrameRate: Float? = null
    private val videoFrameListener = VideoFrameMetadataListener { _, _, _, _ -> videoFrameCount.incrementAndGet() }
    private val stallHandler = Handler(Looper.getMainLooper())
    private var stallWatchdogRunning = false
    private var mediaToken = 0L
    private var hasActiveSource = false
    /** User/system pauses invalidate delayed playback; a host pause is restored separately. */
    internal var playbackPauseGeneration = 0L
        private set
    private var pendingEndedRecovery: Runnable? = null
    private var recoveredEndedMediaToken: Long? = null
    private var currentVideoFormat: Format? = null
    @Volatile private var sourceVideoFrameRateHint: Float? = null
    @Volatile private var decoderConfiguredFrameRate: Float? = null
    private var currentVideoDecoderName: String? = null
    private var audioDiagnostics = PlaybackAudioDiagnostics()
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
            Timber.tag("LivePlayback").d(
                "State=%s, playWhenReady=%s, media=%d, line=%d/%d",
                playbackStateName(playbackState), player?.playWhenReady,
                mediaToken, currentUrlIndex + 1, urls.size
            )
            if (playbackState == Player.STATE_BUFFERING && lastPlaybackState != Player.STATE_BUFFERING) {
                bufferingCount += 1
            }
            lastPlaybackState = playbackState
            _state.value = _state.value.copy(
                playbackState = playbackState,
                playWhenReady = player?.playWhenReady == true
            )
            when (playbackState) {
                Player.STATE_IDLE -> {
                    cancelEndedRecovery()
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
                    _state.value = _state.value.copy(isPlaying = false, isLoading = false)
                    scheduleEndedRecovery()
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val reasonName = when (reason) {
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "user_or_app_request"
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "audio_focus_loss"
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "audio_becoming_noisy"
                else -> reason.toString()
            }
            Timber.tag("LivePlayback").i(
                "Play intent=%s, reason=%s, state=%s",
                playWhenReady, reasonName, playbackStateName(player?.playbackState)
            )
            _state.value = _state.value.copy(playWhenReady = playWhenReady)
            if (!playWhenReady) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                ) playbackPauseGeneration += 1L
                cancelEndedRecovery()
                stopStallWatchdog(resetMonitor = true)
            } else when (player?.playbackState ?: Player.STATE_IDLE) {
                Player.STATE_ENDED -> scheduleEndedRecovery()
                Player.STATE_READY, Player.STATE_BUFFERING -> startStallWatchdog()
                Player.STATE_IDLE -> Unit
            }
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            Timber.tag("LivePlayback").i("Playback suppression=%d", playbackSuppressionReason)
            if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                player?.playbackState == Player.STATE_ENDED
            ) scheduleEndedRecovery()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!hasActiveSource) return
            Timber.e(error, "Player error")
            val errorText = playbackExceptionText(error)
            if (!recoverPlayback(RecoveryTrigger.Error, errorText) && _state.value.error == null)
                showError(userVisiblePlaybackError(errorText))
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            if (!isCurrentSourceEvent(eventTime)) return
            audioDiagnostics = audioDiagnostics.copy(
                sampleMimeType = format.sampleMimeType,
                codecs = format.codecs,
                sampleRate = format.sampleRate.takeIf { it > 0 },
                channelCount = format.channelCount.takeIf { it > 0 }
            )
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            if (isCurrentSourceEvent(eventTime)) {
                audioDiagnostics = audioDiagnostics.copy(decoderName = decoderName)
            }
        }

        override fun onAudioDecoderReleased(eventTime: AnalyticsListener.EventTime, decoderName: String) {
            if (audioDiagnostics.decoderName == decoderName) {
                audioDiagnostics = audioDiagnostics.copy(decoderName = null, outputSampleRate = null)
            }
        }

        override fun onAudioTrackInitialized(
            eventTime: AnalyticsListener.EventTime,
            audioTrackConfig: AudioSink.AudioTrackConfig
        ) {
            if (isCurrentSourceEvent(eventTime)) {
                audioDiagnostics = audioDiagnostics.copy(outputSampleRate = audioTrackConfig.sampleRate)
            }
        }

        override fun onAudioUnderrun(
            eventTime: AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long
        ) {
            if (!isCurrentSourceEvent(eventTime)) return
            audioDiagnostics = audioDiagnostics.copy(underrunCount = audioDiagnostics.underrunCount + 1)
            val count = audioDiagnostics.underrunCount
            if ((count and (count - 1)) == 0L) {
                CoreLog.w("音频缓冲欠载: source=$mediaToken count=$count " +
                    "bufferMs=$bufferSizeMs elapsedSinceFeedMs=$elapsedSinceLastFeedMs " +
                    "sampleRate=${audioDiagnostics.sampleRate} channels=${audioDiagnostics.channelCount}")
            }
        }

        override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) {
            if (!isCurrentSourceEvent(eventTime)) return
            val discontinuity = audioSinkError as? AudioSink.UnexpectedDiscontinuityException
            audioDiagnostics = audioDiagnostics.copy(
                sinkErrorCount = audioDiagnostics.sinkErrorCount + 1,
                timestampDiscontinuityCount = audioDiagnostics.timestampDiscontinuityCount +
                    if (discontinuity != null) 1 else 0,
                lastTimestampDeltaUs = discontinuity?.let {
                    it.actualPresentationTimeUs - it.expectedPresentationTimeUs
                } ?: audioDiagnostics.lastTimestampDeltaUs
            )
            // Recoverable sink events are evidence, not a request to restart a live connection.
            // Bound repeated logging and exclude exception messages, source URLs and credentials.
            val count = audioDiagnostics.sinkErrorCount
            if ((count and (count - 1)) == 0L) {
                CoreLog.w("音频输出诊断: source=$mediaToken errors=$count " +
                    "timestampJumps=${audioDiagnostics.timestampDiscontinuityCount} " +
                    "deltaUs=${audioDiagnostics.lastTimestampDeltaUs} " +
                    "sampleRate=${audioDiagnostics.sampleRate} channels=${audioDiagnostics.channelCount} " +
                    "underruns=${audioDiagnostics.underrunCount}")
            }
        }

        override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
            val eventToken = runCatching {
                eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window())
                    .mediaItem.localConfiguration?.tag as? Long
            }.getOrNull() ?: return
            if (startupTiming.onRenderedFirstFrame(renderTimeMs, eventToken)) {
                _state.value = _state.value.copy(firstFrameRendered = true)
                Timber.tag("LivePlayback").i("First frame: room=%s ms, source=%s ms, media=%d",
                    startupTiming.roomFirstFrameMillis, startupTiming.sourceFirstFrameMillis, eventToken)
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

    private fun isCurrentSourceEvent(eventTime: AnalyticsListener.EventTime): Boolean = hasActiveSource &&
        runCatching {
            eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window())
                .mediaItem.localConfiguration?.tag as? Long
        }.getOrNull() == mediaToken

    private fun initialize(softwareDecoderOnly: Boolean = !hardwareDecodeEnabled) {
        usingSoftwareDecoderOnly = softwareDecoderOnly
        decoderConfiguredFrameRate = null
        audioDiagnostics = PlaybackAudioDiagnostics()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1000, 3000, 500, 1000)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            private val liveCodecAdapterFactory by lazy {
                LiveVideoCodecAdapterFactory(
                    super.getCodecAdapterFactory(),
                    onVideoConfigured = { decoderConfiguredFrameRate = it }
                ) { sourceVideoFrameRateHint }
            }

            override fun getCodecAdapterFactory(): MediaCodecAdapter.Factory = liveCodecAdapterFactory
        }
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
            playerGeneration = _state.value.playerGeneration + 1L,
            playWhenReady = player?.playWhenReady == true,
            playbackState = player?.playbackState ?: Player.STATE_IDLE
        )
    }

    fun beginRoomLoad(startedAtMillis: Long) {
        startupTiming.beginRoomLoad(startedAtMillis)
        _state.value = _state.value.copy(firstFrameRendered = false)
    }

    fun play(
        urls: List<String>,
        headers: Map<String, String>? = null,
        startIndex: Int = 0,
        origin: PlaybackRequestOrigin = PlaybackRequestOrigin.UserSelection,
        videoFrameRateHint: Float? = null,
        startPlayback: Boolean = origin == PlaybackRequestOrigin.UserSelection || player?.playWhenReady == true
    ) {
        if (origin == PlaybackRequestOrigin.UserSelection) recoveryBudget.resetForUserSelection()
        val processedUrls = buildPlaybackUrlCandidates(urls, forceHttps)
        val frameRateHint = videoFrameRateHint?.takeIf { it.isFinite() && it in 1f..240f }
        if (this.urls.isNotEmpty() && sourceVideoFrameRateHint != frameRateHint) {
            // FLV formats can look identical at 60 and 120 fps. Reset the retained decoder so
            // a new source cannot inherit the previous source's optional operating rate.
            decoderConfiguredFrameRate = null
            player?.stop()
        }
        this.urls = processedUrls
        this.headers = headers
        this.currentUrlIndex = startIndex
        this.softwareDecoderFallbackAttempted = false
        sourceVideoFrameRateHint = frameRateHint
        playCurrentUrl(startPlayback)
    }

    private fun playCurrentUrl(startPlayback: Boolean = player?.playWhenReady == true) {
        cancelEndedRecovery()
        hasActiveSource = false
        val p = player ?: return
        // Line identity can change while the URL stays equal, including duplicate CDNs and
        // a refreshed candidate list. Publish both so an already-open menu also observes it.
        _state.value = _state.value.copy(currentLineIndex = currentUrlIndex, urls = urls)
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

        _state.value = _state.value.copy(isLoading = true, currentUrl = url, firstFrameRendered = false)
        mediaToken += 1L
        // Format/decoder may be reused, but incident counts belong only to this connection.
        audioDiagnostics = audioDiagnostics.copy(
            underrunCount = 0,
            sinkErrorCount = 0,
            timestampDiscontinuityCount = 0,
            lastTimestampDeltaUs = null
        )
        val mediaItem = MediaItem.Builder().setUri(url).setTag(mediaToken).build()

        val requestHeaders = headers
        val dataSourceFactory = if (requestHeaders != null) {
            DefaultHttpDataSource.Factory().setDefaultRequestProperties(requestHeaders)
        } else {
            DefaultHttpDataSource.Factory()
        }

        val mediaSource = when {
            url.contains(".m3u8") || url.contains(".m3u") -> {
                HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }
            else -> {
                val extractorsFactory = LiveExtractorsFactory()
                ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                    .createMediaSource(mediaItem)
            }
        }

        val sourceStartedAtMillis = SystemClock.elapsedRealtime()
        startupTiming.beginSource(sourceStartedAtMillis, mediaToken)
        frameRateSampleAtMillis = sourceStartedAtMillis
        frameRateSampleCount = videoFrameCount.get()
        outputFrameRate = null
        currentVideoFormat = null
        currentVideoDecoderName = null
        hasActiveSource = true
        p.playWhenReady = startPlayback
        p.setMediaSource(mediaSource)
        p.prepare()
        if (p.playWhenReady) startStallWatchdog()
        else stopStallWatchdog(resetMonitor = true)
    }

    private fun shouldRetryWithSoftwareDecoder(errorText: String): Boolean {
        return hardwareDecodeEnabled &&
            !usingSoftwareDecoderOnly &&
            !softwareDecoderFallbackAttempted &&
            isVideoDecoderPlaybackError(errorText)
    }

    private fun retryCurrentUrlWithSoftwareDecoder() {
        val previousPlayer = player ?: return
        val startPlayback = previousPlayer.playWhenReady
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
        playCurrentUrl(startPlayback)
    }

    fun showError(message: String) {
        hasActiveSource = false
        startupTiming.invalidateSource()
        cancelEndedRecovery()
        stopStallWatchdog(resetMonitor = true)
        player?.stop()
        _state.value = _state.value.copy(
            isPlaying = false,
            isLoading = false,
            error = message.ifBlank { "播放失败" },
            firstFrameRendered = false,
            currentUrl = null
        )
    }

    fun setForceHttps(enabled: Boolean) {
        forceHttps = enabled
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_ENDED) {
            // At EOF the UI shows Play although Media3 may still have playWhenReady=true.
            onUserPlaybackSelection?.invoke()
            recoveryBudget.resetForUserSelection()
            recoveredEndedMediaToken = null
            resume()
            return
        }
        if (p.playWhenReady) pause() else resume()
    }

    fun pause() {
        playbackPauseGeneration += 1L
        pauseForHost()
    }

    internal fun pauseForHost() {
        cancelEndedRecovery()
        player?.playWhenReady = false
        stopStallWatchdog(resetMonitor = true)
    }

    fun resume() {
        player?.playWhenReady = true
        if (player?.playbackState == Player.STATE_ENDED) scheduleEndedRecovery()
        else startStallWatchdog()
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
        hasActiveSource = false
        startupTiming.invalidateSource()
        decoderConfiguredFrameRate = null
        _state.value = _state.value.copy(firstFrameRendered = false)
        cancelEndedRecovery()
        stopStallWatchdog(resetMonitor = true)
        player?.stop()
        _state.value = _state.value.copy(isPlaying = false, isLoading = false)
    }

    fun release() {
        hasActiveSource = false
        startupTiming.invalidateSource()
        decoderConfiguredFrameRate = null
        _state.value = _state.value.copy(firstFrameRendered = false)
        cancelEndedRecovery()
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
            play(urls, headers, index, videoFrameRateHint = sourceVideoFrameRateHint)
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
                firstFrameMillis = startupTiming.sourceFirstFrameMillis,
                roomFirstFrameMillis = startupTiming.roomFirstFrameMillis,
                outputFrameRate = outputFrameRate.takeIf { player?.isPlaying == true && videoOutputExpected },
                playMillis = previousPlayerPlayMillis + (stats?.totalPlayTimeMs ?: 0L),
                rebufferMillis = previousPlayerRebufferMillis + (stats?.totalRebufferTimeMs ?: 0L),
                recoveryAttempts = recoveryBudget.attempts,
                lastRecoveryReason = lastRecoveryReason,
                decoderConfiguredFrameRate = decoderConfiguredFrameRate,
                audio = audioDiagnostics
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
            Timber.tag("LivePlayback").w("Recovery budget exhausted: %s", reason)
            showError("自动恢复已达上限，请手动刷新或切换画质")
            return false
        }
        Timber.tag("LivePlayback").w(
            "Recovery: %s, attempt=%d/%d", reason, recoveryBudget.attempts, MAX_PLAYBACK_RECOVERY_ATTEMPTS
        )
        lastRecoveryReason = reason
        _state.value = _state.value.copy(isLoading = true, error = null)
        val started = recover()
        if (started && player?.playbackState != Player.STATE_READY && _state.value.error == null) {
            _state.value = _state.value.copy(isLoading = true)
        }
        return started
    }

    private enum class RecoveryTrigger(val switchReason: String, val refreshReason: String) {
        Error("播放错误换线", "重新获取播放源"),
        Ended("直播流结束换线", "直播流结束重新取流"),
        Stall("播放停滞换线", "播放停滞重新取流")
    }

    /** One action order and execution path for errors, EOF and stalled playback. */
    private fun recoverPlayback(trigger: RecoveryTrigger, errorText: String? = null): Boolean {
        if (player == null || urls.isEmpty()) return false
        if (trigger == RecoveryTrigger.Error && errorText != null && shouldRetryWithSoftwareDecoder(errorText)) {
            return runRecovery("解码回退") { retryCurrentUrlWithSoftwareDecoder(); true }
        }
        return when (playbackSourceRecoveryAction(currentUrlIndex, urls.size)) {
            PlaybackSourceRecoveryAction.SwitchToNextUrl -> runRecovery(trigger.switchReason) {
                currentUrlIndex++
                softwareDecoderFallbackAttempted = false
                playCurrentUrl()
                true
            }
            PlaybackSourceRecoveryAction.RequestFreshSource ->
                runRecovery(trigger.refreshReason) { onPlaybackSourceExhausted?.invoke() == true }
        }
    }

    private fun cancelEndedRecovery() {
        pendingEndedRecovery?.let(stallHandler::removeCallbacks)
        pendingEndedRecovery = null
    }

    /** A live HTTP/FLV connection can reach EOF without an onPlayerError callback. */
    private fun scheduleEndedRecovery() {
        if (pendingEndedRecovery != null || recoveredEndedMediaToken == mediaToken) return
        val endedPlayer = player ?: return
        val endedMediaToken = mediaToken
        val recovery = Runnable {
            pendingEndedRecovery = null
            // Finish the Media3 event batch first; discard work superseded by pause, navigation,
            // another source, or decoder replacement.
            if (player !== endedPlayer || mediaToken != endedMediaToken ||
                endedPlayer.playbackState != Player.STATE_ENDED || !endedPlayer.playWhenReady ||
                endedPlayer.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE ||
                urls.getOrNull(currentUrlIndex) == null || _state.value.currentUrl == null
            ) return@Runnable
            recoveredEndedMediaToken = endedMediaToken
            val started = recoverPlayback(RecoveryTrigger.Ended)
            if (!started && _state.value.error == null) showError("直播流已结束，请刷新重试")
        }
        pendingEndedRecovery = recovery
        stallHandler.post(recovery)
    }

    private fun recoverPlaybackStall() {
        Timber.w("Live playback stalled; attempting bounded source recovery")
        val started = recoverPlayback(RecoveryTrigger.Stall)
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
