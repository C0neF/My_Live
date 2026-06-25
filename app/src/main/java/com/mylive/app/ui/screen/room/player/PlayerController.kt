package com.mylive.app.ui.screen.room.player

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import kotlin.math.roundToInt

import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

data class PlayerState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentUrl: String? = null,
    val volume: Float = 1f,
    val brightness: Float = 0.5f,
    val isFullscreen: Boolean = false
)

@OptIn(UnstableApi::class)
class PlayerController(
    private val context: Context,
    private val hardwareDecodeEnabled: Boolean = true,
    private var forceHttps: Boolean = false,
    private val onPlaybackSourceExhausted: (() -> Unit)? = null
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
    private var sourceRefreshAttempted = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    private var lastNonZeroVolume = 1f

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    sourceRefreshAttempted = false
                    _state.value = _state.value.copy(isLoading = false, error = null)
                }
                Player.STATE_BUFFERING -> {
                    _state.value = _state.value.copy(isLoading = true)
                }
                Player.STATE_ENDED -> {
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
            if (currentUrlIndex < urls.size - 1) {
                currentUrlIndex++
                softwareDecoderFallbackAttempted = false
                playCurrentUrl()
            } else if (shouldRetryWithSoftwareDecoder(errorText)) {
                retryCurrentUrlWithSoftwareDecoder()
            } else if (!sourceRefreshAttempted && onPlaybackSourceExhausted != null) {
                sourceRefreshAttempted = true
                _state.value = _state.value.copy(isLoading = true, error = null)
                onPlaybackSourceExhausted.invoke()
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = userVisiblePlaybackError(errorText)
                )
            }
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
            .build()
            .also { it.addListener(playerListener) }

        // Init volume state
        player?.volume = 1f
        val currentVol = currentSystemMediaVolume()
        if (currentVol > 0f) {
            lastNonZeroVolume = currentVol
        }
        _state.value = _state.value.copy(volume = currentVol)
    }

    fun play(
        urls: List<String>,
        headers: Map<String, String>? = null,
        startIndex: Int = 0,
        resetSourceRefreshAttempt: Boolean = true
    ) {
        val processedUrls = buildPlaybackUrlCandidates(urls, forceHttps)
        this.urls = processedUrls
        this.headers = headers
        this.currentUrlIndex = startIndex
        this.softwareDecoderFallbackAttempted = false
        if (resetSourceRefreshAttempt) {
            this.sourceRefreshAttempted = false
        }

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

        p.setMediaSource(mediaSource)
        p.prepare()
        p.playWhenReady = true
    }

    private fun shouldRetryWithSoftwareDecoder(errorText: String): Boolean {
        return hardwareDecodeEnabled &&
            !usingSoftwareDecoderOnly &&
            !softwareDecoderFallbackAttempted &&
            isVideoDecoderPlaybackError(errorText)
    }

    private fun retryCurrentUrlWithSoftwareDecoder() {
        softwareDecoderFallbackAttempted = true
        player?.removeListener(playerListener)
        player?.release()
        player = null
        initialize(softwareDecoderOnly = true)
        _state.value = _state.value.copy(isLoading = true, error = null)
        playCurrentUrl()
    }

    fun showError(message: String) {
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
    }

    fun pause() {
        player?.playWhenReady = false
    }

    fun resume() {
        player?.playWhenReady = true
    }

    fun setVolume(delta: Float) {
        val current = _state.value.volume
        val newVol = (current + delta).coerceIn(0f, 1f)
        setVolumeDirect(newVol)
    }

    fun setVolumeDirect(volume: Float) {
        val targetVolume = volume.coerceIn(0f, 1f)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            streamVolumeIndexFor(targetVolume),
            0
        )
        player?.volume = 1f
        val appliedVolume = currentSystemMediaVolume()
        if (appliedVolume > 0f) {
            lastNonZeroVolume = appliedVolume
        }
        _state.value = _state.value.copy(volume = appliedVolume)
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

    private fun currentSystemMediaVolume(): Float {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            .coerceIn(0, maxVolume)
        return current.toFloat() / maxVolume
    }

    private fun streamVolumeIndexFor(volume: Float): Int {
        val clamped = volume.coerceIn(0f, 1f)
        if (clamped <= 0f) return 0
        return (clamped * maxVolume).roundToInt().coerceIn(1, maxVolume)
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
        player?.stop()
        _state.value = _state.value.copy(isPlaying = false, isLoading = false)
    }

    fun release() {
        player?.removeListener(playerListener)
        player?.release()
        player = null
    }

    fun getUrls(): List<String> = urls
    fun getCurrentUrlIndex(): Int = currentUrlIndex
    fun changeLine(index: Int) {
        if (index in urls.indices) {
            play(urls, headers, index)
        }
    }

    fun switchQuality(urls: List<String>, headers: Map<String, String>? = null) {
        play(urls, headers, 0)
    }
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
