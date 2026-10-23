package com.mylive.app.ui.screen.room.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackStartupTimingTest {
    @Test fun roomTimingIncludesPlatformLookupAndSourceTimingOnlyIncludesPlaybackSetup() {
        val timing = PlaybackStartupTiming()
        timing.beginRoomLoad(100)
        timing.beginSource(900, 1)
        assertNull(timing.roomFirstFrameMillis)
        assertTrue(timing.onRenderedFirstFrame(1250, 1))
        assertEquals(1150L, timing.roomFirstFrameMillis)
        assertEquals(350L, timing.sourceFirstFrameMillis)
    }

    @Test fun lateFramesFromOldMediaAndDuplicateSurfaceCallbacksDoNotChangeMeasurements() {
        val timing = PlaybackStartupTiming()
        timing.beginRoomLoad(100)
        timing.beginSource(200, 1)
        timing.beginSource(400, 2)
        assertFalse(timing.onRenderedFirstFrame(450, 1))
        assertNull(timing.sourceFirstFrameMillis)
        assertTrue(timing.onRenderedFirstFrame(600, 2))
        assertFalse(timing.onRenderedFirstFrame(800, 2))
        assertEquals(200L, timing.sourceFirstFrameMillis)
        assertEquals(500L, timing.roomFirstFrameMillis)
    }

    @Test fun retriesBeforeAnyFrameRemainPartOfTheRoomStartupTime() {
        val timing = PlaybackStartupTiming()
        timing.beginRoomLoad(100)
        timing.beginSource(300, 1)
        timing.invalidateSource()
        timing.beginSource(1200, 2)
        assertTrue(timing.onRenderedFirstFrame(1500, 2))
        assertEquals(1400L, timing.roomFirstFrameMillis)
        assertEquals(300L, timing.sourceFirstFrameMillis)
        timing.beginSource(2000, 3)
        timing.onRenderedFirstFrame(2200, 3)
        assertEquals(1400L, timing.roomFirstFrameMillis)
        assertEquals(200L, timing.sourceFirstFrameMillis)
    }

    @Test fun enteringAnotherRoomInvalidatesEveryEarlierFrame() {
        val timing = PlaybackStartupTiming()
        timing.beginRoomLoad(100)
        timing.beginSource(300, 1)
        timing.onRenderedFirstFrame(500, 1)
        timing.beginRoomLoad(900)
        assertFalse(timing.onRenderedFirstFrame(1000, 1))
        assertNull(timing.roomFirstFrameMillis)
        timing.beginSource(1100, 2)
        timing.onRenderedFirstFrame(1250, 2)
        assertEquals(350L, timing.roomFirstFrameMillis)
    }

    @Test fun aFrameTimestampEarlierThanSourcePreparationIsNotCounted() {
        val timing = PlaybackStartupTiming()
        timing.beginRoomLoad(100)
        timing.beginSource(400, 1)
        assertFalse(timing.onRenderedFirstFrame(399, 1))
        assertNull(timing.sourceFirstFrameMillis)
        assertTrue(timing.onRenderedFirstFrame(400, 1))
        assertEquals(0L, timing.sourceFirstFrameMillis)
    }
}
