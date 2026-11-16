package com.mylive.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.mylive.app.core.model.*
import com.mylive.app.core.site.LiveSiteAccount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.mpv.compose.MpvMediampPlayerSurface
import javax.swing.JFileChooser



@Composable
fun DesktopApp(
    services: DesktopServices, playback: MpvDesktopPlayback,
    controller: DesktopRoomController, browse: DesktopBrowseController, followRefresh: DesktopFollowController, windowState: WindowState,
    parentWindow: java.awt.Window,
    mini: Boolean, onToggleMini: () -> Unit,
    onToggleFullscreen: () -> Unit, onExitFullscreen: () -> Unit, roomFocusRequest: Int = 0
) {
    val library by services.library.state.collectAsState()
    DesktopTheme(library.appearance) {
    val room by controller.state.collectAsState()
    val savedPages = rememberSaveableStateHolder()
    val orderedSites = remember(services, library.appearance.platformOrder) {
        services.sites.sortedBy { library.appearance.platformOrder.indexOf(it.id) }
    }
    val scope = rememberCoroutineScope()
    var homePage by remember { mutableStateOf(DesktopHomePage.Recommend) }
    var fullArea by remember { mutableStateOf(false) }
    val searchBrowse = remember(services) { DesktopBrowseController(services.sites) }
    DisposableEffect(searchBrowse) { onDispose { searchBrowse.close() } }
    LaunchedEffect(searchBrowse) { searchBrowse.prepareSearch(browse.state.value.siteId) }
    var directRoomRequest by remember { mutableIntStateOf(0) }
    val browseState by browse.state.collectAsState()
    val selectedSite = browseState.siteId
    var roomEntrySite by remember { mutableStateOf(room.address?.siteId ?: selectedSite) }
    LaunchedEffect(selectedSite, room.address?.siteId) { roomEntrySite = room.address?.siteId ?: selectedSite }
    LaunchedEffect(roomFocusRequest) {
        if (roomFocusRequest > 0) fullArea = false
        if (roomFocusRequest > 0 && homePage == DesktopHomePage.Search && room.address == null) roomEntrySite = searchBrowse.state.value.siteId
    }
    var roomInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var accountDialog by remember { mutableStateOf(false) }
    var videoDialog by remember { mutableStateOf(false) }
    var dataDialog by remember { mutableStateOf(false) }
    var syncDialog by remember { mutableStateOf(false) }
    var aboutDialog by remember { mutableStateOf(false) }
    var playerDialog by remember { mutableStateOf(false) }
    var returnToMiniAfterSettings by remember { mutableStateOf(false) }
    fun openSettings() {
        returnToMiniAfterSettings = mini
        if (mini) onToggleMini()
        playerDialog = true
    }
    fun closeSettings() {
        playerDialog = false
        if (returnToMiniAfterSettings) {
            returnToMiniAfterSettings = false
            onToggleMini()
        }
    }
    LaunchedEffect(library.player) { playback.applyPreferences(library.player) }
    fun updatePlayer(transform: (DesktopPlayerPreferences) -> DesktopPlayerPreferences) {
        scope.launch { services.library.updatePlayer(transform) }
    }
    val fullscreen = windowState.placement == WindowPlacement.Fullscreen
    val inRoom = room.address != null || room.detail != null
    fun toggleFullArea() {
        val active = fullArea && !fullscreen && !mini
        if (fullscreen) onExitFullscreen()
        if (mini) onToggleMini()
        fullArea = !active
    }
    LaunchedEffect(inRoom) { if (!inRoom) fullArea = false }
    LaunchedEffect(browse, inRoom, homePage) {
        if (!inRoom && homePage == DesktopHomePage.Recommend && browse.state.value.page == 0 && !browse.state.value.loading) {
            browse.recommend(selectedSite)
        }
    }
    var editingRoomInput by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(inRoom, library.player.autoStopEnabled, library.player.autoStopMinutes) {
        if (inRoom && library.player.autoStopEnabled) {
            delay(library.player.autoStopMinutes * 60_000L)
            controller.leave(); onExitFullscreen()
        }
    }
    fun openRoom(): Boolean {
        val address = runCatching { parseRoomAddress(roomInput, roomEntrySite) }
            .getOrElse { inputError = it.message; return false }
        inputError = null; focusManager.clearFocus(); controller.open(address)
        return true
    }
    fun navigateTo(page: DesktopHomePage) {
        if (inRoom) controller.leave()
        homePage = page
        if (page == DesktopHomePage.Recommend && browseState.mode != BrowseMode.Recommend) browse.recommend(selectedSite)
        if (page == DesktopHomePage.Categories && browseState.mode !in listOf(BrowseMode.Categories, BrowseMode.CategoryRooms)) browse.categories(selectedSite)
    }
    val dialogOpen = accountDialog || videoDialog || dataDialog || syncDialog || aboutDialog || playerDialog
    Surface(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || dialogOpen || editingRoomInput) false
        else when {
            event.key == Key.F11 && !event.isCtrlPressed && !event.isAltPressed -> { onToggleFullscreen(); true }
            inRoom && event.key == Key.F10 && !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed -> { toggleFullArea(); true }
            event.key == Key.Escape && fullscreen -> { onExitFullscreen(); true }
            event.key == Key.Escape && fullArea -> { fullArea = false; true }
            !inRoom && (event.key == Key.F5 || (event.isCtrlPressed && event.key == Key.R)) -> {
                when (homePage) {
                    DesktopHomePage.Search -> if (searchBrowse.state.value.query.isNotBlank()) searchBrowse.refresh()
                    DesktopHomePage.Recommend, DesktopHomePage.Categories -> browse.refresh()
                    DesktopHomePage.Follows -> followRefresh.refresh()
                    DesktopHomePage.History -> Unit
                }
                true
            }
            else -> false
        }
    }.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed || !inRoom || dialogOpen) false
        else if (editingRoomInput) {
            if (event.key == Key.Escape) { focusManager.clearFocus(); true } else false
        } else when (event.key) {
            Key.Spacebar -> { if (playback.player.state.value.playWhenReady) playback.pause() else playback.resume(); true }
            Key.M -> { updatePlayer { it.copy(muted = !it.muted) }; true }
            Key.P -> { onToggleMini(); true }
            Key.T -> { updatePlayer { it.copy(alwaysOnTop = !it.alwaysOnTop) }; true }
            Key.DirectionUp -> { updatePlayer { it.copy(volume = (it.volume + 5f).coerceAtMost(100f)) }; true }
            Key.DirectionDown -> { updatePlayer { it.copy(volume = (it.volume - 5f).coerceAtLeast(0f)) }; true }
            else -> false
        }
    }, color = UiBg) {
        val pageContent: @Composable ColumnScope.() -> Unit = {
            if (!fullscreen && !mini && !fullArea) library.error?.let { error ->
                DesktopErrorBanner(error, "重试读取") { scope.launch { services.library.load() } }
            }
            if (inRoom) {
                RoomContent(room, playback, controller, fullscreen, onFullscreen = onToggleFullscreen,
                    mini = mini, onMini = onToggleMini,
                    fullArea = fullArea && !fullscreen && !mini, onFullArea = ::toggleFullArea,
                    preferences = library.player, onPreferences = ::updatePlayer,
                    danmakuPreferences = library.danmaku,
                    followed = room.address?.let { address -> library.follows.any { it.matches(address, room.detail) } } == true,
                    canFollow = library.loaded && library.writable && room.address != null && room.detail != null,
                    onFollow = {
                        val address = room.address
                        val detail = room.detail
                        if (address != null && detail != null) scope.launch { services.library.toggleFollow(address, detail) }
                    },
                    onBack = { controller.leave(); onExitFullscreen() }, onSettings = ::openSettings)
            } else savedPages.SaveableStateProvider(homePage.name) {
                if (homePage == DesktopHomePage.Search) {
                    BrowseContent(orderedSites, searchBrowse, searchPage = true,
                        onDirectRoom = { roomEntrySite = searchBrowse.state.value.siteId; directRoomRequest++ }, onRoom = controller::open)
                } else if (homePage in listOf(DesktopHomePage.Recommend, DesktopHomePage.Categories)) {
                    BrowseContent(orderedSites, browse, onRoom = controller::open)
                } else {
                    LibraryContent(services.library, library, orderedSites, followRefresh, homePage == DesktopHomePage.History,
                        onBrowse = { navigateTo(DesktopHomePage.Recommend) }, onRoom = controller::open)
                }
            }
        }
        if (inRoom && (fullscreen || mini || fullArea)) Column(Modifier.fillMaxSize(), content = pageContent)
        else DesktopShell(
            selectedPage = if (inRoom) null else homePage, follows = library.follows.size, history = library.history.size,
            roomInput = roomInput, inputError = inputError, platform = orderedSites.firstOrNull { it.id == roomEntrySite }?.name.orEmpty(),
            onRoomInput = { roomInput = it; inputError = null }, onOpenRoom = ::openRoom, onInputFocus = { editingRoomInput = it },
            onPage = ::navigateTo,
            actions = listOf(
                DesktopShellAction(Icons.Default.VideoLibrary, "打开视频") { videoDialog = true },
                DesktopShellAction(Icons.Default.AccountCircle, "账号设置") { accountDialog = true },
                DesktopShellAction(Icons.Default.ImportExport, "资料导入与导出") { dataDialog = true },
                DesktopShellAction(Icons.Default.Sync, "同步") { syncDialog = true },
                DesktopShellAction(Icons.Default.Settings, "设置") { openSettings() },
                DesktopShellAction(Icons.Default.Info, "关于与诊断") { aboutDialog = true }
            ), platforms = orderedSites.map { it.id to it.name }, onPlatform = { roomEntrySite = it },
            focusRequest = roomFocusRequest + directRoomRequest, content = pageContent)
    }
    if (accountDialog) DesktopAccountDialog(services, parentWindow, onApplied = { account ->
        val siteId = when (account) {
            LiveSiteAccount.BILIBILI -> "bilibili"
            LiveSiteAccount.DOUYU -> "douyu"
            LiveSiteAccount.DOUYIN -> "douyin"
        }
        controller.state.value.address?.takeIf { it.siteId == siteId }?.let(controller::open)
    }, onDismiss = { accountDialog = false })
    if (videoDialog) DesktopVideoDialog(parentWindow, onOpen = { controller.openVideo(it); videoDialog = false }, onDismiss = { videoDialog = false })
    if (dataDialog) RoomProfileDialog(services.library, library, parentWindow, onDismiss = { dataDialog = false })
    if (syncDialog) DesktopSyncDialog(services, library, onDismiss = { syncDialog = false })
    if (aboutDialog) DesktopAboutDialog(services, room, playback, parentWindow, onDismiss = { aboutDialog = false })
    if (playerDialog) DesktopSettingsDialog(services.library, library,
        onVolume = playback::setVolume, onDismiss = ::closeSettings)
}

}

