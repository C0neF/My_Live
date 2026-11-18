package com.mylive.app.ui.screen.room

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-port")
class PortraitPlayerResizeInteractionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var resizeState: PortraitPlayerResizeState
    private val messages = MutableStateFlow(messages(0, 60))
    private val notice = MutableStateFlow<String?>(null)
    private val roomKey = mutableStateOf("room-a")

    @Test fun upwardChatDragShrinksPlayerAndGivesItsHeightToChatThenDownwardRestoresIt() {
        render()
        val initialPlayer = bounds("video")
        val initialChat = bounds("chat")
        swipeChat(upward = true)
        val smallerPlayer = bounds("video")
        val largerChat = bounds("chat")
        assertTrue(smallerPlayer.height < initialPlayer.height - 20f)
        assertEquals(initialPlayer.height - smallerPlayer.height, largerChat.height - initialChat.height, 1f)
        compose.onNodeWithTag("chat").performTouchInput {
            swipe(center, center + Offset(0f, (initialPlayer.height - smallerPlayer.height) / 2f), durationMillis = 600)
        }
        assertTrue(bounds("video").height > smallerPlayer.height + 20f)
        compose.onNodeWithText("message-59", substring = true).assertIsDisplayed()
    }

    @Test fun repeatedUpwardDragsStopAtTheMinimumPlayerHeight() {
        val aspectRatio = mutableStateOf(9f / 16f)
        render(aspectRatio = { aspectRatio.value })
        repeat(4) { swipeChat(upward = true) }
        compose.onNodeWithTag("video").assertHeightIsEqualTo(360.dp / DefaultLandscapePlayerAspectRatio)
        val minimumHeight = bounds("video").height
        swipeChat(upward = true)
        assertEquals(minimumHeight, bounds("video").height, 1f)
        compose.onNodeWithText("message-59", substring = true).assertIsDisplayed()

        // Shrunk portrait height matches normal 16:9 landscape height.
        compose.runOnIdle { aspectRatio.value = DefaultLandscapePlayerAspectRatio }
        assertEquals(bounds("video").height, minimumHeight, 1f)
    }

    @Test fun newMessagesDoNotInterruptAnActiveResizeAndFollowingResumesAfterRelease() {
        render()
        val chat = compose.onNodeWithTag("chat")
        chat.performTouchInput {
            down(Offset(centerX, height * 0.8f))
            moveBy(Offset(0f, -90f), delayMillis = 100)
        }
        val heightDuringDrag = bounds("video").height
        compose.runOnIdle { messages.value = messages(0, 70) }
        assertEquals(heightDuringDrag, bounds("video").height, 1f)
        chat.performTouchInput { moveBy(Offset(0f, -50f), delayMillis = 100) }
        assertTrue(bounds("video").height < heightDuringDrag - 20f)
        chat.performTouchInput { up() }
        compose.onNodeWithText("message-69", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("滚动到最新弹幕").assertDoesNotExist()
    }

    @Test fun chatHistoryStillScrollsAtTheExpandedLimitAndNewMessagesDoNotPullItAway() {
        render()
        val originalHeight = bounds("video").height
        swipeChat(upward = false)
        assertEquals(originalHeight, bounds("video").height, 1f)
        compose.runOnIdle { messages.value = messages(0, 70) }
        compose.onNodeWithContentDescription("滚动到最新弹幕").assertIsDisplayed().performClick()
        compose.onNodeWithText("message-69", substring = true).assertIsDisplayed()
    }

    @Test fun emptyChatCanStillResizeAndLandscapeSourcesKeepTheirSize() {
        messages.value = emptyList()
        val aspectRatio = mutableStateOf(9f / 16f)
        render(aspectRatio = { aspectRatio.value })
        val before = bounds("video").height
        swipeChat(upward = true)
        assertTrue(bounds("video").height < before)
        compose.runOnIdle { aspectRatio.value = 16f / 9f }
        val landscapeHeight = bounds("video").height
        swipeChat(upward = true)
        assertEquals(landscapeHeight, bounds("video").height, 1f)
    }

    @Test fun savedSizeSurvivesRecreationAndANewRoomStartsExpanded() {
        val restoration = StateRestorationTester(compose)
        render(restoration)
        swipeChat(upward = true)
        val savedHeight = bounds("video").height
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(savedHeight, bounds("video").height, 1f)
        compose.runOnIdle { roomKey.value = "room-b" }
        assertTrue(bounds("video").height > savedHeight + 20f)
    }

    private fun render(restoration: StateRestorationTester? = null, aspectRatio: () -> Float = { 9f / 16f }) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            MaterialTheme {
                resizeState = rememberPortraitPlayerResizeState(roomKey.value)
                val ratio = aspectRatio()
                AdaptiveLiveRoomLayout(
                    splitPane = false,
                    chatWidthDp = 320,
                    limitStackedPlayerHeight = false,
                    videoAspectRatio = ratio,
                    playerResizeState = resizeState,
                    player = { maximumHeight ->
                        LiveRoomPlayerArea(maximumHeight, videoAspectRatio = ratio) {
                            Box(Modifier.fillMaxSize().testTag("video"))
                        }
                        Spacer(Modifier.height(64.dp))
                    },
                    chat = {
                        ChatPanel(messages, notice, it.testTag("chat"), playerResizing = resizeState.isResizing)
                    }
                )
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun swipeChat(upward: Boolean) {
        compose.onNodeWithTag("chat").performTouchInput {
            val top = Offset(centerX, height * 0.15f)
            val bottom = Offset(centerX, height * 0.85f)
            swipe(if (upward) bottom else top, if (upward) top else bottom, durationMillis = 600)
        }
        compose.waitForIdle()
    }

    private fun messages(start: Int, end: Int) = (start until end).map {
        DisplayLiveMessage(it.toLong(), LiveMessage(LiveMessageType.CHAT, "QA", "message-$it"))
    }
}
