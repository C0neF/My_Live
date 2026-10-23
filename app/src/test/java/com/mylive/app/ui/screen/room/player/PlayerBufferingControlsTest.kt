package com.mylive.app.ui.screen.room.player

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** Full PlayerView and real Controller/ExoPlayer; the HTTP response is held before any media arrives. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h891dp")
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class PlayerBufferingControlsTest {
    private val effects = StandardTestDispatcher()
    @get:Rule val compose = createComposeRule(effectContext = effects)

    private var showPlayer by mutableStateOf(true)
    private lateinit var controller: PlayerController
    private var server: MockWebServer? = null

    @After
    fun releasePlaybackAndLoopbackServer() {
        if (::controller.isInitialized) {
            compose.runOnIdle { showPlayer = false }
            settleUi()
            compose.runOnIdle { controller.release() }
        }
        server?.shutdown()
    }

    @Test
    fun bufferingWithPlaybackRequestedShowsPauseAndItsClickPausesTheActualPlayer() {
        renderBufferingPlayer()

        compose.onNodeWithContentDescription("暂停").assertIsDisplayed().performClick()
        settleUi()

        assertActualBufferingState(playbackRequested = false)
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()
    }

    @Test
    fun explicitPauseWhileBufferingShowsPlayAndItsClickRequestsPlayback() {
        renderBufferingPlayer()
        compose.runOnIdle { controller.pause() }
        settleUi()
        assertActualBufferingState(playbackRequested = false)

        compose.onNodeWithContentDescription("播放").assertIsDisplayed().performClick()
        settleUi()

        assertActualBufferingState(playbackRequested = true)
    }

    @Test
    fun externalPlayWhileStillBufferingUpdatesTheControlToPause() {
        renderBufferingPlayer()
        compose.runOnIdle { controller.pause() }
        settleUi()
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()

        // The foreground notification also invokes the real Player's play command directly.
        // Its listener must update PlayerView even though isPlaying remains false throughout.
        compose.runOnIdle { checkNotNull(controller.player).play() }
        settleUi()

        assertActualBufferingState(playbackRequested = true)
        compose.onNodeWithContentDescription("暂停").assertIsDisplayed()
    }

    @Test
    fun externalPauseUpdatesTheControlWithoutAnIsPlayingCallback() {
        renderBufferingPlayer()
        var playingCallbacks = 0
        compose.runOnIdle {
            checkNotNull(controller.player).addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playingCallbacks += 1
                }
            })
            checkNotNull(controller.player).pause()
        }
        settleUi()

        assertActualBufferingState(playbackRequested = false)
        compose.runOnIdle {
            assertEquals("Buffering never changed actual isPlaying, so play intent must drive this update",
                0, playingCallbacks)
        }
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()
    }

    @Test
    fun endedPlayerWithRetainedPlayIntentShowsPlayAndKeepsItsExistingAction() {
        renderPlayer()
        compose.runOnIdle {
            // Preparing an empty playlist reaches real Media3 ENDED without a fake listener
            // or media response. With no source to recover, the Controller leaves EOF intact.
            checkNotNull(controller.player).play()
            checkNotNull(controller.player).prepare()
        }
        settleUi()
        val pauseGeneration = compose.runOnIdle {
            val actualPlayer = checkNotNull(controller.player)
            assertEquals(Player.STATE_ENDED, actualPlayer.playbackState)
            assertTrue("EOF can retain a play request even though playback ended", actualPlayer.playWhenReady)
            assertFalse(actualPlayer.isPlaying)
            assertNull(controller.state.value.error)
            controller.playbackPauseGeneration
        }

        compose.onNodeWithContentDescription("播放").assertIsDisplayed().performClick()
        settleUi()

        compose.runOnIdle {
            assertTrue("EOF's existing Play action must keep requesting playback", checkNotNull(controller.player).playWhenReady)
            assertEquals("EOF's Play action must not become a manual pause", pauseGeneration, controller.playbackPauseGeneration)
        }
    }

    @Test
    fun idleAndErrorKeepPlayControlsEvenWhenMedia3RetainsPlayIntent() {
        renderBufferingPlayer()
        compose.runOnIdle { controller.stop() }
        settleUi()
        assertStoppedPlayerRetainsIntent()
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()

        compose.runOnIdle { controller.showError("synthetic source error") }
        settleUi()

        assertStoppedPlayerRetainsIntent()
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()
    }

    private fun renderBufferingPlayer() {
        val sourceServer = MockWebServer().apply {
            enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            start(InetAddress.getByName("127.0.0.1"), 0)
        }.also { server = it }
        val sourceUrl = "http://127.0.0.1:${sourceServer.port}/buffering.flv"
        renderPlayer()
        compose.runOnIdle {
            controller.play(listOf(sourceUrl))
        }
        assertEquals("The real media loader must reach the loopback server before testing controls",
            "/buffering.flv", sourceServer.takeRequest(5, TimeUnit.SECONDS)?.path)
        settleUi()
        assertActualBufferingState(playbackRequested = true)
    }

    private fun renderPlayer() {
        compose.setContent {
            val context = LocalContext.current
            val actualController = remember { PlayerController(context.applicationContext) }
            SideEffect { controller = actualController }
            MaterialTheme {
                if (showPlayer) {
                    PlayerView(
                        playerController = actualController,
                        viewState = PlayerViewUiState(danmakuEnabled = false),
                        modifier = Modifier.fillMaxWidth().height(300.dp)
                    )
                }
            }
        }
        compose.runOnIdle {
            controller.setVolumeDirect(0f)
        }
    }

    private fun settleUi() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.runOnUiThread { effects.scheduler.runCurrent() }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnUiThread { effects.scheduler.runCurrent() }
        compose.waitForIdle()
    }

    private fun assertStoppedPlayerRetainsIntent() {
        compose.runOnIdle {
            val actualPlayer = checkNotNull(controller.player)
            assertEquals(Player.STATE_IDLE, actualPlayer.playbackState)
            assertTrue("Stopping media does not clear Media3's playWhenReady intent", actualPlayer.playWhenReady)
            assertFalse(actualPlayer.isPlaying)
            assertFalse(controller.state.value.isPlaying)
            assertFalse(controller.state.value.isLoading)
        }
    }

    private fun assertActualBufferingState(playbackRequested: Boolean) {
        compose.runOnIdle {
            val actualPlayer = checkNotNull(controller.player)
            assertEquals("The source remains in real Media3 BUFFERING without a response",
                Player.STATE_BUFFERING, actualPlayer.playbackState)
            assertEquals("The control must apply the requested command to the real player",
                playbackRequested, actualPlayer.playWhenReady)
            assertEquals("The real Player callback must project its buffering state before testing Compose",
                actualPlayer.playbackState, controller.state.value.playbackState)
            assertEquals("The real Player callback must project its play intent before testing Compose",
                actualPlayer.playWhenReady, controller.state.value.playWhenReady)
            assertFalse("No media has arrived, so actual playback cannot have started", actualPlayer.isPlaying)
            assertFalse("Controller output state must also remain non-playing", controller.state.value.isPlaying)
            assertTrue("The real buffering callback must keep the loading state", controller.state.value.isLoading)
            assertNull("A media error must not replace the buffering fixture", controller.state.value.error)
        }
    }
}
