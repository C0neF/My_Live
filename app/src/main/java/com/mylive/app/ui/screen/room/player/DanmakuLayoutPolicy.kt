package com.mylive.app.ui.screen.room.player

internal data class DanmakuTrackLayout(
    val trackCount: Int,
    val topOffsetPx: Float,
    val trackHeightPx: Float
)

internal fun resolveDanmakuTrackLayout(
    viewportHeightPx: Int,
    density: Float,
    fontSizeSp: Float,
    area: Float,
    requestedLineCount: Int,
    topMarginDp: Float,
    bottomMarginDp: Float,
    maxTracks: Int = 32
): DanmakuTrackLayout {
    val safeDensity = density.coerceAtLeast(0.1f)
    val topOffsetPx = topMarginDp.coerceAtLeast(0f) * safeDensity
    val bottomOffsetPx = bottomMarginDp.coerceAtLeast(0f) * safeDensity
    val usableHeight = (viewportHeightPx - topOffsetPx - bottomOffsetPx).coerceAtLeast(0f)
    val fontHeight = fontSizeSp.coerceAtLeast(1f) * safeDensity
    val trackHeight = fontHeight * 1.5f

    if (requestedLineCount <= 0 || usableHeight <= 0f || trackHeight <= 0f) {
        return DanmakuTrackLayout(trackCount = 0, topOffsetPx = topOffsetPx, trackHeightPx = trackHeight)
    }

    val areaLimit = ((usableHeight * area.coerceIn(0f, 1f)) / trackHeight).toInt()
    val trackCount = requestedLineCount
        .coerceAtMost(areaLimit)
        .coerceIn(0, maxTracks)

    return DanmakuTrackLayout(
        trackCount = trackCount,
        topOffsetPx = topOffsetPx,
        trackHeightPx = trackHeight
    )
}
