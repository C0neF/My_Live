package com.mylive.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import com.mylive.app.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.imageio.ImageIO

@OptIn(ExperimentalTestApi::class)
class DesktopUiTestShell {
    @get:Rule val temporary = TemporaryFolder()
    private val sites = listOf("douyu" to "斗鱼", "bilibili" to "哔哩哔哩", "huya" to "虎牙", "douyin" to "抖音").map { (id, label) ->
        object : StubLiveSite(id) {
            override val name = label
            override suspend fun getRecommendRooms(page: Int) = LiveCategoryResult(false, rooms)
            override suspend fun searchRooms(keyword: String, page: Int) = LiveSearchRoomResult(false, rooms)
        }
    }
    private val actions get() = listOf(
        DesktopShellAction(Icons.Default.VideoLibrary, "打开视频") {}, DesktopShellAction(Icons.Default.AccountCircle, "账号设置") {},
        DesktopShellAction(Icons.Default.ImportExport, "资料导入与导出") {}, DesktopShellAction(Icons.Default.Sync, "同步") {},
        DesktopShellAction(Icons.Default.Settings, "设置") {}, DesktopShellAction(Icons.Default.Info, "关于与诊断") {}
    )
    private val rooms get() = List(18) { index ->
        val titles = listOf("今晚一起排位 · 新赛季上分", "音乐现场 | 再唱一首", "城市漫游，走走停停", "周末的游戏时光", "聊聊今天的新鲜事", "慢下来，一起看风景")
        LiveRoomItem("room-$index", "${titles[index % titles.size]} ${index + 1}", "", listOf("晚风", "小耳朵", "旅途", "北方", "半夏", "行走的人")[index % 6], online = 12_500 + index * 8_170)
    }
    private fun capture(rule: ComposeUiTest, name: String) {
        val directory = File(System.getProperty("mylive.uiEvidenceDir")).apply { mkdirs() }
        ImageIO.write(rule.onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    @Test fun compactShellKeepsNavigationToolsAndRoomEntryAccessible() = runSkikoComposeUiTest(size = Size(1600f, 1000f)) {
        val rule = this
        var page by mutableStateOf(DesktopHomePage.Recommend)
        var input by mutableStateOf("douyu:9999")
        var opens = 0
        rule.setContent {
            DesktopTheme(DesktopAppearancePreferences()) { Box(Modifier.size(900.dp, 600.dp)) {
                DesktopShell(page, 12, 28, input, null, "斗鱼", { input = it }, { opens++; true }, {}, { page = it }, actions) { Text("内容区域") }
            } }
        }
        rule.onNodeWithTag("room-input").assertDoesNotExist()
        rule.onNodeWithTag("nav-Search").assertIsDisplayed().performClick()
        assertEquals(DesktopHomePage.Search, page)
        rule.onNodeWithText("历史").assertIsDisplayed().performClick()
        assertEquals(DesktopHomePage.History, page)
        rule.onNodeWithText("历史").assertIsSelected()
        actions.takeLast(2).forEach { rule.onNodeWithContentDescription(it.label).assertIsDisplayed() }
        rule.onNodeWithContentDescription("更多工具").performClick()
        actions.dropLast(2).forEach { rule.onNodeWithText(it.label).assertIsDisplayed() }
        rule.onNodeWithText(actions.first().label).performClick()
        rule.onNodeWithText("历史").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        rule.onNodeWithText("历史").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.L); keyUp(Key.CtrlLeft) }
        rule.onNodeWithTag("room-input").assertIsFocused()
        rule.onNodeWithTag("room-input").performKeyInput { pressKey(Key.Enter) }
        assertEquals(1, opens)
        rule.onNodeWithTag("room-input").assertDoesNotExist()
        capture(rule, "shell-compact")
    }

    @Test fun browseFitsWideDarkAndCompactLightWindows() = runSkikoComposeUiTest(size = Size(1600f, 1000f)) {
        val rule = this
        val controller = DesktopBrowseController(sites, io = Dispatchers.Unconfined)
        var light by mutableStateOf(false)
        var width by mutableStateOf(1280)
        var height by mutableStateOf(820)
        try {
            controller.recommend("douyu")
            rule.setContent {
                DesktopTheme(DesktopAppearancePreferences(theme = if (light) 1 else 2)) { Surface { Box(Modifier.size(width.dp, height.dp)) {
                    DesktopShell(DesktopHomePage.Recommend, 12, 28, "", null, "斗鱼", {}, { true }, {}, {}, actions,
                        platforms = sites.map { it.id to it.name }) { BrowseContent(sites, controller) {} }
                } } }
            }
            rule.waitUntil(timeoutMillis = 5000) { !controller.state.value.loading }
            rule.onNodeWithTag("desktop-shell").assertWidthIsEqualTo(1280.dp)
            rule.onNodeWithText("正在直播").assertDoesNotExist()
            rule.onNodeWithTag("browse-search").assertDoesNotExist()
            rule.onNodeWithTag("room-input").assertDoesNotExist()
            capture(rule, "browse-dark-1280")
            rule.runOnIdle { width = 1440; height = 900 }
            rule.onNodeWithTag("desktop-shell").assertWidthIsEqualTo(1440.dp)
            rule.onNodeWithText("资料导入与导出").assertIsDisplayed()
            capture(rule, "browse-dark-1440")
            rule.runOnIdle { light = true; width = 900; height = 600 }
            rule.onNodeWithTag("browse-search").assertDoesNotExist()
            rule.onNodeWithTag("nav-Search").assertIsDisplayed()
            rule.onNodeWithText("晚风").assertIsDisplayed()
            capture(rule, "browse-light-900")
        } finally { controller.close() }
    }

