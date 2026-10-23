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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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

/** Actual line-sheet actions, Controller and ExoPlayer. Loopback responses remain buffering. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w900dp-h600dp-land")
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class PlayerLineSelectionStateTest {
    private val effects = StandardTestDispatcher()
    @get:Rule val compose = createComposeRule(effectContext = effects)

    private var showPlayer by mutableStateOf(true)
    private lateinit var controller: PlayerController
    private var server: MockWebServer? = null
    private lateinit var firstUrl: String
    private lateinit var secondUrl: String

    @After
    fun releaseOnlyTheFixturePlayerAndServer() {
        if (::controller.isInitialized) {
            compose.runOnIdle { showPlayer = false }
            settleUi()
            compose.runOnIdle { controller.release() }
        }
        server?.shutdown()
    }

    @Test
    fun selectingASecondDistinctUrlUpdatesTheActualPlayerAndDisplayedLine() {
        renderPlayer(duplicateUrls = false)

        selectSecondLine()

        assertActualSelection(index = 1, url = secondUrl)
        compose.onNodeWithText("线路 2").assertIsDisplayed()
    }

    @Test
    fun selectingASecondIdenticalUrlUpdatesTheActualIndexAndDisplayedLine() {
        renderPlayer(duplicateUrls = true)
        compose.runOnIdle {
            assertEquals("With force HTTPS disabled, the actual Controller retains both source entries",
                listOf(firstUrl, firstUrl), controller.getUrls())
        }

        selectSecondLine()

        assertActualSelection(index = 1, url = firstUrl)
        compose.onNodeWithText("线路 2").assertIsDisplayed()
    }

    @Test
    fun refreshingToAShorterListWithTheSameFirstUrlRemovesTheObsoleteLine() {
        renderPlayer(duplicateUrls = false)
        compose.onNodeWithText("线路 1").assertIsDisplayed()

        // A refreshed source is delivered through the actual Controller playback entry. Keeping
        // the first URL is valid; the now-unavailable second source must leave the next menu.
        compose.runOnIdle {
            controller.play(listOf(firstUrl), origin = PlaybackRequestOrigin.SourceRecovery)
        }
        settleUi()
        assertActualSelection(index = 0, url = firstUrl)
        compose.runOnIdle { assertEquals(listOf(firstUrl), controller.getUrls()) }

        openLineSheet()

        compose.onAllNodesWithText("线路 2").assertCountEquals(0)
    }

    @Test
    fun anOpenLineMenuUpdatesWhenARefreshedListKeepsTheSameFirstUrl() {
        renderPlayer(duplicateUrls = false)
        openLineSheet()
        compose.onNodeWithText("线路 2").assertIsDisplayed()

        compose.runOnIdle {
            controller.play(listOf(firstUrl), origin = PlaybackRequestOrigin.SourceRecovery)
        }
        settleUi()
        assertActualSelection(index = 0, url = firstUrl)
        compose.runOnIdle { assertEquals(listOf(firstUrl), controller.getUrls()) }

        compose.onAllNodesWithText("线路 2").assertCountEquals(0)
    }

    private fun renderPlayer(duplicateUrls: Boolean) {
        val sourceServer = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
            }
            start(InetAddress.getByName("127.0.0.1"), 0)
        }.also { server = it }
        firstUrl = "http://127.0.0.1:${sourceServer.port}/first.flv"
        secondUrl = "http://127.0.0.1:${sourceServer.port}/second.flv"
        compose.setContent {
            val context = LocalContext.current
            val actualController = remember { PlayerController(context.applicationContext, forceHttps = false) }
            SideEffect { controller = actualController }
            MaterialTheme {
                if (showPlayer) {
                    PlayerView(
                        playerController = actualController,
                        viewState = PlayerViewUiState(danmakuEnabled = false, fullscreenOverride = true),
                        modifier = Modifier.fillMaxWidth().height(300.dp)
                    )
                }
            }
        }
        compose.runOnIdle {
            controller.setVolumeDirect(0f)
            controller.play(listOf(firstUrl, if (duplicateUrls) firstUrl else secondUrl))
        }
        assertEquals("The actual media loader must reach the loopback source before the line gesture",
            "/first.flv", sourceServer.takeRequest(5, TimeUnit.SECONDS)?.path)
        settleUi()
        assertActualSelection(index = 0, url = firstUrl)
        compose.onNodeWithText("线路 1").assertIsDisplayed()
    }

    private fun selectSecondLine() {
        openLineSheet()
        // Invoke the actual Surface action. Robolectric touch dispatch to Material's separate
        // dialog window can miss the row, even though the real sheet semantics are present.
        compose.onNodeWithText("线路 2").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick) { click -> click() }
        settleUi()
        compose.runOnIdle {
            assertEquals("The actual sheet action must reach the Controller before asserting its UI label",
                1, controller.getCurrentUrlIndex())
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("选择线路").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun openLineSheet() {
        compose.onNodeWithText("线路 1").assertIsDisplayed().performClick()
        settleUi()
        compose.onNodeWithText("选择线路").assertIsDisplayed()
    }

    private fun assertActualSelection(index: Int, url: String) {
        compose.runOnIdle {
            val actualPlayer = checkNotNull(controller.player)
            assertEquals("The real Controller must accept the selected line", index, controller.getCurrentUrlIndex())
            assertEquals(url, controller.state.value.currentUrl)
            assertEquals("The selected source must also reach the real Media3 player",
                url, actualPlayer.currentMediaItem?.localConfiguration?.uri?.toString())
            assertEquals(Player.STATE_BUFFERING, actualPlayer.playbackState)
            assertTrue(actualPlayer.playWhenReady)
            assertFalse(actualPlayer.isPlaying)
            assertNull("Automatic error recovery must not explain a changed line in this fixture",
                controller.state.value.error)
        }
    }

    private fun settleUi() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.runOnUiThread { effects.scheduler.runCurrent() }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnUiThread { effects.scheduler.runCurrent() }
        compose.waitForIdle()
    }
}
