package com.mylive.app.ui.screen.room.player

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Protects the long-press contract by sending a real gesture through PlayerView. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h891dp")
class PlayerLongPressInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun longPressDoesNotToggleOrPausePlayback() {
        val controller = mockk<PlayerController>(relaxed = true) {
            every { state } returns MutableStateFlow(PlayerState(isLoading = false))
            every { player } returns null
        }
        compose.setContent {
            MaterialTheme {
                PlayerView(controller, PlayerViewUiState(danmakuEnabled = false),
                    modifier = Modifier.fillMaxWidth().height(300.dp).testTag("long_press_player"))
            }
        }

        compose.onNodeWithTag("long_press_player").performTouchInput { longClick(center) }
        compose.waitForIdle()

        verify(exactly = 0) { controller.togglePlayPause() }
        verify(exactly = 0) { controller.pause() }
    }
}
