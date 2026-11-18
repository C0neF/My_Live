package com.mylive.desktop

import com.mylive.app.core.model.LivePlayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeout
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.MediaStatus
import java.util.Locale
import java.net.URI
import java.nio.file.Path
import java.io.IOException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkikoProperties
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

internal fun sanitizeMediaLogLine(line: String): String = line
    .replace(Regex("https?://[^\\s]+")) { match ->
        runCatching { URI(match.value).let { uri -> uri.host?.let { "${uri.scheme}://$it/<redacted>" } } }.getOrNull() ?: "<url>"
    }
    .replace(Regex("(?i)\\b(cookie|authorization|proxy-authorization)\\s*[:=][^\\r\\n]*"), "$1: <redacted>")

fun normalizeMediaLocation(location: String): String {
    val value = location.trim()
    return if (value.startsWith("file:", ignoreCase = true)) Path.of(URI(value)).toString() else value
}

interface DesktopPlayback : AutoCloseable {
    fun beginRoom() {}
    suspend fun open(url: String, headers: Map<String, String>)
    fun stop()
}

internal class DesktopPlaybackIntent {
    var playing = true
        private set
    var revision = 0L
        private set
    fun request(playing: Boolean) {
        this.playing = playing
        revision++
    }
    suspend fun loadSource(open: suspend (Boolean) -> Unit, applyIntent: (Boolean) -> Unit) {
        val requestedRevision = revision
        open(playing)
        currentCoroutineContext().ensureActive()
        if (requestedRevision != revision) applyIntent(playing)
    }
}

data class PlaybackDiagnostics(
    val width: Int = 0,
    val height: Int = 0,
    val sourceFps: Double = 0.0,
    val droppedFrames: Int = 0,
    val decoderDroppedFrames: Int = 0,
    val hardwareDecoder: String = "",
    val cacheSeconds: Double = 0.0,
    val positionSeconds: Double = 0.0
) {
    val summary: String get() = if (width > 0) String.format(
        Locale.ROOT, "%d × %d · 源 %.1f fps · 丢帧 %d / 解码 %d · %s · 缓冲 %.1f s",
        width, height, sourceFps, droppedFrames, decoderDroppedFrames,
        hardwareDecoder.ifBlank { "软件解码" }, cacheSeconds
    ) else "等待视频信息"
}

class MpvDesktopPlayback(context: Any, scope: CoroutineScope) : DesktopPlayback {
    private var preferences = DesktopPlayerPreferences()
    private val nativePlayer = lazy {
        MpvMediampPlayer(context, scope.coroutineContext).also { player ->
            val handle = player.impl as MPVHandle
            handle.setPropertyString("hwdec", if (preferences.hardwareDecode) enabledHardwareDecoder else "no")
            handle.setPropertyString("cache", "yes")
            handle.setPropertyString("demuxer-max-bytes", (32L * 1024 * 1024).toString())
            handle.setPropertyString("demuxer-max-back-bytes", "0")
            handle.setPropertyDouble("cache-secs", 4.0)
            handle.setPropertyDouble("cache-pause-wait", 1.0)
            handle.setPropertyDouble("volume", preferences.volume.toDouble())
            handle.setPropertyString("mute", if (preferences.muted) "yes" else "no")
            handle.setPropertyString("keepaspect", if (preferences.scale == DesktopVideoScale.Stretch) "no" else "yes")
            handle.setPropertyDouble("panscan", if (preferences.scale == DesktopVideoScale.Crop) 1.0 else 0.0)
        }
    }
    val player by nativePlayer
    internal val playerInitialized: Boolean get() = nativePlayer.isInitialized()
    internal var videoUploadStats = DesktopVideoUploadStats()
    private val handle get() = player.impl as MPVHandle
    // Preserve MediaMP's decode policy for independent Windows OpenGL contexts.
    private val enabledHardwareDecoder = if (hostOs == OS.Windows && SkikoProperties.renderApi == GraphicsApi.OPENGL)
        "d3d11va-copy,auto-safe" else "auto"
    private val intent = DesktopPlaybackIntent()
    val playbackRequested: Boolean get() = intent.playing && (!playerInitialized || player.state.value.mediaStatus != MediaStatus.Ended)

