package com.mylive.app.ui.screen.room.player

import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FixedDanmakuOverflowTrackPicker(private val track: Int = 0) : DanmakuOverflowTrackPicker {
    override fun pick(trackCount: Int): Int {
        if (trackCount <= 0) return 0
        return track.coerceIn(0, trackCount - 1)
    }
}

private class FakeDanmakuClock(private var nowMs: Long = 0L) : DanmakuClock {
    override fun uptimeMillis(): Long = nowMs
    fun advance(deltaMs: Long) {
        nowMs += deltaMs
    }
}

/**
 * Exercises the surface schedule path with fixed clock + overflow picker.
 * Requires Robolectric because Paint.measureText is Android-backed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DanmakuScheduleDeterminismTest {

    @Test
    fun sameInputYieldsSameScrollTrackWhenOverflowPickerIsFixed() {
        val clock = FakeDanmakuClock(nowMs = 1_000L)
        val controller = DanmakuController(
            context = ApplicationProvider.getApplicationContext(),
            clock = clock,
            overflowTrackPicker = FixedDanmakuOverflowTrackPicker(track = 0)
        )
        controller.width = 1080
        controller.height = 600
        controller.danmuLineCount = 4
        controller.danmuArea = 1.0f
        controller.danmuDelayMs = 0
        controller.danmuSpeed = 1.0f
        controller.danmuSize = 16f

        val messages = (0 until 8).map { index ->
            LiveMessage(
                type = LiveMessageType.CHAT,
                userName = "u$index",
                message = "msg-$index"
            )
        }
        messages.forEach(controller::addDanmaku)

        val canvas = Canvas()
        // First frame initializes dt and drains pending.
        controller.draw(canvas, frameTimeNanos = 16_000_000L)
        controller.draw(canvas, frameTimeNanos = 32_000_000L)
        val firstSnapshot = controller.scheduleSnapshot()
        // Replay same sequence on a second controller with same deps.
        val clock2 = FakeDanmakuClock(nowMs = 1_000L)
        val controller2 = DanmakuController(
            context = ApplicationProvider.getApplicationContext(),
            clock = clock2,
            overflowTrackPicker = FixedDanmakuOverflowTrackPicker(track = 0)
        )
        controller2.width = 1080
        controller2.height = 600
        controller2.danmuLineCount = 4
        controller2.danmuArea = 1.0f
        controller2.danmuDelayMs = 0
        controller2.danmuSpeed = 1.0f
        controller2.danmuSize = 16f
        messages.forEach(controller2::addDanmaku)
        controller2.draw(canvas, frameTimeNanos = 16_000_000L)
        controller2.draw(canvas, frameTimeNanos = 32_000_000L)
        val secondSnapshot = controller2.scheduleSnapshot()

        assertEquals(0, firstSnapshot.pendingCount)
        assertEquals(8, firstSnapshot.activeCount)
        assertEquals(firstSnapshot, secondSnapshot)
        assertEquals(listOf(0, 1, 2, 3, 0, 0, 0, 0), firstSnapshot.activeTracks)
        controller.release()
        controller2.release()
    }

    @Test
    fun delayedMessagesStayPendingUntilClockAdvances() {
        val clock = FakeDanmakuClock(nowMs = 0L)
        val controller = DanmakuController(
            context = ApplicationProvider.getApplicationContext(),
            clock = clock,
            overflowTrackPicker = FixedDanmakuOverflowTrackPicker(0)
        )
        controller.width = 720
        controller.height = 400
        controller.danmuLineCount = 2
        controller.danmuDelayMs = 500
        controller.addDanmaku(
            LiveMessage(type = LiveMessageType.CHAT, userName = "a", message = "later")
        )
        val canvas = Canvas()
        controller.draw(canvas, frameTimeNanos = 16_000_000L)
        assertEquals(1, controller.scheduleSnapshot().pendingCount)
        assertEquals(0, controller.scheduleSnapshot().activeCount)

        clock.advance(600)
        controller.draw(canvas, frameTimeNanos = 32_000_000L)
        assertEquals(0, controller.scheduleSnapshot().pendingCount)
        assertEquals(1, controller.scheduleSnapshot().activeCount)
        controller.release()
    }
}
