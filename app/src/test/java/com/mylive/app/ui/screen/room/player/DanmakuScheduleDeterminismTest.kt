package com.mylive.app.ui.screen.room.player

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageDanmakuPosition
import com.mylive.app.core.model.LiveMessageSpan
import com.mylive.app.core.model.LiveMessageType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class RenderTestClock(var now: Long = 0L) : DanmakuClock {
    override fun uptimeMillis() = now
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DanmakuScheduleDeterminismTest {
    private fun controller(clock: RenderTestClock) = DanmakuController(ApplicationProvider.getApplicationContext(), clock,
        // Robolectric's legacy Paint reports character count, independent of textSize.
        measureText = { paint, text -> text.length * paint.textSize * 0.5f }).apply {
        width = 1080; height = 800; danmuSize = 16f; danmuArea = 1f; danmuLineCount = 4
    }
    private fun message(text: String, position: LiveMessageDanmakuPosition = LiveMessageDanmakuPosition.SCROLL) =
        LiveMessage(LiveMessageType.CHAT, "viewer", text, danmakuPosition = position)
    private fun draw(controller: DanmakuController, clock: RenderTestClock, at: Long) {
        clock.now = at
        controller.draw(Canvas(), (at + 1) * 1_000_000L)
    }

    @Test fun fullTracksWaitInsteadOfOverlappingAndOldQueuedMessagesExpire() {
        val clock = RenderTestClock()
        val c = controller(clock)
        try {
            repeat(8) { c.addDanmaku(message("message $it")) }
            draw(c, clock, 0)
            assertEquals(listOf(0, 1, 2, 3), c.scheduleSnapshot().activeTracks)
            assertEquals(4, c.scheduleSnapshot().pendingCount)
            draw(c, clock, 3_000)
            assertEquals(0, c.scheduleSnapshot().pendingCount)
            assertEquals(4L, c.scheduleSnapshot().droppedCount)
        } finally { c.release() }
    }

    @Test fun futureMessagesRequestOneDelayedWakeupAndDelayReductionAppliesToQueuedMessages() {
        val clock = RenderTestClock()
        val c = controller(clock)
        try {
            assertFalse(c.needsFrame())
            c.danmuDelayMs = 5_000
            c.addDanmaku(message("delayed"))
            assertEquals(5_000L, c.nextFrameDelayMillis())
            draw(c, clock, 1_000)
            assertEquals(0, c.scheduleSnapshot().activeCount)
            assertEquals(4_000L, c.nextFrameDelayMillis())
            c.danmuDelayMs = 0
            c.addDanmaku(message("new"))
            assertEquals(0L, c.nextFrameDelayMillis())
            draw(c, clock, 1_016)
            assertEquals(2, c.scheduleSnapshot().activeCount)
            assertEquals(0, c.scheduleSnapshot().pendingCount)
        } finally { c.release() }
    }

    @Test fun configurationChangesRemeasureAndRespectTheNewTrackLimit() {
        val clock = RenderTestClock()
        val c = controller(clock)
        try {
            repeat(4) { c.addDanmaku(message("layout $it")) }
            draw(c, clock, 0)
            val oldWidth = c.scheduleSnapshot().activeItems.first().width
            c.danmuSize = 24f
            c.danmuLineCount = 1
            c.danmuTopMargin = 12f
            draw(c, clock, 16)
            val remaining = c.scheduleSnapshot().activeItems
            assertEquals(1, remaining.size)
            assertTrue(remaining.single().width > oldWidth)
            assertEquals(0, remaining.single().track)
            val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
            assertEquals((12f + 24f * 1.5f) * density, remaining.single().y, 0.01f)
        } finally { c.release() }
    }

    @Test fun viewportResizePreservesTravelProgressAndRecomputesSpeed() {
        val clock = RenderTestClock()
        val c = controller(clock)
        try {
            c.addDanmaku(message("resize"))
            draw(c, clock, 0)
            draw(c, clock, 1_000)
            val before = c.scheduleSnapshot().activeItems.single()
            val progress = (1080f - before.x) / (1080f + before.width)
            c.updateViewport(720, 400)
            draw(c, clock, 1_016)
            val after = c.scheduleSnapshot().activeItems.single()
            assertEquals(progress, (720f - after.x) / (720f + after.width), 0.005f)
            assertEquals((720f + after.width) / 8000f, after.speed, 0.001f)
        } finally { c.release() }
    }

    @Test fun fixedAndScrollingDanmakuDoNotShareAVerticalBand() {
        val clock = RenderTestClock()
        val c = controller(clock)
        try {
            c.addDanmaku(message("scroll"))
            c.addDanmaku(message("top", LiveMessageDanmakuPosition.TOP))
            draw(c, clock, 0)
            val items = c.scheduleSnapshot().activeItems
            assertEquals(2, items.size)
            assertNotEquals(items[0].y, items[1].y)
        } finally { c.release() }
    }

    @Test fun lateWideEmojiRemeasuresAndRecentersFixedText() {
        val clock = RenderTestClock()
        val c = controller(clock)
        try {
            c.addDanmaku(message("", LiveMessageDanmakuPosition.TOP).copy(
                spans = listOf(LiveMessageSpan.Image("emoji-test://late"))))
            draw(c, clock, 0)
            val before = c.scheduleSnapshot().activeItems.single()
            val field = DanmakuController::class.java.getDeclaredField("activeDanmakus").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val active = field.get(c) as List<DanmakuItem>
            (active.single().parts.single() as DanmakuPart.Image).bitmap = Bitmap.createBitmap(200, 50, Bitmap.Config.ARGB_8888)
            draw(c, clock, 16)
            val after = c.scheduleSnapshot().activeItems.single()
            assertEquals(before.width * 4f, after.width, 0.01f)
            assertEquals((1080f - after.width) / 2f, after.x, 0.01f)
        } finally { c.release() }
    }

    @Test fun perFrameAdmissionAndActiveCountsStayBoundedUnderSustainedBursts() {
        val clock = RenderTestClock()
        val c = controller(clock).apply { height = 4000; danmuLineCount = 32 }
        try {
            repeat(60) { step ->
                clock.now = step * 100L
                repeat(24) { c.addDanmaku(message("burst")) }
                val before = c.scheduleSnapshot().activeCount
                draw(c, clock, clock.now)
                val snapshot = c.scheduleSnapshot()
                assertTrue(snapshot.activeCount - before <= DanmakuController.MAX_ADMISSIONS_PER_FRAME)
                assertTrue(snapshot.activeCount <= DanmakuController.MAX_ACTIVE_DANMAKU)
                assertTrue(snapshot.pendingCount <= DanmakuController.MAX_PENDING_DANMAKU)
            }
            assertTrue(c.scheduleSnapshot().droppedCount > 0)
        } finally { c.release() }
    }

    @Test fun clearingTheControllerStopsAllFrameRequests() {
        val clock = RenderTestClock()
        val c = controller(clock)
        try {
            c.addDanmaku(message("hello"))
            draw(c, clock, 0)
            c.clear()
            draw(c, clock, 16)
            assertFalse(c.needsFrame())
            assertNull(c.nextFrameDelayMillis())
        } finally { c.release() }
    }
    @Test fun stableFramesReuseMeasuredTextUntilTheFontChanges() {
        var measures = 0
        val clock = RenderTestClock()
        val c = DanmakuController(ApplicationProvider.getApplicationContext(), clock,
            measureText = { paint, text -> measures++; text.length * paint.textSize }).apply {
            width = 1080; height = 800
        }
        try {
            c.addDanmaku(message("cached"))
            repeat(12) { draw(c, clock, it * 16L) }
            assertEquals(1, measures)
            c.danmuSize = 24f
            draw(c, clock, 208)
            assertEquals(2, measures)
        } finally { c.release() }
    }
    @Test fun rapidResizesAndConfigurationChangesKeepAllVisibleItemsSeparated() {
        val clock = RenderTestClock()
        val c = controller(clock)
        val random = java.util.Random(7)
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        try {
            repeat(240) { frame ->
                clock.now = frame * 40L
                if (frame % 20 == 0) {
                    c.updateViewport(if (frame % 40 == 0) 1080 else 720, if (frame % 40 == 0) 500 else 800)
                    c.danmuSize = if (frame % 60 == 0) 24f else 16f
                    c.danmuLineCount = 2 + random.nextInt(7)
                    c.danmuTopMargin = 8f
                    c.danmuBottomMargin = 12f
                    c.danmuSpeed = if (frame % 80 == 0) 1.5f else 0.8f
                }
                repeat(8) {
                    val position = when (random.nextInt(10)) {
                        0 -> LiveMessageDanmakuPosition.TOP
                        1 -> LiveMessageDanmakuPosition.BOTTOM
                        else -> LiveMessageDanmakuPosition.SCROLL
                    }
                    c.addDanmaku(message("m".repeat(1 + random.nextInt(70)), position))
                }
                draw(c, clock, clock.now)
                val items = c.scheduleSnapshot().activeItems
                assertTrue(items.size <= DanmakuController.MAX_ACTIVE_DANMAKU)
                items.forEach {
                    assertTrue(it.y - c.danmuSize * density >= c.danmuTopMargin * density - 0.1f)
                    assertTrue(it.y <= c.height - c.danmuBottomMargin * density + 0.1f)
                }
                for (i in items.indices) for (j in i + 1 until items.size) {
                    val a = items[i]
                    val b = items[j]
                    if (kotlin.math.abs(a.y - b.y) < c.danmuSize * density * 1.5f - 0.1f) {
                        assertTrue("Overlapping messages at frame $frame: $a / $b",
                            a.x + a.width <= b.x + 0.1f || b.x + b.width <= a.x + 0.1f)
                    }
                }
            }
        } finally { c.release() }
    }
}