    override suspend fun open(url: String, headers: Map<String, String>) {
        // mpv retains these options between loads. Always set both, including on a cross-site switch.
        val normalized = linkedMapOf("User-Agent" to DEFAULT_USER_AGENT, "Referer" to "")
        headers.forEach { (name, value) ->
            require(!value.contains('\r') && !value.contains('\n')) { "Invalid media header" }
            normalized[when {
                name.equals("user-agent", true) -> "User-Agent"
                name.equals("referer", true) -> "Referer"
                else -> name
            }] = value
        }
        try {
            intent.loadSource(open = { playing ->
                withTimeout(30_000) {
                    player.setMediaData(UriMediaData(normalizeMediaLocation(url), normalized), playWhenReady = playing)
                }
            }, applyIntent = { playing -> if (playing) player.play() else player.pause() })
        } catch (timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw IOException("Media connection timed out", timeout)
        }
    }

    override fun beginRoom() { intent.request(true) }
    fun pause() { intent.request(false); if (playerInitialized) player.pause() }
    fun resume() { intent.request(true); if (playerInitialized) player.play() }
    fun togglePlayPause() {
        val currentlyPlaying = if (playerInitialized && player.state.value.mediaStatus != MediaStatus.Idle) {
            player.state.value.playWhenReady
        } else {
            playbackRequested
        }
        if (currentlyPlaying) pause() else resume()
    }
    fun setVolume(value: Float) {
        applyPreferences(preferences.copy(volume = value.coerceIn(0f, 100f)))
    }
    fun applyPreferences(next: DesktopPlayerPreferences) {
        val previous = preferences
        preferences = next
        if (!playerInitialized) return
        if (previous.volume != next.volume) handle.setPropertyDouble("volume", next.volume.toDouble())
        if (previous.muted != next.muted) {
            handle.setPropertyString("mute", if (next.muted) "yes" else "no")
        }
        if (previous.hardwareDecode != next.hardwareDecode) {
            handle.setPropertyString("hwdec", if (next.hardwareDecode) enabledHardwareDecoder else "no")
        }
        if (previous.scale != next.scale) {
            handle.setPropertyString("keepaspect", if (next.scale == DesktopVideoScale.Stretch) "no" else "yes")
            handle.setPropertyDouble("panscan", if (next.scale == DesktopVideoScale.Crop) 1.0 else 0.0)
        }
    }
    fun diagnostics(): PlaybackDiagnostics = if (!playerInitialized) PlaybackDiagnostics() else PlaybackDiagnostics(
        width = handle.getPropertyInt("width"),
        height = handle.getPropertyInt("height"),
        sourceFps = handle.getPropertyDouble("container-fps").takeIf { it.isFinite() } ?: 0.0,
        droppedFrames = handle.getPropertyInt("frame-drop-count"),
        decoderDroppedFrames = handle.getPropertyInt("decoder-frame-drop-count"),
        hardwareDecoder = handle.getPropertyString("hwdec-current").orEmpty().takeUnless { it == "no" }.orEmpty(),
        cacheSeconds = handle.getPropertyDouble("demuxer-cache-duration").takeIf { it.isFinite() } ?: 0.0,
        positionSeconds = handle.getPropertyDouble("time-pos").takeIf { it.isFinite() } ?: 0.0
    )

    override fun stop() { intent.request(false); if (playerInitialized) player.stopPlayback() }
    override fun close() { if (playerInitialized) player.close() }

    private companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36"
    }
}

/** Header and quality metadata stay attached to every CDN retry. */
suspend fun openPlaybackLine(playback: DesktopPlayback, source: LivePlayUrl, preferred: Int = 0): Int {
    require(source.urls.isNotEmpty()) { "直播源为空" }
    var lastError: Exception? = null
    val order = source.urls.indices.toList().let { indices ->
        listOf(preferred.coerceIn(0, indices.lastIndex)) + indices.filter { it != preferred }
    }.distinct()
    for (index in order) {
        try {
            playback.open(source.urls[index], source.headers.orEmpty())
            return index
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            lastError = error
        }
    }
    throw IllegalStateException("所有播放线路均连接失败", lastError)
}
