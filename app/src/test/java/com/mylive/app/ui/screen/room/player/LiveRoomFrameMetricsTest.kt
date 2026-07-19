package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRoomFrameMetricsTest {

    @Test
    fun recordsJankAndAverage() {
        val recorder = LiveRoomFrameMetricsRecorder(jankThresholdMs = 17L)
        recorder.onFrameDurationMs(8)
        recorder.onFrameDurationMs(12)
        recorder.onFrameDurationMs(25)
        val snap = recorder.snapshot()
        assertEquals(3, snap.frameCount)
        assertEquals(1, snap.jankFrameCount)
        assertEquals(25L, snap.maxFrameDurationMs)
        assertEquals(45L, snap.totalFrameDurationMs)
        assertEquals(15.0, snap.averageFrameDurationMs, 0.001)
        assertTrue(snap.jankRatio > 0.3)
    }

    @Test
    fun jankClassifierMatchesThreshold() {
        assertFalse(isJankFrameDuration(16))
        assertFalse(isJankFrameDuration(17))
        assertTrue(isJankFrameDuration(18))
    }

    @Test
    fun resetClearsCounters() {
        val recorder = LiveRoomFrameMetricsRecorder()
        recorder.onFrameDurationMs(30)
        recorder.reset()
        val snap = recorder.snapshot()
        assertEquals(0, snap.frameCount)
        assertEquals(0, snap.jankFrameCount)
        assertEquals(0L, snap.maxFrameDurationMs)
    }
}