    @Test fun roomEntryPlatformCanBeSelectedAndExternalFocusRequestsWork() = runSkikoComposeUiTest(size = Size(1600f, 1000f)) {
        val rule = this
        var platform by mutableStateOf("douyu")
        var focusRequest by mutableIntStateOf(1)
        var opened = 0
        rule.setContent { DesktopTheme(DesktopAppearancePreferences()) { Box(Modifier.size(900.dp, 600.dp)) {
            DesktopShell(DesktopHomePage.Recommend, 0, 0, "123", null, desktopPlatformName(platform), {}, { opened++; true }, {}, {}, actions,
                platforms = sites.map { it.id to it.name }, onPlatform = { platform = it }, focusRequest = focusRequest) { Text("内容") }
        } } }
        rule.onNodeWithContentDescription("选择房间号所属平台").performClick()
        rule.onNodeWithText("哔哩哔哩").performClick()
        assertEquals("bilibili", platform)
        assertEquals(0, opened)
        rule.runOnIdle { focusRequest++ }
        rule.onNodeWithTag("room-input").assertIsFocused()
    }

    @Test fun videoLinkDialogAcceptsPastedWhitespaceAndSubmitsWithEnter() = runSkikoComposeUiTest(size = Size(1600f, 1000f)) {
        val rule = this
        var opened: String? = null
        rule.setContent { DesktopTheme(DesktopAppearancePreferences()) { DesktopVideoDialog(null, { opened = it }, {}) } }
        rule.onNode(hasSetTextAction()).performTextInput("  https://example.test/sample.mp4  ")
        rule.onNodeWithText("播放", substring = false).assertIsEnabled()
        rule.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        rule.runOnIdle { assertEquals("https://example.test/sample.mp4", opened) }
    }

    @Test fun anInvalidDirectRoomKeepsTheDialogAndItsErrorVisible() = runSkikoComposeUiTest(size = Size(1600f, 1000f)) {
        val rule = this
        var error by mutableStateOf<String?>(null)
        rule.setContent { DesktopTheme(DesktopAppearancePreferences()) { Box(Modifier.size(900.dp, 600.dp)) {
            DesktopShell(DesktopHomePage.Search, 0, 0, "错误的房间", error, "斗鱼", {},
                { error = "请输入有效的直播间地址"; false }, {}, {}, actions, focusRequest = 1) { Text("搜索内容") }
        } } }
        rule.onNodeWithTag("room-input").performKeyInput { pressKey(Key.Enter) }
        rule.onNodeWithTag("room-input").assertIsDisplayed()
        rule.onNodeWithText("请输入有效的直播间地址").assertIsDisplayed()
        rule.onNodeWithText("取消").performClick()
        rule.onNodeWithTag("room-input").assertDoesNotExist()
    }

    @Test fun sidebarSearchKeepsTheHomeGridAndSearchResultsIndependent() = runSkikoComposeUiTest(size = Size(1600f, 1000f)) {
        val rule = this
        val home = DesktopBrowseController(sites, io = Dispatchers.Unconfined)
        val search = DesktopBrowseController(sites, io = Dispatchers.Unconfined)
        var page by mutableStateOf(DesktopHomePage.Recommend)
        var directRequest by mutableIntStateOf(0)
        try {
            home.recommend("douyu"); search.prepareSearch("douyu")
            rule.setContent { DesktopTheme(DesktopAppearancePreferences()) { Box(Modifier.size(1280.dp, 820.dp)) {
                DesktopShell(page, 0, 0, "", null, "斗鱼", {}, { true }, {}, { page = it }, actions, focusRequest = directRequest) {
                    if (page == DesktopHomePage.Search) BrowseContent(sites, search, searchPage = true, onDirectRoom = { directRequest++ }) {}
                    else BrowseContent(sites, home) {}
                }
            } } }
            rule.onNodeWithTag("browse-search").assertDoesNotExist()
            rule.onNodeWithTag("nav-Search").performClick()
            rule.onNodeWithTag("browse-search").performTextInput("音乐")
            rule.onNodeWithTag("browse-search").performKeyInput { pressKey(Key.Enter) }
            rule.waitUntil(timeoutMillis = 5000) { search.state.value.query == "音乐" && !search.state.value.loading }
            rule.onNodeWithText("房间号 / 链接").performClick()
            rule.onNodeWithTag("room-input").assertIsFocused()
            rule.onNodeWithText("取消").performClick()
            rule.onNodeWithTag("nav-Recommend").performClick()
            rule.onNodeWithTag("browse-search").assertDoesNotExist()
            assertEquals(BrowseMode.Recommend, home.state.value.mode)
            rule.onNodeWithTag("nav-Search").performClick()
            rule.onNodeWithTag("browse-search").assertTextContains("音乐")
            capture(rule, "sidebar-search")
        } finally { home.close(); search.close() }
    }

