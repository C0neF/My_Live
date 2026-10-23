package com.mylive.app.ui.screen.room.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
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
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** Full PlayerView and real Controller/ExoPlayer; the HTTP response is held before any media arrives. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class PlayerBufferingControlsAndroidTest {
    private val effects = StandardTestDispatcher()
    @get:Rule val compose = createComposeRule(effectContext = effects)

    private var showPlayer by mutableStateOf(true)
    private lateinit var controller: PlayerController
    private var server: MockWebServer? = null

    @Before fun requireIsolatedAuditApp() {
        assertEquals("com.mylive.app.audit", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
    }

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

    private fun renderBufferingPlayer() {
        val sourceServer = MockWebServer().apply {
            enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            start(InetAddress.getByName("127.0.0.1"), 0)
        }.also { server = it }
        val sourceUrl = "http://127.0.0.1:${sourceServer.port}/buffering.flv"
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
            controller.play(listOf(sourceUrl))
        }
        assertEquals("The real media loader must reach the loopback server before testing controls",
            "/buffering.flv", sourceServer.takeRequest(5, TimeUnit.SECONDS)?.path)
        settleUi()
        assertActualBufferingState(playbackRequested = true)
    }

    private fun settleUi() {
        compose.runOnUiThread { effects.scheduler.runCurrent() }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnUiThread { effects.scheduler.runCurrent() }
        compose.waitForIdle()
    }

    private fun assertActualBufferingState(playbackRequested: Boolean) {
        compose.runOnIdle {
            val actualPlayer = checkNotNull(controller.player)
            assertEquals("The source remains in real Media3 BUFFERING without a response",
                Player.STATE_BUFFERING, actualPlayer.playbackState)
            assertEquals("The control must apply the requested command to the real player",
                playbackRequested, actualPlayer.playWhenReady)
            assertFalse("No media has arrived, so actual playback cannot have started", actualPlayer.isPlaying)
            assertFalse("Controller output state must also remain non-playing", controller.state.value.isPlaying)
            assertTrue("The real buffering callback must keep the loading state", controller.state.value.isLoading)
            assertNull("A media error must not replace the buffering fixture", controller.state.value.error)
        }
    }
}
