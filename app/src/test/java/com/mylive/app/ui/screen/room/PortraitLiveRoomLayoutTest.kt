package com.mylive.app.ui.screen.room

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.mylive.app.ui.theme.MyLiveTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-port")
class PortraitLiveRoomLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun embeddedPortraitVideoGetsMoreHeightAndKeepsChatReadable() {
        renderStackedVideo(9f / 16f)
        compose.onNodeWithTag("chat_area").assertIsDisplayed().assertHeightIsAtLeast(160.dp)
        val video = compose.onNodeWithTag("video_surface").fetchSemanticsNode().boundsInRoot
        assertTrue("Portrait video must not stay in the old 16:9 slot", video.height > video.width)
    }

    @Test fun landscapeSourceKeepsItsSixteenByNineSlot() {
        renderStackedVideo(16f / 9f)
        val video = compose.onNodeWithTag("video_surface").fetchSemanticsNode().boundsInRoot
        assertEquals(16f / 9f, video.width / video.height, 0.01f)
    }

    @Test fun openingPortraitChatOverlaysWithoutResizingThePlayerAndScrimClosesIt() {
        val fraction = mutableFloatStateOf(0f)
        compose.setContent {
            MyLiveTheme {
                ImmersiveLiveRoomLayout(
                    panelFraction = fraction.floatValue,
                    onDismissPanel = { fraction.floatValue = 0f },
                    player = { Box(it.testTag("video_surface")) },
                    panel = { Box(it.testTag("chat_content")) }
                )
            }
        }
        val before = compose.onNodeWithTag("video_surface").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { fraction.floatValue = 1f }
        compose.onNodeWithTag("chat_content").assertIsDisplayed()
        val after = compose.onNodeWithTag("video_surface").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("immersive_chat_panel").fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        assertTrue(panel.width < before.width)
        compose.onNodeWithTag("immersive_chat_scrim").performClick()
        compose.onNodeWithTag("immersive_chat_panel").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w800dp-h400dp-land")
    fun landscapeChatStillSharesTheWindowWithThePlayer() {
        compose.setContent {
            MyLiveTheme {
                ImmersiveLiveRoomLayout(
                    panelFraction = 1f,
                    onDismissPanel = {},
                    player = { Box(it.testTag("video_surface")) },
                    panel = { Box(it.testTag("chat_content")) }
                )
            }
        }
        val player = compose.onNodeWithTag("video_surface").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("immersive_chat_panel").fetchSemanticsNode().boundsInRoot
        assertTrue(player.right <= panel.left)
        compose.onNodeWithTag("immersive_chat_scrim").assertDoesNotExist()
    }

    private fun renderStackedVideo(aspectRatio: Float) {
        compose.setContent {
            MyLiveTheme {
                AdaptiveLiveRoomLayout(
                    splitPane = false,
                    chatWidthDp = 320,
                    limitStackedPlayerHeight = false,
                    videoAspectRatio = aspectRatio,
                    player = { maximumHeight ->
                        LiveRoomPlayerArea(maximumHeight, videoAspectRatio = aspectRatio) {
                            Box(Modifier.fillMaxSize().testTag("video_surface"))
                        }
                        Spacer(Modifier.height(64.dp))
                    },
                    chat = { Box(it.fillMaxWidth().testTag("chat_area")) }
                )
            }
        }
    }
}