    @Test fun returningToAPlatformRestoresItsGridPositionWithoutFetchingAgain() = runSkikoComposeUiTest(size = Size(1600f, 1000f)) {
        val rule = this
        val requests = mutableListOf<String>()
        val sources = listOf("douyu", "huya").map { id -> object : StubLiveSite(id) {
            override suspend fun getRecommendRooms(page: Int): LiveCategoryResult {
                requests += id
                return LiveCategoryResult(false, (1..40).map { LiveRoomItem("$it", "$id room $it", "", "anchor $it") })
            }
        } }
        val controller = DesktopBrowseController(sources, io = Dispatchers.Unconfined)
        try {
            controller.recommend("douyu")
            rule.setContent { DesktopTheme(DesktopAppearancePreferences()) { Surface {
                Column(Modifier.size(1000.dp, 760.dp)) { BrowseContent(sources, controller) {} }
            } } }
            rule.waitUntil(timeoutMillis = 5000) { !controller.state.value.loading }
            rule.onNodeWithTag("room-grid").performScrollToIndex(12)
            rule.onNodeWithText("douyu room 13").assertIsDisplayed()
            rule.onNodeWithText("huya").performClick()
            rule.waitUntil(timeoutMillis = 5000) { controller.state.value.siteId == "huya" && !controller.state.value.loading }
            rule.onNodeWithText("douyu").performClick()
            rule.onNodeWithText("douyu room 13").assertIsDisplayed()
            assertEquals(listOf("douyu", "huya"), requests)
            capture(rule, "platform-return-restored")
        } finally { controller.close() }
    }

    @Test fun returningToBrowseRestoresSearchAndThePreviousScrollPosition() = runSkikoComposeUiTest(size = Size(1600f, 1000f)) {
        val rule = this
        val controller = DesktopBrowseController(sites, io = Dispatchers.Unconfined)
        var inRoom by mutableStateOf(false)
        try {
            controller.prepareSearch("douyu")
            rule.setContent {
                val saved = rememberSaveableStateHolder()
                DesktopTheme(DesktopAppearancePreferences()) { Surface { Column(Modifier.size(1000.dp, 760.dp)) {
                    if (inRoom) Button({ inRoom = false }) { Text("返回列表") }
                    else saved.SaveableStateProvider("browse") { BrowseContent(sites, controller, searchPage = true) { inRoom = true } }
                } } }
            }
            rule.waitUntil(timeoutMillis = 5000) { !controller.state.value.loading }
            rule.onNodeWithTag("browse-search").performTextInput("音乐")
            rule.onNodeWithTag("browse-search").performKeyInput { pressKey(Key.Enter) }
            rule.waitUntil(timeoutMillis = 5000) { controller.state.value.query == "音乐" && !controller.state.value.loading }
            rule.onNodeWithTag("room-grid").performScrollToIndex(12)
            val title = rooms[12].title
            rule.onNodeWithText(title).assertIsDisplayed().performClick()
            rule.onNodeWithText("返回列表").performClick()
            rule.onNodeWithTag("browse-search").assertTextContains("音乐")
            rule.onNodeWithText(title).assertIsDisplayed()
        } finally { controller.close() }
    }

    @Test fun emptyFilteredFollowsCanBeRecoveredWithoutDeletingSavedData() = runSkikoComposeUiTest(size = Size(1600f, 1000f)) {
        val rule = this
        val library = DesktopLibrary(temporary.root.toPath().resolve("library.json"))
        runBlocking { library.importRooms(RoomProfileImport(listOf(SavedRoom("douyu", "1", "本地主播", "直播标题", savedAt = 1)), emptyList())) }
        val refresh = DesktopFollowController(library, sites)
        try {
            rule.setContent {
                val state by library.state.collectAsState()
                DesktopTheme(state.appearance) { Surface { Column(Modifier.size(900.dp, 600.dp)) { LibraryContent(library, state, sites, refresh, false) {} } } }
            }
            rule.onNodeWithText("只看开播").performClick()
            rule.onNodeWithText("没有匹配的记录。").assertIsDisplayed()
            rule.onNodeWithText("清除筛选").performClick()
            rule.onNodeWithText("直播标题").assertIsDisplayed()
            assertEquals(1, library.state.value.follows.size)
        } finally { refresh.close() }
    }
}
