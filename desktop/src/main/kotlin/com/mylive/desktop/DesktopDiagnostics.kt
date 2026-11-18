package com.mylive.desktop

import com.mylive.app.core.common.CoreLog
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.time.Instant

const val DESKTOP_VERSION = DesktopBuildInfo.VERSION
const val DESKTOP_APP_NAME = "MyLive"
internal const val DESKTOP_GRAPHICS_CACHE_BYTES = 64L * 1024 * 1024
const val DESKTOP_PROJECT_URL = "https://github.com/C0neF/My_Live"

internal fun redactDesktopLog(text: String): String = sanitizeMediaLogLine(text)
    .replace(Regex("(?i)wss?://[^\\s]+"), "<websocket-url>")
    .replace(Regex("(?i)([\"']?(?:token|password|sessdata|sessionid(?:_ss)?|sid_guard|sid_tt|auth|access_token)[\"']?\\s*[:=]\\s*)[^\\s,;}]+"), "$1<redacted>")
    .replace(Regex("(?i)[a-z]:[\\\\/]+users[\\\\/]+[^\\\\/\\s]+"), "<user-home>")
    .take(4000)

object DesktopDiagnostics {
    private val entries = ArrayDeque<JSONObject>()
    fun install() {
        CoreLog.onPrintLog = { level, line -> record(level.name, line) }
        CoreLog.configure(true, false)
    }
    @Synchronized fun record(level: String, text: String) {
        if (entries.size >= 500) entries.removeFirst()
        entries.add(JSONObject().put("time", Instant.now().toString()).put("level", level).put("message", redactDesktopLog(text)))
    }
    @Synchronized fun clear() { entries.clear(); CoreLog.clear() }
    @Synchronized fun snapshot(room: DesktopRoomState, playback: PlaybackDiagnostics): String {
        val runtime = Runtime.getRuntime()
        return JSONObject().put("type", "my_live_windows_diagnostics").put("version", DESKTOP_VERSION)
            .put("time", Instant.now().toString()).put("os", "${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
            .put("java", System.getProperty("java.version")).put("heapUsedBytes", runtime.totalMemory() - runtime.freeMemory()).put("heapMaxBytes", runtime.maxMemory())
            .put("playback", JSONObject().put("site", room.address?.siteId).put("quality", room.quality?.quality)
                .put("line", room.line).put("lineCount", room.lineCount).put("chat", redactDesktopLog(room.chatStatus))
                .put("width", playback.width).put("height", playback.height).put("sourceFps", playback.sourceFps)
                .put("dropped", playback.droppedFrames).put("decoderDropped", playback.decoderDroppedFrames)
                .put("hardwareDecoder", playback.hardwareDecoder).put("cacheSeconds", playback.cacheSeconds)
                .put("error", room.error?.let(::redactDesktopLog)))
            .put("logs", JSONArray(entries.toList())).toString(2)
    }
    fun writeNew(path: Path, room: DesktopRoomState, playback: PlaybackDiagnostics) {
        Files.writeString(path, snapshot(room, playback), CREATE_NEW)
    }
}
