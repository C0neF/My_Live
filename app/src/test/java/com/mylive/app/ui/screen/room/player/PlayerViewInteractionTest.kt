package com.mylive.app.ui.screen.room.player

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-port")
class PlayerViewInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun longPlaybackErrorIsReadableAndScrollableInAShortPlayer() {
        val error = (1..8).joinToString("\n") { "连接失败，第 $it 条错误信息" }
        val controller = mockk<PlayerController>(relaxed = true) {
            every { state } returns MutableStateFlow(PlayerState(isLoading = false, error = error))
            every { player } returns null
        }
        compose.setContent {
            MaterialTheme {
                PlayerView(
                    playerController = controller,
                    viewState = PlayerViewUiState(danmakuEnabled = false),
                    modifier = Modifier.size(width = 320.dp, height = 180.dp)
                )
            }
        }

        val message = compose.onNodeWithText(error).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        message.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(error.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        val scroll = message.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        message.performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue(scroll.value() > 0f) }
    }

    @Test
    fun leftSwipeDoesNotDragChatInPortraitFullscreen() {
        assertEquals(0f, swipeLeftInFullscreen(), 0f)
    }

    @Test
    @Config(qualifiers = "w800dp-h400dp-land")
    fun leftSwipeStillDragsChatInLandscapeFullscreen() {
        assertTrue(swipeLeftInFullscreen() < 0f)
    }

    private fun swipeLeftInFullscreen(): Float {
        var dragDistance = 0f
        compose.setContent {
            MaterialTheme {
                PlayerView(
                    playerController = null,
                    viewState = PlayerViewUiState(danmakuEnabled = false, fullscreenOverride = true),
                    actions = PlayerViewActions(onHorizontalDragDelta = { dragDistance += it }),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        compose.onNodeWithTag("player_video_waiting").performTouchInput {
            swipeLeft(startX = width * 0.8f, endX = width * 0.2f)
        }
        return compose.runOnIdle { dragDistance }
    }
}
