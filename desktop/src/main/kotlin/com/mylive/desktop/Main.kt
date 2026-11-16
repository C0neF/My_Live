package com.mylive.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.awt.Dimension
import java.awt.SystemTray
import java.io.File
import org.json.JSONObject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.skiko.Arch
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostArch
import org.jetbrains.skiko.hostOs

private class DesktopWindowSession(window: Any, scope: CoroutineScope, services: DesktopServices) {
    val playback = MpvDesktopPlayback(window, scope)
    val controller = DesktopRoomController(services.sites, playback, library = services.library)
    val browse = DesktopBrowseController(services.sites)
    val follows = DesktopFollowController(services.library, services.sites)
    suspend fun close() {
        browse.close()
        follows.close()
        controller.shutdown()
    }
}

fun main(args: Array<String>) {
    System.getProperties().putIfAbsent("skiko.gpu.resourceCacheLimit", DESKTOP_GRAPHICS_CACHE_BYTES.toString())
    // Use the Windows x64 OpenGL compatibility path across GPU vendors. Other
    // systems/architectures retain Skiko's platform default, such as Metal on macOS.
    // An explicit Skiko property/environment setting still wins.
    // https://github.com/JetBrains/compose-multiplatform/issues/1648
    if (hostOs == OS.Windows && hostArch == Arch.X64) {
        System.getProperties().putIfAbsent("skiko.renderApi", "OPENGL")
    }
    val startInBackground = "--background" in args
    val windowSmoke = args.firstOrNull { it.startsWith("--window-smoke=") }?.substringAfter('=')?.let(java.nio.file.Path::of)
    if (windowSmoke != null) System.setProperty("mylive.dataDir", windowSmoke.resolve("profile").toAbsolutePath().toString())
    DesktopDiagnostics.install()
    val runtime = File(System.getProperty("compose.application.resources.dir"), "mpv")
    require(File(runtime, "libmpv-2.dll").isFile) { "Windows media runtime missing; run :desktop:prepareWindowsMediaRuntime" }
    args.firstOrNull { it.startsWith("--self-check=") }?.substringAfter('=')?.let { directory ->
        prepareDesktopMediaRuntime(runtime)
        val passed = DesktopSelfCheck.run(java.nio.file.Path.of(directory), args.firstOrNull { it.startsWith("--self-check-media=") }?.substringAfter('='))
        kotlin.system.exitProcess(if (passed) 0 else 1)
    }
    val services = DesktopServices().also { configureDesktopImages(it.client) }
    if (startInBackground) runBlocking { initializeDesktopRuntime(services, runtime) }
    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 820.dp)
        val windowMode = remember { DesktopWindowMode(windowState) }
        val scope = rememberCoroutineScope()
        var closing by remember { mutableStateOf(false) }
        var windowVisible by remember { mutableStateOf(!startInBackground) }
        var roomFocusRequest by remember { mutableIntStateOf(0) }
        val visibilityPolicy = remember { DesktopVisibilityPolicy() }
        var windowSession by remember { mutableStateOf<DesktopWindowSession?>(null) }
        val hasRoom by remember(windowSession) {
            windowSession?.controller?.state?.map { it.address != null || it.detail != null }?.distinctUntilChanged()
                ?: flowOf(false)
        }.collectAsState(initial = false)
        var startupReady by remember { mutableStateOf(startInBackground) }
        LaunchedEffect(services) {
            if (!startInBackground) initializeDesktopRuntime(services, runtime)
            startupReady = true
        }
        val libraryState by services.library.state.collectAsState()
        val traySupported = remember { SystemTray.isSupported() }
        val icon = painterResource("icons/mylive.png")
        if (!closing && traySupported && (libraryState.player.minimizeToTray || !windowVisible)) Tray(icon = icon,
            tooltip = DESKTOP_APP_NAME, onAction = { windowVisible = true }, menu = {
                Item("显示窗口", onClick = { windowVisible = true })
                Item("暂停 / 继续播放", enabled = hasRoom, onClick = {
                    visibilityPolicy.manuallyChanged()
                    windowSession?.playback?.let { playback ->
                        if (playback.player.state.value.playWhenReady) playback.pause() else playback.resume()
                    }
                })
                Separator()
                Item("退出", onClick = { closing = true })
            })
        LaunchedEffect(windowVisible, libraryState.player.backgroundPlayback, windowSession, hasRoom) {
            val session = windowSession ?: return@LaunchedEffect
            when (visibilityPolicy.update(windowVisible, libraryState.player.backgroundPlayback,
                session.playback.playbackRequested, hasRoom)) {
                VisibilityPlaybackAction.Pause -> session.playback.pause()
                VisibilityPlaybackAction.Resume -> session.playback.resume()
                VisibilityPlaybackAction.None -> Unit
            }
        }
        LaunchedEffect(closing) {
            if (closing) {
                try {
                    DesktopWebLogin.shutdown()
                    windowSession?.close()
                    withContext(Dispatchers.IO) { services.close() }
                } finally { exitApplication() }
            }
        }
        LaunchedEffect(startupReady) {
            if (startupReady && startInBackground && windowSmoke != null) {
                val report = JSONObject().put("version", DESKTOP_VERSION).put("backgroundOnly", true)
                    .put("windowVisible", windowVisible).put("traySupported", traySupported)
                    .put("trayIconCount", if (traySupported) SystemTray.getSystemTray().trayIcons.size else 0)
                    .put("profileLoaded", libraryState.loaded).put("passed", !windowVisible && libraryState.loaded)
                java.nio.file.Files.createDirectories(windowSmoke)
                writeDesktopFileAtomically(windowSmoke.resolve("window-smoke.json"), report.toString(2).toByteArray(Charsets.UTF_8))
                closing = true
            }
        }
        // The root composition removes the native surface before the application scope closes its player.
        if (!closing) Window(
            title = DESKTOP_APP_NAME,
            state = windowState,
            visible = windowVisible,
            icon = icon,
            alwaysOnTop = libraryState.player.alwaysOnTop || windowMode.isMini,
            onCloseRequest = {
                if (!startupReady) exitApplication()
                else if (traySupported && libraryState.player.minimizeToTray) windowVisible = false else closing = true
            },
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.L) {
                    if (windowMode.isMini) windowMode.toggleMini()
                    windowMode.exit()
                    roomFocusRequest++
                    true
                } else false
            }
        ) {
            if (!startupReady) DesktopTheme(libraryState.appearance) { DesktopLoadingScreen("正在准备播放组件与本地资料…") }
            else {
            LaunchedEffect(window, windowMode.isMini) {
                window.minimumSize = if (windowMode.isMini) Dimension(360, 220) else Dimension(900, 600)
                if (windowMode.isMini) window.setSize(480, 300)
            }
            val session = windowSession ?: remember(window) { DesktopWindowSession(window, scope, services) }
            SideEffect { windowSession = session }
            val playback = session.playback
            val controller = session.controller
            val browse = session.browse
            val followRefresh = session.follows
            LaunchedEffect(windowMode.restoreSequence, windowState.placement, windowMode.isMini, closing) {
                if (!closing && !windowMode.isMini && windowMode.restoreSequence > 0 && windowState.placement == WindowPlacement.Floating) {
                    // Windows' exclusive-fullscreen restore can leave the AWT peer at physical-pixel
                    // bounds on HiDPI displays. Reapply logical bounds after the peer has reattached.
                    delay(100)
                    if (closing || windowMode.isMini || windowState.placement != WindowPlacement.Floating) return@LaunchedEffect
                    val width = windowMode.floatingSize.width.value.toInt()
                    val height = windowMode.floatingSize.height.value.toInt()
                    window.setSize(width + 1, height + 1)
                    window.setSize(width, height)
                    (windowMode.floatingPosition as? WindowPosition.Absolute)?.let { position ->
                        window.setLocation(position.x.value.toInt(), position.y.value.toInt())
                    }
                }
            }
            LaunchedEffect(Unit) {
                if (startInBackground && windowSmoke != null) return@LaunchedEffect
                if (windowSmoke != null) services.library.updatePlayer { it.copy(volume = 0f, muted = true) }
                followRefresh.startAutoRefresh()
                playback.applyPreferences(services.library.state.value.player)
                args.firstOrNull { it.startsWith("--room=") }?.substringAfter('=')?.let { room ->
                    val site = room.substringBefore(':')
                    if (services.sites.any { it.id == site }) controller.open(RoomAddress(site, room.substringAfter(':')))
                }
                args.firstOrNull { it.startsWith("--video=") }?.substringAfter('=')?.let { video ->
                    controller.openVideo(if (video.contains("://")) video else File(video).toURI().toString())
                }
                if (startInBackground && !services.library.state.value.player.backgroundPlayback) playback.pause()
                if (windowSmoke != null) {
                    try { desktopWindowSmoke(windowSmoke, window, windowState, windowMode, playback, services.library,
                        onVisible = { windowVisible = it }) }
                    finally { closing = true }
                }
            }
            if (closing) DesktopTheme(libraryState.appearance) { DesktopLoadingScreen("正在退出…") }
            else if (!libraryState.loaded) DesktopTheme(libraryState.appearance) { DesktopLoadingScreen("正在读取本地资料…") }
            else DesktopApp(services, playback, controller, browse, followRefresh, windowState, window,
                windowMode.isMini, windowMode::toggleMini, windowMode::toggle, windowMode::exit, roomFocusRequest)
            }
        }
    }
}