@Composable
private fun ColumnScope.RoomContent(
    room: DesktopRoomState, playback: MpvDesktopPlayback, controller: DesktopRoomController,
    fullscreen: Boolean, followed: Boolean, canFollow: Boolean, onFollow: () -> Unit,
    mini: Boolean, onMini: () -> Unit,
    fullArea: Boolean, onFullArea: () -> Unit,
    preferences: DesktopPlayerPreferences, onPreferences: ((DesktopPlayerPreferences) -> DesktopPlayerPreferences) -> Unit,
    danmakuPreferences: DesktopDanmakuPreferences,
    onFullscreen: () -> Unit, onBack: () -> Unit, onSettings: () -> Unit
) {
    if (!fullscreen && !mini && !fullArea) Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        DesktopIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回直播列表", onClick = onBack)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(room.detail?.title ?: "正在打开直播间…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(buildString {
                append(room.detail?.userName.orEmpty())
                room.address?.let { append("  ·  ").append(desktopPlatformName(it.siteId)).append("  ·  房间 ").append(room.detail?.roomId ?: it.roomId) }
            }, color = UiMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp))
        }
        if (room.address != null) {
            if (followed) DesktopTonalButton(onFollow, enabled = canFollow, modifier = Modifier.padding(start = 12.dp)) {
                Icon(Icons.Default.Favorite, null, Modifier.size(17.dp))
                Text("已关注", Modifier.padding(start = 6.dp))
            } else DesktopOutlinedButton(onFollow, enabled = canFollow, modifier = Modifier.padding(start = 12.dp)) {
                Icon(Icons.Default.FavoriteBorder, null, Modifier.size(17.dp))
                Text("关注", Modifier.padding(start = 6.dp))
            }
        }
    }
    val playbackStatus by rememberDesktopPlayerStatus(playback)
    val nativeError = playbackStatus.error
    val busy = room.opening || playbackStatus.buffering || playbackStatus.opening
    var info by remember { mutableStateOf(false) }
    val logDiagnostics = remember { System.getProperty("mylive.playbackDiagnostics") == "true" }
    val diagnostics by rememberDesktopPlaybackDiagnostics(info || logDiagnostics) {
        playback.diagnostics().also {
            if (logDiagnostics) {
                println("[Playback] $it")
                println("[VideoUpload] ${playback.videoUploadStats}")
            }
        }
    }
    DesktopWatchLayout(fullscreen || mini, preferences.chatVisible, { onPreferences { it.copy(chatVisible = !it.chatVisible) } },
        Modifier.fillMaxWidth().weight(1f), chatAvailable = room.address != null, fullArea = fullArea, player = { chatVisible, toggleChat ->
            val accent = UiAccent
            val playerColorScheme = remember(accent) { desktopDarkColorScheme(accent) }
            MaterialTheme(
                colorScheme = playerColorScheme,
                typography = MaterialTheme.typography,
                shapes = MaterialTheme.shapes
            ) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(if (fullscreen || fullArea) 0.dp else 12.dp))
                    .background(Color.Black).then(if (fullscreen || fullArea) Modifier else Modifier.borderOutline())) {
                    DesktopVideoSurface(playback, Modifier.fillMaxSize(), preferences.scale)
                    if (preferences.danmakuEnabled) DanmakuOverlay(controller, danmakuPreferences, room.address?.siteId.orEmpty(), Modifier.fillMaxSize().padding(top = 24.dp, bottom = 72.dp))
                    DesktopPlayerChrome(playbackStatus.playing, busy || nativeError || room.error != null, fullscreen || mini || fullArea,
                        room.detail?.title.orEmpty(), onFullscreen, Modifier.fillMaxSize()) { modifier, held ->
                        PlayerControls(room, playback, controller, fullscreen, mini, onMini, fullArea, onFullArea, chatVisible, preferences,
                            onPreferences, onFullscreen, toggleChat, onSettings, info, diagnostics, { info = !info }, modifier, held, playbackStatus.playing)
                    }
                    if (busy && room.error == null && !nativeError) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                        Text(if (playbackStatus.buffering) "正在缓冲" else "正在连接直播…", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                    if (room.error != null || nativeError) Surface(Modifier.align(Alignment.Center).padding(24.dp), shape = RoundedCornerShape(12.dp), color = UiSurface.copy(alpha = .96f)) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, null, Modifier.size(28.dp), tint = UiMuted)
                            Text(room.error ?: "视频播放中断，请重试或切换线路", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                            room.address?.let { address -> TextButton(onClick = { controller.open(address) }) { Text("重新连接") } }
                        }
                    }
                }
            }
        }, chat = { modifier, close ->
            key(room.address) { ChatPanel(controller, room.chatStatus, preferences, danmakuPreferences, room.address?.siteId.orEmpty(),
                { enabled -> onPreferences { it.copy(chatAutoScroll = enabled) } }, modifier, close) }
        })
}
@Composable
private fun Modifier.borderOutline(): Modifier = this.then(
    Modifier.background(Color.Transparent).then(Modifier.border(1.dp, UiHairlineStrong, RoundedCornerShape(12.dp)))
)

