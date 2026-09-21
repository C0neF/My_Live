package com.mylive.app.ui.screen.room

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.mylive.app.ui.theme.MyLiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w800dp-h600dp")
class AdaptiveLiveRoomLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun stackedTabletReservesChatSpaceAndConstrainsTheActualVideoSurface() {
        compose.setContent {
            MyLiveTheme {
                AdaptiveLiveRoomLayout(
                    splitPane = false,
                    chatWidthDp = 320,
                    limitStackedPlayerHeight = true,
                    modifier = Modifier.fillMaxSize(),
                    player = { maximumHeight ->
                        LiveRoomPlayerArea(maximumHeight) {
                            Box(Modifier.fillMaxSize().testTag("video_surface"))
                        }
                        Spacer(Modifier.height(56.dp))
                    },
                    details = {},
                    chat = { Box(it.fillMaxWidth().testTag("chat_area")) }
                )
            }
        }
        compose.onNodeWithTag("chat_area").assertIsDisplayed().assertHeightIsAtLeast(160.dp)
        val surface = compose.onNodeWithTag("video_surface").fetchSemanticsNode().boundsInRoot
        val player = compose.onNodeWithTag("live_room_player").fetchSemanticsNode().boundsInRoot
        val chat = compose.onNodeWithTag("chat_area").fetchSemanticsNode().boundsInRoot
        assertEquals(player.height, surface.height, 1f)
        assertTrue(surface.bottom <= chat.top)
    }
}