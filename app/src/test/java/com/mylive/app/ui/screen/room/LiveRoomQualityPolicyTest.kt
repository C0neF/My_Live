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
    fun unavailableQualityPrefersNearestLevelThatIsNotHigher() {
        val qualities = listOf(
            LivePlayQuality("蓝光30M", PlayQualityData.Huya(0, 0)),
            LivePlayQuality("超清", PlayQualityData.Huya(0, 2000)),
            LivePlayQuality("流畅", PlayQualityData.Huya(0, 500))
        )

        assertEquals(2, selectPreferredQualityIndex(qualities, preferredLevel = 3))
    }

    @Test
    fun unavailableQualityUsesNearestHigherLevelWhenNoLowerLevelExists() {
        val qualities = listOf(
            LivePlayQuality("原画1080P30", PlayQualityData.Douyu(0, emptyList())),
            LivePlayQuality("高清", PlayQualityData.Douyu(2, emptyList()))
        )

        assertEquals(1, selectPreferredQualityIndex(qualities, preferredLevel = 4))
    }

    @Test
    fun exactQualityMatchStillTakesPriority() {
        val qualities = listOf(
            LivePlayQuality("蓝光30M", PlayQualityData.Huya(0, 0)),
            LivePlayQuality("超清", PlayQualityData.Huya(0, 2000)),
            LivePlayQuality("流畅", PlayQualityData.Huya(0, 500))
        )

        assertEquals(1, selectPreferredQualityIndex(qualities, preferredLevel = 2))
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
