package com.mylive.app.ui.component

import androidx.compose.ui.unit.dp

internal const val LIVE_ROOM_GRID_MIN_CELL_WIDTH_DP = 136

internal val LiveRoomGridMinCellWidth = LIVE_ROOM_GRID_MIN_CELL_WIDTH_DP.dp

internal fun liveRoomGridColumnCount(
    availableWidthDp: Int,
    horizontalPaddingDp: Int,
    horizontalGapDp: Int,
    minCellWidthDp: Int = LIVE_ROOM_GRID_MIN_CELL_WIDTH_DP
): Int {
    val contentWidth = (availableWidthDp - horizontalPaddingDp * 2).coerceAtLeast(0)
    if (contentWidth <= 0) return 1
    return ((contentWidth + horizontalGapDp) / (minCellWidthDp + horizontalGapDp))
        .coerceAtLeast(1)
}
