package com.mylive.app.ui.screen.room.player

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device regression for the null controller exposed while LivePlaybackHost loads preferences.
 * These tests render the real PlayerView and use real, idle PlayerControllers / ExoPlayers.
 * No stream, network request, saved preference, or account data is needed.
 */
@RunWith(AndroidJUnit4::class)
class PlayerGestureInitializationTest {
    @get:Rule
    val compose = createComposeRule()

    private var selectedController by mutableStateOf<PlayerController?>(null)
    private var showPlayer by mutableStateOf(true)
    private val controllers = mutableListOf<PlayerController>()
    private lateinit var audioManager: AudioManager
    private var savedMediaVolume = 0

    @Before
    fun rememberDeviceVolume() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        savedMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    @After
    fun releasePlayersAndRestoreDeviceVolume() {
        // Detach the actual platform video view before releasing its ExoPlayer.
        compose.runOnIdle { showPlayer = false }
        compose.waitForIdle()
        compose.runOnIdle { controllers.forEach(PlayerController::release) }
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedMediaVolume, 0)
    }

    @Test
    fun controllerReadyBeforeFirstTouch_doubleTapTogglesFullscreen() {
        val controller = newController()
        renderPlayer()
        makeReady(controller)

        doubleTapVideo()

        assertTrue("A ready controller must receive the first double tap", controller.state.value.isFullscreen)
    }

    @Test
    fun touchBeforeControllerReady_laterDoubleTapTogglesFullscreen() {
        val controller = newController()
        renderPlayer()
        touchWhileControllerIsLoading()
        makeReady(controller)

        doubleTapVideo()

        assertTrue("An early touch must not retain the startup null controller", controller.state.value.isFullscreen)
    }

    @Test
    fun controllerReadyBeforeFirstTouch_verticalSwipeChangesMediaVolume() {
        val controller = newController()
        renderPlayer()
        makeReady(controller)
        val startingVolume = setMediaVolumeForSwipe()

        swipeVolumeUp()

        assertTrue("The ready controller must handle a media volume swipe", mediaVolume() > startingVolume)
    }

    @Test
    fun touchBeforeControllerReady_laterVerticalSwipeChangesMediaVolume() {
        val controller = newController()
        renderPlayer()
        touchWhileControllerIsLoading()
        makeReady(controller)
        val startingVolume = setMediaVolumeForSwipe()

        swipeVolumeUp()

        assertTrue("An early touch must not freeze volume gestures after startup", mediaVolume() > startingVolume)
    }

    @Test
    fun touchBeforeControllerReady_lockAndUnlockStillAllowsDoubleTap() {
        val controller = newController()
        renderPlayer()
        touchWhileControllerIsLoading()
        makeReady(controller)

        // The early tap hides chrome. A second single tap shows the lock action again.
        compose.onNodeWithTag(PLAYER).performTouchInput {
            advanceEventTime(600)
            click(center)
        }
        settleSingleTap()
        compose.onAllNodesWithContentDescription("锁屏").onFirst().performClick()
        compose.onAllNodesWithContentDescription("解锁").onFirst().performClick()
        doubleTapVideo()

        assertTrue("Lock/unlock must retain the ready controller", controller.state.value.isFullscreen)
    }

    @Test
    fun controllerReplacedAfterFirstGesture_doubleTapUsesReplacementController() {
        val first = newController()
        val replacement = newController()
        renderPlayer()
        makeReady(first)
        doubleTapVideo()
        assertTrue("The initial controller must receive its gesture", first.state.value.isFullscreen)
        makeReady(replacement)

        doubleTapVideo()

        assertTrue("A later double tap must reach the replacement controller", replacement.state.value.isFullscreen)
        assertTrue("Replacing a controller must stop dispatch to the old one", first.state.value.isFullscreen)
    }

    private fun newController(): PlayerController = compose.runOnIdle {
        PlayerController(InstrumentationRegistry.getInstrumentation().targetContext).also {
            // Idle, with no source: control auto-hide timers cannot explain gesture failures.
            it.stop()
            controllers += it
        }
    }

    private fun renderPlayer() {
        compose.setContent {
            MaterialTheme {
                if (showPlayer) {
                    PlayerView(
                        playerController = selectedController,
                        viewState = PlayerViewUiState(danmakuEnabled = false),
                        modifier = Modifier.fillMaxWidth().height(300.dp).testTag(PLAYER)
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun makeReady(controller: PlayerController) {
        compose.runOnIdle { selectedController = controller }
        compose.waitForIdle()
        assertFalse("The fixture is idle, so controls cannot auto-hide", controller.state.value.isPlaying)
    }

    private fun touchWhileControllerIsLoading() {
        compose.runOnIdle { assertTrue(selectedController == null) }
        compose.onNodeWithTag(PLAYER).performTouchInput { click(center) }
        settleSingleTap()
    }

    private fun settleSingleTap() {
        // Wait out double-tap detection so the next gesture cannot be grouped with this tap.
        // Pointer detection also uses Android uptime, which mainClock alone does not advance.
        compose.mainClock.advanceTimeBy(600)
        SystemClock.sleep(600)
        compose.waitForIdle()
    }

    private fun doubleTapVideo() {
        compose.onNodeWithTag(PLAYER).performTouchInput {
            advanceEventTime(600)
            doubleClick(center)
        }
        compose.waitForIdle()
    }

    private fun setMediaVolumeForSwipe(): Int {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume / 3, 0)
        return mediaVolume()
    }

    private fun mediaVolume(): Int = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    private fun swipeVolumeUp() {
        // Middle of the right half, clear of the top/bottom buttons and lock affordance.
        compose.onNodeWithTag(PLAYER).performTouchInput {
            advanceEventTime(600)
            swipe(
                start = Offset(width * 0.65f, height * 0.5f),
                end = Offset(width * 0.65f, height * 0.25f),
                durationMillis = 350
            )
        }
        compose.waitForIdle()
    }

    private companion object {
        const val PLAYER = "startup_gesture_player"
    }
}