@Composable
private fun PlayerControls(room: DesktopRoomState, playback: MpvDesktopPlayback, controller: DesktopRoomController,
    fullscreen: Boolean, mini: Boolean, onMini: () -> Unit, fullArea: Boolean, onFullArea: () -> Unit,
    chatVisible: Boolean, preferences: DesktopPlayerPreferences,
    onPreferences: ((DesktopPlayerPreferences) -> DesktopPlayerPreferences) -> Unit,
    onFullscreen: () -> Unit, onChat: () -> Unit, onSettings: () -> Unit, info: Boolean, diagnostics: PlaybackDiagnostics,
    onInfo: () -> Unit, modifier: Modifier, onMenuHold: (Boolean) -> Unit, playing: Boolean) {
    var volume by remember(preferences.volume) { mutableFloatStateOf(preferences.volume) }
    DesktopPlayerToolbar(DesktopPlayerToolbarState(playing, volume, preferences.muted, fullscreen, mini, chatVisible,
        preferences.danmakuEnabled, info, room.quality, room.qualities, room.line, room.lineCount, chatAvailable = room.address != null, fullArea = fullArea),
        DesktopPlayerToolbarActions(
            playPause = { if (playing) playback.pause() else playback.resume() },
            mute = { onPreferences { it.copy(muted = !it.muted) } },
            volume = { volume = it; playback.setVolume(it) }, saveVolume = { value -> onPreferences { it.copy(volume = value) } },
            quality = { controller.selectQuality(it) }, nextLine = controller::nextLine,
            chat = onChat, danmaku = { onPreferences { it.copy(danmakuEnabled = !it.danmakuEnabled) } }, info = onInfo,
            mini = onMini, fullscreen = onFullscreen, settings = onSettings, fullArea = onFullArea
        ), modifier, if (info) diagnostics.summary else null, onMenuHold)
}

