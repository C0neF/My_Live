package com.mylive.app.ui.screen.room

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
}
