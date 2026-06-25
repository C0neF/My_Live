package com.mylive.app.ui.screen.room.player

import com.mylive.app.core.model.LiveMessageDanmakuPosition
import kotlin.math.abs

internal data class DanmakuTrackLayout(
    val trackCount: Int,
    val topOffsetPx: Float,
    val bottomOffsetPx: Float,
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
        return DanmakuTrackLayout(
            trackCount = 0,
            topOffsetPx = topOffsetPx,
            bottomOffsetPx = bottomOffsetPx,
            trackHeightPx = trackHeight
        )
    }

    val areaLimit = ((usableHeight * area.coerceIn(0f, 1f)) / trackHeight).toInt()
    val trackCount = requestedLineCount
        .coerceAtMost(areaLimit)
        .coerceIn(0, maxTracks)

    return DanmakuTrackLayout(
        trackCount = trackCount,
        topOffsetPx = topOffsetPx,
        bottomOffsetPx = bottomOffsetPx,
        trackHeightPx = trackHeight
    )
}

internal fun shouldDisplayDanmakuPosition(
    position: LiveMessageDanmakuPosition,
    hideScroll: Boolean,
    hideTop: Boolean,
    hideBottom: Boolean
): Boolean {
    return when (position) {
        LiveMessageDanmakuPosition.SCROLL -> !hideScroll
        LiveMessageDanmakuPosition.TOP -> !hideTop
        LiveMessageDanmakuPosition.BOTTOM -> !hideBottom
    }
}

internal fun resolveFixedDanmakuBaseline(
    position: LiveMessageDanmakuPosition,
    track: Int,
    viewportHeightPx: Int,
    fontHeightPx: Float,
    layout: DanmakuTrackLayout
): Float {
    val safeTrack = track.coerceAtLeast(0)
    return when (position) {
        LiveMessageDanmakuPosition.BOTTOM -> {
            viewportHeightPx -
                layout.bottomOffsetPx -
                safeTrack * layout.trackHeightPx -
                (layout.trackHeightPx - fontHeightPx)
        }
        LiveMessageDanmakuPosition.TOP,
        LiveMessageDanmakuPosition.SCROLL -> {
            layout.topOffsetPx + (safeTrack + 1) * layout.trackHeightPx
        }
    }
}

internal fun isFixedDanmakuTrackAvailable(
    candidateBaselinePx: Float,
    occupiedBaselinesPx: List<Float>,
    trackHeightPx: Float
): Boolean {
    return occupiedBaselinesPx.none { occupied ->
        abs(candidateBaselinePx - occupied) < trackHeightPx
    }
}
