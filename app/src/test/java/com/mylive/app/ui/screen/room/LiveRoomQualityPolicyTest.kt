package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.core.model.PlayQualityData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveRoomQualityPolicyTest {

    @Test
    fun cellularNetworkUsesCellularQualityPreference() {
        assertEquals(
            5,
            selectLiveRoomPreferredQualityLevel(
                defaultQualityLevel = 1,
                cellularQualityLevel = 5,
                isCellularNetwork = true
            )
        )
    }

    @Test
    fun nonCellularNetworkUsesDefaultQualityPreference() {
        assertEquals(
            1,
            selectLiveRoomPreferredQualityLevel(
                defaultQualityLevel = 1,
                cellularQualityLevel = 5,
                isCellularNetwork = false
            )
        )
    }

    @Test
    fun liveRoomViewModelUsesCellularQualityPreferenceWhenLoadingQualities() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomViewModel.kt").readText()

        assertTrue(source.contains("settingsRepository.qualityLevelCellular.first()"))
        assertTrue(source.contains("selectLiveRoomPreferredQualityLevel("))
        assertTrue(source.contains("isCellularNetworkActive()"))
    }

    @Test
    fun resolvedPlaybackQualityReplacesRequestedQualityIndex() {
        val original = LivePlayQuality("原画", PlayQualityData.BiliBili(10000))
        val ultra = LivePlayQuality("超清", PlayQualityData.BiliBili(250))
        val qualities = listOf(original, ultra)

        assertEquals(
            1,
            updatedQualityIndexForResolvedPlayback(
                qualities = qualities,
                currentIndex = 0,
                requestedQuality = original,
                actualQuality = ultra
            )
        )
    }

    @Test
    fun stalePlaybackResponseDoesNotReplaceNewerQualitySelection() {
        val original = LivePlayQuality("原画", PlayQualityData.BiliBili(10000))
        val ultra = LivePlayQuality("超清", PlayQualityData.BiliBili(250))
        val qualities = listOf(original, ultra)

        assertEquals(
            1,
            updatedQualityIndexForResolvedPlayback(
                qualities = qualities,
                currentIndex = 1,
                requestedQuality = original,
                actualQuality = ultra
            )
        )
    }
}