private data class DesktopPlayerStatus(val playing: Boolean, val buffering: Boolean, val opening: Boolean, val error: Boolean)

@Composable
private fun rememberDesktopPlayerStatus(playback: MpvDesktopPlayback): State<DesktopPlayerStatus> {
    val status = remember(playback) {
        playback.player.state.map { DesktopPlayerStatus(it.playWhenReady, it.isBuffering,
            it.mediaStatus == MediaStatus.Opening, it.mediaStatus is MediaStatus.Error) }.distinctUntilChanged()
    }
    val current = playback.player.state.value
    return status.collectAsState(DesktopPlayerStatus(current.playWhenReady, current.isBuffering,
        current.mediaStatus == MediaStatus.Opening, current.mediaStatus is MediaStatus.Error))
}
@Composable
private fun ChatPanel(controller: DesktopRoomController, status: String, preferences: DesktopPlayerPreferences,
    filter: DesktopDanmakuPreferences, siteId: String, onAutoScroll: (Boolean) -> Unit, modifier: Modifier, onClose: () -> Unit) {
    val messages by controller.messages.collectAsState()
    val paid by controller.superChats.collectAsState()
    DesktopChatPanel(messages, paid, status, preferences, filter, siteId, onAutoScroll, modifier, onClose)
}
@Composable
private fun DanmakuOverlay(controller: DesktopRoomController, preferences: DesktopDanmakuPreferences, siteId: String, modifier: Modifier) {
    val messages by controller.messages.collectAsState()
    DesktopDanmakuOverlay(messages, preferences, siteId, modifier)
}
@Composable
internal fun DesktopVideoDialog(parentWindow: java.awt.Window?, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    var uri by remember { mutableStateOf("") }
    val valid = uri.trim().let { it.startsWith("https://") || it.startsWith("http://") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("打开视频") }, text = {
        Column(Modifier.width(460.dp)) {
            Text("输入 HTTP 视频链接，或选择本地文件。", color = UiMuted, fontSize = 12.sp)
            OutlinedTextField(uri, { uri = it }, singleLine = true, label = { Text("视频链接") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { if (valid) onOpen(uri.trim()) }))
            TextButton(onClick = {
                val chooser = JFileChooser()
                if (chooser.showOpenDialog(parentWindow) == JFileChooser.APPROVE_OPTION) onOpen(chooser.selectedFile.toURI().toString())
            }) { Text("选择本地文件") }
        }
    }, confirmButton = { TextButton(onClick = { onOpen(uri.trim()) }, enabled = valid) { Text("播放") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
