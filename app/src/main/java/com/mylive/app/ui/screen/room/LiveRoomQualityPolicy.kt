package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.LivePlayQuality
import java.util.concurrent.atomic.AtomicLong

internal class LatestPlaybackRequestGate {
    private val generation = AtomicLong(0L)

    fun begin(): Long = generation.incrementAndGet()

    fun isCurrent(requestGeneration: Long): Boolean = generation.get() == requestGeneration
}

internal fun selectLiveRoomPreferredQualityLevel(
    defaultQualityLevel: Int,
    cellularQualityLevel: Int,
    isCellularNetwork: Boolean
): Int = if (isCellularNetwork) cellularQualityLevel else defaultQualityLevel

internal fun updatedQualityIndexForResolvedPlayback(
    qualities: List<LivePlayQuality>,
    currentIndex: Int,
    requestedQuality: LivePlayQuality,
    actualQuality: LivePlayQuality?
): Int {
    if (actualQuality == null || qualities.getOrNull(currentIndex) != requestedQuality) {
        return currentIndex
    }
    return qualities.indexOfFirst { it.data == actualQuality.data }
        .takeIf { it >= 0 }
        ?: currentIndex
}
