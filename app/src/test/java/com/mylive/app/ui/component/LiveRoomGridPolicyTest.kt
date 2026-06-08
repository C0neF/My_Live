package com.mylive.app.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRoomGridPolicyTest {

    @Test
    fun phonePortraitWidthsKeepTwoLiveRoomColumns() {
        assertEquals(
            2,
            liveRoomGridColumnCount(
                availableWidthDp = 320,
                horizontalPaddingDp = 16,
                horizontalGapDp = 12
            )
        )
        assertEquals(
            2,
            liveRoomGridColumnCount(
                availableWidthDp = 360,
                horizontalPaddingDp = 16,
                horizontalGapDp = 12
            )
        )
    }

    @Test
    fun liveRoomMinCellWidthFitsTwoColumnsOnCompactPhones() {
        assertTrue(LIVE_ROOM_GRID_MIN_CELL_WIDTH_DP <= 136)
    }
}
