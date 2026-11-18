package com.mylive.desktop

import com.mylive.app.core.model.LiveMessageDanmakuPosition
import kotlin.math.roundToInt

internal const val MAX_DESKTOP_DANMAKU = 120

internal data class DesktopDanmakuOccupant(val lane: Int, val scrolling: Boolean, val rightEdge: Float)

/** One planner per incoming batch. New reservations remain occupied until their text is measured. */
internal class DesktopDanmakuLanePlanner(
    private val slots: Int,
    private val scrollSlots: Int,
    private val maxRows: Int,
    private val viewportWidth: Float,
    private val gap: Float,
    occupied: List<DesktopDanmakuOccupant>
) {
    private val tracks = occupied.groupBy { it.lane }.mapValuesTo(mutableMapOf()) { it.value.toMutableList() }

    fun reserve(position: LiveMessageDanmakuPosition): Int? {
        if (viewportWidth <= 0 || !viewportWidth.isFinite()) return null
        val scrolling = position == LiveMessageDanmakuPosition.SCROLL
        val candidates = if (position == LiveMessageDanmakuPosition.BOTTOM) (slots - 1 downTo 0) else (0 until scrollSlots)
        for (lane in candidates) {
            val previous = tracks[lane].orEmpty()
            val available = if (previous.isEmpty()) tracks.size < maxRows
                else scrolling && previous.all { it.scrolling && it.rightEdge <= viewportWidth - gap }
            if (available) {
                tracks.getOrPut(lane) { mutableListOf() }.add(DesktopDanmakuOccupant(lane, scrolling, Float.POSITIVE_INFINITY))
                return lane
            }
        }
        return null
    }
}

/** A shared pixel velocity prevents long messages from catching shorter messages in the same lane. */
internal fun desktopDanmakuDurationMillis(viewportWidth: Float, textWidth: Float, travelSeconds: Float): Int =
    (((viewportWidth.toDouble() + textWidth) / viewportWidth) * travelSeconds * 1000)
        .coerceIn(1.0, Int.MAX_VALUE.toDouble()).roundToInt()
