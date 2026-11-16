package com.mylive.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.mylive.app.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopUiTest {
    @get:Rule val rule = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()
    private fun library() = DesktopLibrary(temporary.root.toPath().resolve("library.json"))
    private fun capture(name: String) {
        val directory = java.io.File(System.getProperty("mylive.uiEvidenceDir")).apply { mkdirs() }
        javax.imageio.ImageIO.write(rule.onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", java.io.File(directory, "$name.png"))
    }

    @Test fun aScrollingLaneAcceptsTheNextMessageOnceThePreviousTailHasClearedTheEntrance() {
        rule.mainClock.autoAdvance = false
        fun entry(id: Long, text: String) = ChatEntry(id, LiveMessage(LiveMessageType.CHAT, "user", text))
        var messages by mutableStateOf(listOf(entry(1, "first")))
        rule.setContent {
            Box(Modifier.size(500.dp, 240.dp)) {
                DesktopDanmakuOverlay(messages, DesktopDanmakuPreferences(lines = 1, area = 1f, travelSeconds = 4f), "douyu", Modifier.fillMaxSize())
            }
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("first").assertExists()
        rule.runOnIdle { messages = messages + entry(2, "second") }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("first").assertExists()
        rule.onNodeWithText("second").assertExists()
        val firstBounds = rule.onNodeWithText("first").fetchSemanticsNode().boundsInRoot
        val secondBounds = rule.onNodeWithText("second").fetchSemanticsNode().boundsInRoot
        assertEquals(firstBounds.top, secondBounds.top, .1f)
        assertTrue("The new message must remain behind the previous tail", secondBounds.left > firstBounds.right)
        capture("danmaku-same-lane")
    }

    @Test fun diagnosticsStopPollingWhenHiddenAndResumeWithTheCurrentReader() {
        rule.mainClock.autoAdvance = false
        var enabled by mutableStateOf(false)
        var reads = 0
        rule.setContent {
            val diagnostics by rememberDesktopPlaybackDiagnostics(enabled) { reads++; PlaybackDiagnostics(width = reads) }
            androidx.compose.material3.Text("samples ${diagnostics.width}")
        }
        rule.mainClock.advanceTimeBy(5_000)
        rule.runOnIdle { assertEquals(0, reads); enabled = true }
        rule.mainClock.advanceTimeBy(2_100)
        rule.runOnIdle { assertEquals(3, reads); enabled = false }
        rule.mainClock.advanceTimeBy(5_000)
        rule.runOnIdle { assertEquals(3, reads); enabled = true }
        rule.mainClock.advanceTimeBy(100)
        rule.runOnIdle { assertEquals(4, reads) }
    }

    @Test fun aLongScrollingMessageEntersFromTheRightWithoutJumpingOverItsPredecessor() {
        rule.mainClock.autoAdvance = false
        val longText = "LONG MESSAGE ".repeat(12)
        var messages by mutableStateOf(listOf(ChatEntry(1, LiveMessage(LiveMessageType.CHAT, "user", "first"))))
        rule.setContent {
            Box(Modifier.size(500.dp, 240.dp)) {
                DesktopDanmakuOverlay(messages, DesktopDanmakuPreferences(lines = 1, area = 1f, travelSeconds = 4f), "douyu", Modifier.fillMaxSize())
            }
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnIdle { messages = messages + ChatEntry(2, LiveMessage(LiveMessageType.CHAT, "user", longText)) }
        rule.mainClock.advanceTimeBy(100)
        val leadingEdge = rule.onNodeWithText(longText).fetchSemanticsNode().boundsInRoot.left
        assertTrue("Long text must begin at the right edge, actual=$leadingEdge", leadingEdge > 400f)
        rule.mainClock.advanceTimeBy(500)
        val laterEdge = rule.onNodeWithText(longText).fetchSemanticsNode().boundsInRoot.left
        assertEquals(62.5f, leadingEdge - laterEdge, 5f)
    }

    @Test fun aBlankMessageExpiresAndDoesNotReserveItsLaneForever() {
        rule.mainClock.autoAdvance = false
        var messages by mutableStateOf(listOf(ChatEntry(1, LiveMessage(LiveMessageType.CHAT, "user", ""))))
        rule.setContent {
            Box(Modifier.size(500.dp, 240.dp)) {
                DesktopDanmakuOverlay(messages, DesktopDanmakuPreferences(lines = 1, travelSeconds = 3f), "douyu", Modifier.fillMaxSize())
            }
        }
        rule.mainClock.advanceTimeBy(3_200)
        rule.runOnIdle { messages = messages + ChatEntry(2, LiveMessage(LiveMessageType.CHAT, "user", "after blank")) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("after blank").assertExists()
    }

    @Test fun aBusyOverlayKeepsMultipleMessagesPerLaneWithinABoundedVisibleSet() {
        rule.mainClock.autoAdvance = false
        var messages by mutableStateOf(emptyList<ChatEntry>())
        var nextId = 0L
        rule.setContent {
            Box(Modifier.size(500.dp, 600.dp)) {
                DesktopDanmakuOverlay(messages, DesktopDanmakuPreferences(lines = 20, area = 1f, travelSeconds = 4f), "douyu", Modifier.fillMaxSize())
            }
        }
        var peak = 0
        repeat(50) {
            rule.runOnIdle {
                messages = (messages + List(20) { ChatEntry(++nextId, LiveMessage(LiveMessageType.CHAT, "user", "x")) }).takeLast(200)
            }
            rule.mainClock.advanceTimeBy(250)
            val visible = rule.onAllNodesWithText("x").fetchSemanticsNodes().size
            peak = maxOf(peak, visible)
            assertTrue("Visible messages must remain bounded", visible <= 120)
        }
        assertTrue("More than one message per lane should be visible", peak > 20)
        println("[Danmaku workload] 1000 messages / 12.5 virtual seconds; peak visible=$peak; bound=120")
        rule.runOnIdle { messages = emptyList() }
        rule.mainClock.advanceTimeBy(100)
        rule.onAllNodesWithText("x").assertCountEquals(0)
    }

    @Test fun aFixedMessageKeepsItsLaneUntilItExpires() {
        rule.mainClock.autoAdvance = false
        val first = ChatEntry(1, LiveMessage(LiveMessageType.CHAT, "user", "fixed", danmakuPosition = LiveMessageDanmakuPosition.TOP))
        var messages by mutableStateOf(listOf(first))
        rule.setContent {
            Box(Modifier.size(500.dp, 240.dp)) {
                DesktopDanmakuOverlay(messages, DesktopDanmakuPreferences(lines = 1, travelSeconds = 3f), "douyu", Modifier.fillMaxSize())
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        val start = rule.onNodeWithText("fixed").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { messages = messages + ChatEntry(2, LiveMessage(LiveMessageType.CHAT, "user", "scroll")) }
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(start, rule.onNodeWithText("fixed").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithText("scroll").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(1_100)
        rule.onNodeWithText("fixed").assertDoesNotExist()
    }

    @Test fun categoriesSearchPaginationAndOpeningAreConnectedToTheActualUi() {
        val site = object : StubLiveSite() {
            override suspend fun getCategories() = listOf(LiveCategory("其他分区", "other"),
                LiveCategory("游戏分区", "parent", listOf(LiveSubCategory("测试分类", "category", "parent"))))
            override suspend fun getCategoryRooms(category: LiveSubCategory, page: Int) =
                LiveCategoryResult(page == 1, listOf(LiveRoomItem("$page", "分类直播$page", "", "主播$page")))
            override suspend fun searchAnchors(keyword: String, page: Int) = LiveSearchAnchorResult(false, listOf(LiveAnchorItem("anchor", userName = "搜索主播", liveStatus = true)))
        }
        val controller = DesktopBrowseController(listOf(site), io = Dispatchers.Unconfined)
        var opened: RoomAddress? = null
        var searchPage by mutableStateOf(false)
        try {
            controller.categories(site.id)
            rule.setContent { DesktopTheme(DesktopAppearancePreferences()) { androidx.compose.material3.Surface { Column(Modifier.size(1000.dp, 760.dp)) { BrowseContent(listOf(site), controller, searchPage = searchPage) { opened = it } } } } }
            rule.waitUntil(5000) { !controller.state.value.loading }
            rule.onNodeWithText("游戏分区").performClick()
            rule.onNodeWithText("测试分类").performClick()
            rule.waitUntil(5000) { controller.state.value.rooms.isNotEmpty() }
            rule.onNodeWithText("分类直播1").assertExists()
            capture("category-rooms")
            rule.waitUntil(5000) { controller.state.value.rooms.size == 2 }
            rule.onNodeWithContentDescription("返回分类").performClick()
            rule.onNodeWithText("测试分类").assertIsDisplayed().performClick()
            rule.waitUntil(5000) { controller.state.value.rooms.size == 2 }
            rule.onNodeWithText("分类直播2").performClick()
            assertEquals(RoomAddress("douyu", "2"), opened)
            rule.runOnIdle { searchPage = true; controller.prepareSearch(site.id) }
            rule.onNodeWithText("主播").performClick()
            rule.onNode(hasSetTextAction()).performTextInput("测试")
            rule.onNodeWithTag("submit-search").performClick()
            rule.waitUntil(5000) { controller.state.value.mode == BrowseMode.Anchors && !controller.state.value.loading }
            rule.onNodeWithText("搜索主播").performClick()
            assertEquals(RoomAddress("douyu", "anchor"), opened)
        } finally { controller.close() }
    }

    @Test fun followEditorSavesGroupsAndNotesAndCancelKeepsTheFollow() {
        val repository = library()
        runBlocking { repository.importRooms(RoomProfileImport(listOf(SavedRoom("douyu", "1", "本地主播", "直播标题", savedAt = 1)), emptyList())) }
        val refresh = DesktopFollowController(repository, listOf(StubLiveSite()))
        try {
            rule.setContent {
                val state by repository.state.collectAsState()
                DesktopTheme(state.appearance) { androidx.compose.material3.Surface { Column(Modifier.size(1000.dp, 760.dp)) { LibraryContent(repository, state, listOf(StubLiveSite()), refresh, false) {} } } }
            }
            rule.onNodeWithContentDescription("分组、置顶与备注").performClick()
            rule.onNodeWithText("分组（每行一个）").performTextInput("常看\n游戏")
            rule.onNodeWithText("备注").performTextInput("测试备注")
            rule.onNodeWithText("保存").performClick()
            rule.waitUntil(5000) { repository.state.value.follows.single().note == "测试备注" }
            assertEquals(listOf("常看", "游戏"), repository.state.value.follows.single().tags)
            capture("follows")
            rule.onNodeWithContentDescription("取消关注").performClick()
            rule.onNodeWithText("保留关注").performClick()
            assertEquals(1, repository.state.value.follows.size)
            rule.onNodeWithText("只看开播").performClick()
            rule.onNodeWithText("没有匹配的记录。").assertExists()
        } finally { refresh.close() }
    }

    @Test fun appearanceSettingsPersistAndShieldInputsAreEditable() {
        val repository = library(); runBlocking { repository.load() }
        rule.setContent {
            val state by repository.state.collectAsState()
            DesktopTheme(state.appearance) { DesktopSettingsDialog(repository, state, {}, {}) }
        }
        capture("settings-playback-dark")
        rule.onNodeWithText("弹幕").performClick()
        capture("settings-danmaku-dark")
        rule.onNodeWithText("屏蔽").performClick()
        capture("settings-shield-dark")
        rule.onNodeWithText("外观").performClick()
        capture("settings-appearance-dark")
        rule.onNodeWithText("快捷键").performClick()
        capture("settings-shortcuts-dark")
        rule.onNodeWithText("外观").performClick()
        rule.onNodeWithText("浅色").performClick()
        rule.waitUntil(5000) { repository.state.value.appearance.theme == 1 }
        rule.onNodeWithTag("appearance-text-scale-110").performScrollTo().performClick()
        rule.waitUntil(5000) { repository.state.value.appearance.textScalePercent == 110 }
        rule.onAllNodesWithText("下移").onFirst().performScrollTo().performClick()
        rule.waitUntil(5000) { repository.state.value.appearance.platformOrder.first() == "douyu" }
        capture("appearance-light")
        rule.onNodeWithText("屏蔽").performClick()
        rule.onNodeWithText("关键词（每行一个）").performTextInput("广告")
        rule.onNodeWithText("保存关键词").performClick()
        rule.waitUntil(5000) { repository.state.value.danmaku.keywords == listOf("广告") }
    }
}
