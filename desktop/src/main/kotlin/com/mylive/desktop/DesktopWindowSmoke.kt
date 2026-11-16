package com.mylive.desktop

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.awt.SystemTray
import java.nio.file.Files
import java.nio.file.Path

/** App-owned acceptance sequence: no external keyboard/mouse or desktop automation. */
internal suspend fun desktopWindowSmoke(directory: Path, window: ComposeWindow, state: WindowState,
    mode: DesktopWindowMode, playback: MpvDesktopPlayback, library: DesktopLibrary, onVisible: (Boolean) -> Unit) {
    Files.createDirectories(directory)
    val result = JSONObject().put("version", DESKTOP_VERSION)
    val phases = JSONArray(); result.put("phases", phases)
    fun playbackState(): JSONObject {
        val video = playback.diagnostics()
        return JSONObject().put("windowVisible", window.isVisible).put("windowShowing", window.isShowing)
            .put("windowDisplayable", window.isDisplayable).put("mediaStatus", playback.player.state.value.mediaStatus.toString())
            .put("playWhenReady", playback.player.state.value.playWhenReady).put("width", video.width)
            .put("height", video.height).put("positionSeconds", video.positionSeconds)
    }
    result.put("initialState", playbackState())
    var previousVideoFrames = 0L
    suspend fun phase(name: String) {
        var frames = playback.videoUploadStats
        if (name in setOf("fullscreen", "restored", "mini", "restored_from_mini") && previousVideoFrames > 0) {
            val initialFrames = frames.uploads
            delay(150)
            frames = playback.videoUploadStats
            check(frames.uploads > initialFrames) { "Video frames stopped after $name" }
        }
        previousVideoFrames = frames.uploads
        phases.put(JSONObject().put("name", name).put("width", window.width).put("height", window.height)
        .put("mini", mode.isMini).put("onTop", window.isAlwaysOnTop).put("playWhenReady", playback.player.state.value.playWhenReady)
        .put("video", playback.diagnostics().summary).put("videoFrames", frames.uploads)
        .put("gpuTexturesAllocated", frames.gpuTextures).put("cpuFrameBuffers", frames.cpuBuffers))
    }
    try {
        withTimeout(25_000) { while (playback.diagnostics().width <= 0 || playback.diagnostics().positionSeconds < 1.0) delay(100) }
        delay(1500)
        phase("normal")
        val normalWidth = window.width; val normalHeight = window.height
        mode.toggle(); delay(1800); phase("fullscreen")
        check(state.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen)
        mode.exit(); delay(1800); phase("restored")
        check(kotlin.math.abs(window.width - normalWidth) <= 3 && kotlin.math.abs(window.height - normalHeight) <= 3)
        mode.toggleMini(); delay(1600); phase("mini")
        check(mode.isMini && window.isAlwaysOnTop && window.width in 360..520 && window.height in 220..350)
        mode.toggleMini(); delay(1500); phase("restored_from_mini")
        check(!mode.isMini && kotlin.math.abs(window.width - normalWidth) <= 3)
        check(library.updatePlayer { it.copy(alwaysOnTop = true) }); delay(300); check(window.isAlwaysOnTop)
        check(library.updatePlayer { it.copy(alwaysOnTop = false, minimizeToTray = false, backgroundPlayback = false) }); delay(300)
        val traySupported = SystemTray.isSupported(); result.put("traySupported", traySupported)
        val previousIcons = if (traySupported) SystemTray.getSystemTray().trayIcons.size else 0
        check(library.updatePlayer { it.copy(minimizeToTray = true) }); delay(400)
        if (traySupported) check(SystemTray.getSystemTray().trayIcons.size > previousIcons)
        onVisible(false); delay(500); phase("hidden")
        check(!playback.player.state.value.playWhenReady)
        onVisible(true); delay(500); phase("shown")
        check(playback.player.state.value.playWhenReady)
        playback.pause(); onVisible(false); delay(250); onVisible(true); delay(400)
        check(!playback.player.state.value.playWhenReady)
        check(library.updatePlayer { it.copy(minimizeToTray = false) }); delay(400)
        if (traySupported) check(SystemTray.getSystemTray().trayIcons.size == previousIcons)
        phase("paused_by_user")
        result.put("passed", true)
    } catch (error: Throwable) {
        result.put("passed", false).put("errorType", error.javaClass.simpleName).put("message", redactDesktopLog(error.message.orEmpty()))
            .put("finalState", playbackState())
            .put("logs", JSONObject(DesktopDiagnostics.snapshot(DesktopRoomState(), playback.diagnostics())).getJSONArray("logs"))
    } finally {
        writeDesktopFileAtomically(directory.resolve("window-smoke.json"), result.toString(2).toByteArray(Charsets.UTF_8))
    }
}
