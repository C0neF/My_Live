package com.mylive.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPlacement
import com.mylive.app.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

@OptIn(ExperimentalTestApi::class)
class DesktopUiTestPlayer {
    private val high = LivePlayQuality("2K120", PlayQualityData.Douyin(emptyList()))
    private val low = high.copy(quality = "1080P60")
    private fun actions(fullscreen: () -> Unit = {}, settings: () -> Unit = {}, quality: (LivePlayQuality) -> Unit = {},
        nextLine: () -> Unit = {}, mute: () -> Unit = {}, fullArea: () -> Unit = {}) = DesktopPlayerToolbarActions({}, mute, {}, {}, quality, nextLine, {}, {}, {}, {}, fullscreen, settings, fullArea)
    private fun capture(rule: ComposeUiTest, name: String) {
        val directory = File(System.getProperty("mylive.uiEvidenceDir")).apply { mkdirs() }
        ImageIO.write(rule.onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    @Test fun fullAreaFillsTheWindowAndHoverChatDoesNotResizeTheVideo() = runSkikoComposeUiTest(size = Size(1200f, 800f)) {
        val window = WindowState(size = DpSize(1200.dp, 800.dp))
        val nativeModes = DesktopWindowMode(window)
        var fullArea by mutableStateOf(false)
        setContent {
            val fullscreen = window.placement == WindowPlacement.Fullscreen
            DesktopTheme(DesktopAppearancePreferences()) {
                DesktopWatchLayout(fullscreen, true, {}, Modifier.fillMaxSize(), fullArea = fullArea && !fullscreen,
                    player = { _, _ ->
                        Box(Modifier.fillMaxSize().background(Color(0xff101827))) {
                            Text("视频画面")
                            Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                                DesktopPlayerToolbar(DesktopPlayerToolbarState(fullscreen = fullscreen, fullArea = fullArea && !fullscreen),
                                    actions(fullscreen = nativeModes::toggle, fullArea = { fullArea = !fullArea }))
                            }
                        }
                    }, chat = { modifier, close ->
                        DesktopChatPanel(listOf(ChatEntry(1, LiveMessage(LiveMessageType.CHAT, "观众", "全区域聊天已展开"))),
                            emptyList(), "已连接", DesktopPlayerPreferences(), DesktopDanmakuPreferences(), "douyu", {}, modifier, close)
                    })
            }
        }
        onRoot().performMouseInput { moveTo(Offset(400f, 300f)) }
        onNodeWithTag("watch-video").assertWidthIsEqualTo(880.dp)
        onNodeWithContentDescription("全区域 F10").performClick()
        assertEquals(WindowPlacement.Floating, window.placement)
        onNodeWithTag("watch-video").assertWidthIsEqualTo(1200.dp)
        onNodeWithTag("watch-chat-peek").assertDoesNotExist()
        capture(this, "full-area-video")
        onRoot().performMouseInput { moveTo(Offset(1195f, 300f)) }
        onNodeWithTag("watch-chat-peek").assertIsDisplayed().assertWidthIsEqualTo(320.dp)
        onNodeWithTag("watch-video").assertWidthIsEqualTo(1200.dp)
        onRoot().performMouseInput { moveTo(Offset(1050f, 400f)) }
        onNodeWithTag("watch-chat-peek").assertIsDisplayed()
        capture(this, "full-area-hover-chat")
        onRoot().performMouseInput { moveTo(Offset(800f, 400f)) }
        onNodeWithTag("watch-chat-peek").assertDoesNotExist()
        onRoot().performMouseInput { moveTo(Offset(1195f, 300f)) }
        onNodeWithTag("watch-chat-peek").assertIsDisplayed()
        onRoot().performMouseInput { moveTo(Offset(-20f, 300f)) }
        onNodeWithTag("watch-chat-peek").assertDoesNotExist()
        onNodeWithContentDescription("全屏 F11").performClick()
        assertEquals(WindowPlacement.Fullscreen, window.placement)
        onNodeWithContentDescription("退出全屏").performClick()
        assertEquals(WindowPlacement.Floating, window.placement)
        onNodeWithContentDescription("退出全区域 F10").performClick()
        onNodeWithTag("watch-video").assertWidthIsEqualTo(880.dp)
    }

    @Test fun fullAreaHoverChatAnimatesSmoothlyOnMouseEnterAndLeave() = runSkikoComposeUiTest(size = Size(1200f, 800f)) {
        mainClock.autoAdvance = false
        setContent {
            DesktopTheme(DesktopAppearancePreferences()) {
                DesktopWatchLayout(
                    immersive = false,
                    chatPreferred = true,
                    onChatPreference = {},
                    modifier = Modifier.fillMaxSize(),
                    fullArea = true,
                    player = { _, _ -> Box(Modifier.fillMaxSize().background(Color(0xff101827))) },
                    chat = { modifier, close ->
                        DesktopChatPanel(
                            listOf(ChatEntry(1, LiveMessage(LiveMessageType.CHAT, "观众", "过渡动画测试"))),
                            emptyList(), "已连接", DesktopPlayerPreferences(), DesktopDanmakuPreferences(), "douyu", {}, modifier, close
                        )
                    }
                )
            }
        }
        mainClock.advanceTimeBy(50)
        onNodeWithTag("watch-chat-peek").assertDoesNotExist()

        onRoot().performMouseInput { moveTo(Offset(1195f, 300f)) }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(64)
        val enteringLeft = onNodeWithTag("watch-chat-peek").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Expected chat overlay to slide in progressively on hover, actual left=$enteringLeft", enteringLeft > 890f && enteringLeft < 1195f)

        mainClock.advanceTimeBy(PeekEnterDurationMillis.toLong() + 80L)
        val settledLeft = onNodeWithTag("watch-chat-peek").fetchSemanticsNode().boundsInRoot.left
        assertEquals(880f, settledLeft, 1f)

        onRoot().performMouseInput { moveTo(Offset(600f, 300f)) }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(64)
        val exitingNodes = onAllNodesWithTag("watch-chat-peek").fetchSemanticsNodes()
        assertEquals("Chat overlay should animate out rather than vanish immediately", 1, exitingNodes.size)
        val exitingLeft = exitingNodes.single().boundsInRoot.left
        assertTrue("Expected chat overlay to slide out progressively on leave, actual left=$exitingLeft", exitingLeft > 890f && exitingLeft < 1195f)

        mainClock.advanceTimeBy(PeekExitDurationMillis.toLong() + 80L)
        onNodeWithTag("watch-chat-peek").assertDoesNotExist()
    }

    @Test fun compactChatSheetAndInlineChatToggleAnimateSmoothly() = runSkikoComposeUiTest(size = Size(1200f, 800f)) {
        mainClock.autoAdvance = false
        var width by mutableStateOf(760)
        var preferred by mutableStateOf(true)
        setContent {
            DesktopTheme(DesktopAppearancePreferences()) {
                Surface {
                    Box(Modifier.size(width.dp, 600.dp)) {
                        DesktopWatchLayout(
                            immersive = false,
                            chatPreferred = preferred,
                            onChatPreference = { preferred = !preferred },
                            modifier = Modifier.fillMaxSize(),
                            player = { _, toggle -> Button(toggle) { Text("聊天开关") } },
                            chat = { modifier, close -> Surface(modifier) { Button(close) { Text("关闭聊天") } } }
                        )
                    }
                }
            }
        }
        mainClock.advanceTimeBy(50)
        onNodeWithTag("watch-chat-sheet").assertDoesNotExist()

        onNodeWithText("聊天开关").performClick()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(64)
        val sheetEnteringLeft = onNodeWithTag("watch-chat-sheet").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Expected compact chat sheet to slide in progressively, actual left=$sheetEnteringLeft", sheetEnteringLeft > 445f && sheetEnteringLeft < 755f)

        mainClock.advanceTimeBy(PeekEnterDurationMillis.toLong() + 80L)
        assertEquals(440f, onNodeWithTag("watch-chat-sheet").fetchSemanticsNode().boundsInRoot.left, 1f)

        onNodeWithText("关闭聊天").performClick()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(64)
        val sheetExiting = onAllNodesWithTag("watch-chat-sheet").fetchSemanticsNodes()
        assertEquals(1, sheetExiting.size)
        assertTrue("Expected compact chat sheet to slide out progressively", sheetExiting.single().boundsInRoot.left > 445f)

        mainClock.advanceTimeBy(PeekExitDurationMillis.toLong() + 80L)
        onNodeWithTag("watch-chat-sheet").assertDoesNotExist()

        runOnIdle { width = 1100 }
        mainClock.advanceTimeBy(50)
        onNodeWithTag("watch-chat").assertExists()

        onNodeWithText("聊天开关").performClick()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(64)
        val shrinkingChat = onAllNodesWithTag("watch-chat").fetchSemanticsNodes()
        assertEquals("Inline chat should shrink out smoothly rather than disappear in 1 frame", 1, shrinkingChat.size)
        mainClock.advanceTimeBy(260)
        onNodeWithTag("watch-chat").assertDoesNotExist()
    }

    @Test fun compactPlayerKeepsQualityFullscreenAndSecondaryActionsAccessible() = runSkikoComposeUiTest(size = Size(1200f, 800f)) {
        val rule = this
        var chosen: LivePlayQuality? = null
        var settings = 0; var fullscreen = 0; var lines = 0
        var held = false
        rule.setContent { DesktopTheme(DesktopAppearancePreferences()) { Surface { Box(Modifier.width(480.dp)) {
            DesktopPlayerToolbar(DesktopPlayerToolbarState(quality = high, qualities = listOf(high, low), lineCount = 3),
                actions({ fullscreen++ }, { settings++ }, { chosen = it }, { lines++ }), onMenuHold = { held = it })
        } } } }
        rule.onNodeWithContentDescription("全屏 F11").assertIsDisplayed().performClick()
        assertEquals(1, fullscreen)
        rule.onNodeWithText("2K120").performClick()
        rule.onNodeWithText("1080P60").performClick()
        assertEquals(low, chosen)
        rule.onNodeWithContentDescription("更多播放选项").performClick()
        rule.runOnIdle { assertTrue(held) }
        rule.onNodeWithText("切换线路 · 1/3").performClick()
        assertEquals(1, lines)
        rule.onNodeWithContentDescription("更多播放选项").performClick()
        rule.onNodeWithText("播放设置").performClick()
        assertEquals(1, settings)
        rule.runOnIdle { assertFalse(held) }
        capture(rule, "player-controls-480")
    }

    @Test fun miniPlayerControlsFitTheMinimumWindowWidth() = runSkikoComposeUiTest(size = Size(1200f, 800f)) {
        val rule = this
        var chosen: LivePlayQuality? = null
        rule.setContent { DesktopTheme(DesktopAppearancePreferences()) { Surface { Box(Modifier.width(360.dp)) {
            DesktopPlayerToolbar(DesktopPlayerToolbarState(mini = true, quality = high, qualities = listOf(high, low)), actions(quality = { chosen = it }))
        } } } }
        rule.onNodeWithContentDescription("暂停").assertIsDisplayed()
        rule.onNodeWithContentDescription("更多播放选项").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回普通窗口 P").assertIsDisplayed()
        rule.onNodeWithContentDescription("全屏 F11").assertIsDisplayed()
        capture(rule, "player-controls-mini-360")
        rule.onNodeWithContentDescription("更多播放选项").performClick()
        rule.onNodeWithText("1080P60").assertIsDisplayed().performClick()
        assertEquals(low, chosen)
        rule.onNodeWithContentDescription("更多播放选项").performClick()
        rule.onNodeWithText("关闭弹幕").assertIsDisplayed()
        rule.onNodeWithText("播放信息").assertIsDisplayed()
    }

    @Test fun localVideoUsesTheFullWidthWithoutUnavailableChatControls() = runSkikoComposeUiTest(size = Size(1600f, 1000f)) {
        val rule = this
        rule.setContent { DesktopTheme(DesktopAppearancePreferences()) { Surface { Box(Modifier.size(1100.dp, 600.dp)) {
            DesktopWatchLayout(false, true, {}, Modifier.fillMaxSize(), chatAvailable = false, player = { _, _ ->
                DesktopPlayerToolbar(DesktopPlayerToolbarState(chatAvailable = false), actions())
            }, chat = { _, _ -> error("Local video must not create a live chat pane") })
        } } } }
        rule.onNodeWithTag("watch-video").assertWidthIsEqualTo(1100.dp)
        rule.onNodeWithTag("watch-chat").assertDoesNotExist()
        rule.onNodeWithContentDescription("显示或隐藏聊天").assertDoesNotExist()
    }

    @Test fun controlsHideWhileIdleAndRemainVisibleForPauseOrAnOpenMenu() = runSkikoComposeUiTest(size = Size(1200f, 800f)) {
        val rule = this
        rule.mainClock.autoAdvance = false
        var playing by mutableStateOf(true)
        var fullscreen = 0
        rule.setContent { DesktopTheme(DesktopAppearancePreferences()) { Surface { Box(Modifier.size(700.dp, 400.dp).background(Color.Black)) {
            DesktopPlayerChrome(playing, false, true, "测试直播", { fullscreen++ }, Modifier.fillMaxSize()) { modifier, hold ->
                DesktopPlayerToolbar(DesktopPlayerToolbarState(playing = playing), actions(), modifier, onMenuHold = hold)
            }
        } } } }
        rule.mainClock.advanceTimeBy(3_500)
        rule.onNodeWithTag("player-toolbar").assertDoesNotExist()
        rule.onNodeWithTag("player-interaction").performMouseInput { moveTo(Offset(40f, 40f)) }
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithTag("player-toolbar").assertExists()
        rule.onNodeWithTag("player-interaction").performMouseInput { doubleClick(Offset(100f, 80f)) }
        rule.mainClock.advanceTimeBy(100)
        assertEquals(1, fullscreen)
        rule.onNodeWithContentDescription("更多播放选项").performClick()
        rule.mainClock.advanceTimeBy(6_000)
        rule.onNodeWithTag("player-toolbar").assertExists()
        rule.onNodeWithText("播放设置").performClick()
        rule.runOnIdle { playing = false }
        rule.mainClock.advanceTimeBy(6_000)
        rule.onNodeWithContentDescription("播放").assertExists()
    }

    @Test fun compactChatOpensAsASheetWithoutShrinkingVideoAndWideChatReturnsInline() = runSkikoComposeUiTest(size = Size(1600f, 1000f)) {
        val rule = this
        var width by mutableStateOf(1100)
        var preferred by mutableStateOf(true)
        rule.setContent { DesktopTheme(DesktopAppearancePreferences()) { Surface { Box(Modifier.size(width.dp, 600.dp)) {
            DesktopWatchLayout(false, preferred, { preferred = !preferred }, Modifier.fillMaxSize(), player = { _, toggle ->
                Button(toggle) { Text("聊天") }
            }, chat = { modifier, close -> Surface(modifier) { Button(close) { Text("关闭聊天") } } })
        } } } }
        rule.onNodeWithTag("watch-chat").assertExists()
        rule.onNodeWithTag("watch-video").assertWidthIsEqualTo(780.dp)
        rule.onNodeWithTag("chat-divider").performTouchInput { swipeLeft(startX = centerX, endX = centerX - 60f, durationMillis = 200) }
        assertTrue(rule.onNodeWithTag("watch-chat").fetchSemanticsNode().size.width > 340)
        rule.onNodeWithTag("chat-divider").performTouchInput { doubleClick() }
        rule.onNodeWithTag("watch-chat").assertWidthIsEqualTo(304.dp)
        rule.runOnIdle { width = 760 }
        rule.onNodeWithTag("watch-chat").assertDoesNotExist()
        rule.onNodeWithTag("watch-video").assertWidthIsEqualTo(760.dp)
        rule.onNodeWithText("聊天").performClick()
        rule.onNodeWithTag("watch-chat-sheet").assertIsDisplayed()
        rule.onNodeWithTag("watch-video").assertWidthIsEqualTo(760.dp)
        rule.onNodeWithText("聊天").performKeyInput { pressKey(Key.Escape) }
        rule.onNodeWithTag("watch-chat-sheet").assertDoesNotExist()
        assertTrue(preferred)
        rule.runOnIdle { width = 1100 }
        rule.onNodeWithTag("watch-chat").assertIsDisplayed()
    }

    @Test fun readingEarlierChatIsNotInterruptedByNewMessagesAndLatestCanBeRestored() = runSkikoComposeUiTest(size = Size(1200f, 800f)) {
        val rule = this
        fun entry(id: Long) = ChatEntry(id, LiveMessage(LiveMessageType.CHAT, "user $id", "message $id"))
        var messages by mutableStateOf((1L..100L).map(::entry))
        rule.setContent { DesktopTheme(DesktopAppearancePreferences()) { Surface { Box(Modifier.size(320.dp, 600.dp)) {
            DesktopChatPanel(messages, emptyList(), "已连接", DesktopPlayerPreferences(), DesktopDanmakuPreferences(), "douyu", {}, Modifier.fillMaxSize(), {})
        } } } }
        rule.onNodeWithText("message 100").assertIsDisplayed()
        rule.onNodeWithTag("chat-list").performTouchInput { swipeDown(startY = centerY, endY = bottom - 20f, durationMillis = 400) }
        rule.onNodeWithTag("chat-latest").assertExists()
        val before = rule.onNodeWithTag("chat-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        rule.runOnIdle { messages = messages + entry(101) }
        val after = rule.onNodeWithTag("chat-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(before, after, .02f)
        rule.onNodeWithText("回到最新").performClick()
        rule.onNodeWithText("message 101").assertIsDisplayed()
        capture(rule, "chat-panel-latest")
    }
}
