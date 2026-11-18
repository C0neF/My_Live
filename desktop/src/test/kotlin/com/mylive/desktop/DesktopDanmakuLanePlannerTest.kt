package com.mylive.desktop

import com.mylive.app.core.model.LiveMessageDanmakuPosition.*
import org.junit.Assert.*
import org.junit.Test

class DesktopDanmakuLanePlannerTest {
    private fun planner(vararg occupants: DesktopDanmakuOccupant, rows: Int = 1) =
        DesktopDanmakuLanePlanner(4, 3, rows, 500f, 24f, occupants.toList())

    @Test fun aLaneCanBeReusedAfterEveryPreviousTailClearsTheEntranceWithAGap() {
        assertNull(planner(DesktopDanmakuOccupant(0, true, 480f)).reserve(SCROLL))
        assertEquals(0, planner(DesktopDanmakuOccupant(0, true, 476f)).reserve(SCROLL))
        assertNull(planner(DesktopDanmakuOccupant(0, true, 200f), DesktopDanmakuOccupant(0, true, 490f)).reserve(SCROLL))
    }

    @Test fun rowLimitCountsDistinctLanesAndSameBatchReservationsCannotOverlap() {
        val reuse = planner(DesktopDanmakuOccupant(0, true, 100f), DesktopDanmakuOccupant(0, true, 200f))
        assertEquals(0, reuse.reserve(SCROLL))
        assertNull(reuse.reserve(SCROLL))
        val empty = planner(rows = 2)
        assertEquals(0, empty.reserve(SCROLL))
        assertEquals(1, empty.reserve(SCROLL))
        assertNull(empty.reserve(SCROLL))
    }

    @Test fun fixedMessagesOccupyTheirLaneExclusivelyAndBottomStartsAtTheBottom() {
        assertNull(planner(DesktopDanmakuOccupant(0, false, 50f)).reserve(SCROLL))
        assertNull(planner(DesktopDanmakuOccupant(0, true, 50f)).reserve(TOP))
        val fixed = planner()
        assertEquals(3, fixed.reserve(BOTTOM))
        assertNull(fixed.reserve(SCROLL))
    }

    @Test fun unmeasuredTextCannotReleaseALaneAndScrollingRespectsTheConfiguredArea() {
        assertNull(planner(DesktopDanmakuOccupant(0, true, Float.POSITIVE_INFINITY)).reserve(SCROLL))
        val area = DesktopDanmakuLanePlanner(6, 2, 6, 500f, 24f, emptyList())
        assertEquals(0, area.reserve(SCROLL))
        assertEquals(1, area.reserve(SCROLL))
        assertNull(area.reserve(SCROLL))
        assertEquals(5, area.reserve(BOTTOM))
    }

    @Test fun differentTextLengthsMoveAtTheSameSpeedWithoutCatchingEachOther() {
        val shortDuration = desktopDanmakuDurationMillis(500f, 50f, 4f)
        val longDuration = desktopDanmakuDurationMillis(500f, 450f, 4f)
        assertEquals(4400, shortDuration)
        assertEquals(7600, longDuration)
        assertEquals(550f / shortDuration, 950f / longDuration, .0001f)
    }
}
