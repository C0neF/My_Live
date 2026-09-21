package com.mylive.app.ui.screen.room.player

import android.app.Application
import android.graphics.Canvas
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveDanmakuSurfaceFeedBehaviorTest {
    @get:Rule val compose = createComposeRule()

    private val messages = MutableSharedFlow<LiveMessage>(extraBufferCapacity = 64)
    private val playing = PlayerState(isPlaying = true, isLoading = false)
    private val playback = MutableStateFlow(playing)
    private val routeActive = mutableStateOf(true)
    private val mounted = mutableStateOf(true)
    private val selectedController = mutableStateOf<DanmakuController?>(null)
    private val controllers = mutableListOf<DanmakuController>()
    private var now = 0L
    private val clock = object : DanmakuClock {
        override fun uptimeMillis() = now
    }

    @After fun releaseControllers() {
        controllers.forEach { it.release() }
    }

    @Test fun pauseClearsVisibleMessagesAndResumeDoesNotReplayPausedTraffic() {
        val controller = mountFeed()
        send("before pause")
        render(controller)
        assertEquals(1, controller.scheduleSnapshot().activeCount)

        compose.runOnIdle { playback.value = playing.copy(isPlaying = false) }
        awaitSubscribers(0)
        render(controller)
        assertEmpty(controller)
        repeat(25) { send("paused $it") }

        compose.runOnIdle { playback.value = playing }
        awaitSubscribers(1)
        render(controller)
        assertEmpty(controller)
        send("after resume")
        render(controller)
        assertEquals(1, controller.scheduleSnapshot().activeCount)
    }

    @Test fun bufferingAndPlaybackErrorsClearTheSurfaceUntilPlaybackRecovers() {
        val controller = mountFeed()
        for (blockedState in listOf(playing.copy(isLoading = true), playing.copy(error = "source failed"))) {
            send("before blocked playback")
            render(controller)
            assertEquals(1, controller.scheduleSnapshot().activeCount)

            compose.runOnIdle { playback.value = blockedState }
            awaitSubscribers(0)
            render(controller)
            assertEmpty(controller)
            send("while blocked")

            compose.runOnIdle { playback.value = playing }
            awaitSubscribers(1)
            render(controller)
            assertEmpty(controller)
        }
    }

    @Test fun inactiveRoutesAndDisposedFeedsStopCollectingAndClearVisibleMessages() {
        val controller = mountFeed()
        send("before leaving")
        render(controller)
        assertEquals(1, controller.scheduleSnapshot().activeCount)

        compose.runOnIdle { routeActive.value = false }
        awaitSubscribers(0)
        render(controller)
        assertEmpty(controller)
        send("while route inactive")

        compose.runOnIdle { routeActive.value = true }
        awaitSubscribers(1)
        render(controller)
        assertEmpty(controller)
        send("after returning")
        render(controller)
        assertEquals(1, controller.scheduleSnapshot().activeCount)

        compose.runOnIdle { mounted.value = false }
        awaitSubscribers(0)
        render(controller)
        assertEmpty(controller)
        send("after disposal")
        assertEmpty(controller)
    }

    @Test fun replacingTheSurfaceClearsTheOldControllerAndFeedsOnlyTheNewOne() {
        val previous = mountFeed()
        send("old surface")
        render(previous)
        assertEquals(1, previous.scheduleSnapshot().activeCount)

        val replacement = newController()
        compose.runOnIdle { selectedController.value = replacement }
        compose.waitForIdle()
        awaitSubscribers(1)
        render(previous)
        assertEmpty(previous)
        assertEmpty(replacement)

        send("new surface")
        render(replacement)
        assertEquals(1, replacement.scheduleSnapshot().activeCount)
        assertEmpty(previous)
    }

    private fun mountFeed(): DanmakuController {
        val controller = newController()
        selectedController.value = controller
        compose.setContent {
            if (mounted.value) {
                LiveDanmakuSurfaceFeed(
                    messages = messages,
                    controller = selectedController.value,
                    active = routeActive.value,
                    playbackState = playback
                )
            }
        }
        awaitSubscribers(1)
        return controller
    }

    private fun newController() = DanmakuController(
        ApplicationProvider.getApplicationContext(),
        clock,
        measureText = { paint, text -> text.length * paint.textSize * 0.5f }
    ).apply {
        width = 1080
        height = 500
        danmuSize = 16f
        danmuArea = 1f
        danmuLineCount = 4
        controllers += this
    }

    private fun awaitSubscribers(expected: Int) {
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) { messages.subscriptionCount.value == expected }
    }

    private fun send(text: String) {
        compose.runOnIdle {
            assertTrue(messages.tryEmit(LiveMessage(LiveMessageType.CHAT, "viewer", text)))
        }
        compose.waitForIdle()
    }

    private fun render(controller: DanmakuController) {
        compose.runOnIdle {
            now += 16
            controller.draw(Canvas(), now * 1_000_000)
        }
    }

    private fun assertEmpty(controller: DanmakuController) {
        assertEquals(0, controller.scheduleSnapshot().pendingCount)
        assertEquals(0, controller.scheduleSnapshot().activeCount)
    }
}
