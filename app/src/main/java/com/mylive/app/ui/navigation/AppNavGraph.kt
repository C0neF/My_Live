package com.mylive.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.mylive.app.ui.screen.IndexScreen
import com.mylive.app.ui.screen.category.CategoryDetailScreen
import com.mylive.app.ui.screen.mine.ParseScreen
import com.mylive.app.ui.screen.other.HistoryScreen
import com.mylive.app.ui.screen.room.LiveRoomScreen
import com.mylive.app.ui.screen.search.SearchScreen
import com.mylive.app.ui.screen.settings.AccountScreen
import com.mylive.app.ui.screen.settings.AppearanceSettingsScreen
import com.mylive.app.ui.screen.settings.AutoExitSettingsScreen
import com.mylive.app.ui.screen.settings.DanmuSettingsScreen
import com.mylive.app.ui.screen.settings.OtherSettingsScreen
import com.mylive.app.ui.screen.settings.PlaySettingsScreen
import com.mylive.app.ui.screen.settings.SettingsScreen
import com.mylive.app.ui.screen.sync.ProfileBackupScreen
import com.mylive.app.ui.screen.sync.SyncHubScreen
import com.mylive.app.ui.screen.sync.WebDavSyncScreen
import com.mylive.app.ui.screen.settings.DouyinWebLoginScreen
import com.mylive.app.ui.screen.settings.ShieldSettingsScreen
import com.mylive.app.ui.screen.settings.FollowSettingsScreen
import com.mylive.app.ui.screen.settings.IndexedSettingsScreen
import com.mylive.app.ui.screen.settings.PlaybackPageSettingsScreen
import com.mylive.app.ui.screen.sync.LocalSyncScreen
import com.mylive.app.ui.screen.sync.RemoteSyncRoomScreen
import com.mylive.app.ui.screen.sync.SyncScanScreen
import com.mylive.app.ui.screen.sync.SyncDeviceScreen
import com.mylive.app.ui.screen.other.DebugLogScreen

@Composable
fun AppNavGraph(navigator: Navigator, initialRoute: String? = null) {
    val backStack = navigator.backStack

    BackHandler(enabled = backStack.size > 1) {
        navigator.goBack()
    }

    // IndexScreen is always present at the bottom layer, never removed from composition
    Box(modifier = Modifier.fillMaxSize()) {
        IndexScreen(navigator = navigator)

        // NavDisplay is always present. When backStack is [Index], it renders
        // a transparent Box, so IndexScreen underneath is fully visible.
        NavDisplay(
            backStack = backStack,
            transitionSpec = CupertinoTransition.transitionSpec,
            popTransitionSpec = CupertinoTransition.popTransitionSpec,
            entryProvider = entryProvider {
                entry<Route.Index> {
                    // Transparent full-size placeholder; IndexScreen is rendered below.
                    Box(modifier = Modifier.fillMaxSize())
                }
                entry<Route.CategoryDetail> { key ->
                    CategoryDetailScreen(navigator = navigator, key = key)
                }
                entry<Route.Search> {
                    SearchScreen(navigator = navigator)
                }
                entry<Route.LiveRoomDetail> { key ->
                    LiveRoomScreen(navigator = navigator, key = key)
                }
                entry<Route.History> {
                    HistoryScreen(navigator = navigator)
                }
                entry<Route.Settings> {
                    SettingsScreen(navigator = navigator)
                }
                entry<Route.SettingsDanmu> {
                    DanmuSettingsScreen(navigator = navigator)
                }
                entry<Route.SettingsDanmuShield> {
                    ShieldSettingsScreen(navigator = navigator)
                }
                entry<Route.SettingsPlay> {
                    PlaySettingsScreen(navigator = navigator)
                }
                entry<Route.SettingsAppStyle> {
                    AppearanceSettingsScreen(navigator = navigator)
                }
                entry<Route.SettingsAutoExit> {
                    AutoExitSettingsScreen(navigator = navigator)
                }
                entry<Route.SettingsOther> {
                    OtherSettingsScreen(navigator = navigator)
                }
                entry<Route.SettingsAccount> {
                    AccountScreen(navigator = navigator)
                }
                entry<Route.SettingsAccountLoginWebview> {
                    com.mylive.app.ui.screen.settings.LoginWebViewScreen(navigator = navigator)
                }
                entry<Route.SettingsAccountLoginQr> {
                    com.mylive.app.ui.screen.settings.BiliBiliQRLoginScreen(navigator = navigator)
                }
                entry<Route.SettingsAccountDouyinLoginWebview> {
                    DouyinWebLoginScreen(navigator = navigator)
                }
                entry<Route.Sync> {
                    SyncHubScreen(navigator = navigator)
                }
                entry<Route.RemoteSyncWebDav> {
                    WebDavSyncScreen(navigator = navigator)
                }
                entry<Route.ProfileBackup> {
                    ProfileBackupScreen(navigator = navigator)
                }
                entry<Route.Tools> {
                    ParseScreen(navigator = navigator)
                }
                entry<Route.SettingsFollow> {
                    FollowSettingsScreen(navigator = navigator)
                }
                entry<Route.SettingsIndexed> {
                    IndexedSettingsScreen(navigator = navigator)
                }
                entry<Route.SettingsPlaybackPage> {
                    PlaybackPageSettingsScreen(navigator = navigator)
                }
                entry<Route.LocalSync> {
                    LocalSyncScreen(navigator = navigator)
                }
                entry<Route.RemoteSyncRoom> { key ->
                    RemoteSyncRoomScreen(navigator = navigator, roomIdParam = key.roomId)
                }
                entry<Route.DebugLog> {
                    DebugLogScreen(navigator = navigator)
                }
                entry<Route.SyncScan> {
                    SyncScanScreen(navigator = navigator)
                }
                entry<Route.SyncDevice> { key ->
                    SyncDeviceScreen(navigator = navigator, key = key)
                }
            }
        )
    }

    LaunchedEffect(initialRoute) {
        val routeStr = initialRoute ?: return@LaunchedEffect
        val parsed = parseRoute(routeStr)
        if (parsed != null) {
            navigator.navigate(parsed)
        }
    }
}

private fun parseRoute(routeStr: String): Route? {
    if (routeStr == "/index" || routeStr == "index" || routeStr == "/") return Route.Index
    if (routeStr == "/search") return Route.Search
    if (routeStr.startsWith("/room/detail/")) {
        val parts = routeStr.removePrefix("/room/detail/").split("?")
        val roomId = parts.firstOrNull() ?: return null
        var siteId = ""
        if (parts.size > 1) {
            val queryParams = parts[1].split("&")
            for (param in queryParams) {
                val kv = param.split("=")
                if (kv.size == 2 && kv[0] == "siteId") {
                    siteId = kv[1]
                }
            }
        }
        return Route.LiveRoomDetail(roomId = roomId, siteId = siteId)
    }
    if (routeStr.startsWith("/remote_sync/room")) {
        val parts = routeStr.removePrefix("/remote_sync/room").split("?")
        val roomPart = parts.firstOrNull() ?: ""
        val remaining = if (roomPart.startsWith("/")) roomPart.removePrefix("/") else roomPart
        var roomId = remaining.split("?").firstOrNull() ?: ""
        if (parts.size > 1) {
            val queryParams = parts[1].split("&")
            for (param in queryParams) {
                val kv = param.split("=")
                if (kv.size == 2 && kv[0] == "roomId") {
                    roomId = kv[1]
                }
            }
        }
        return Route.RemoteSyncRoom(roomId = roomId)
    }
    return null
}
