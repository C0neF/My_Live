package com.mylive.app.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.repository.LiveRoomPreferences
import com.mylive.app.data.repository.toLiveRoomPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRoomPreferencesTest {

    @Test
    fun defaultsMatchExistingLiveRoomBehavior() {
        val settings = toLiveRoomPreferences(mutablePreferencesOf())

        assertEquals(LiveRoomPreferences(), settings)
        assertTrue(settings.danmuEnable)
        assertTrue(settings.hardwareDecode)
        assertFalse(settings.allowBackgroundPlayback)
        assertEquals(16.0, settings.danmuSize, 0.0)
    }

    @Test
    fun snapshotReadsAllPerformanceCriticalPlaybackValues() {
        val settings = toLiveRoomPreferences(
            mutablePreferencesOf(
                SettingsDataStore.DanmuEnable to false,
                SettingsDataStore.DanmuSize to 22.0,
                SettingsDataStore.HardwareDecode to false,
                SettingsDataStore.PlayerForceHttps to true,
                SettingsDataStore.LiveRoomQuickAccessEnabled to false,
                SettingsDataStore.RoomAutoExitDuration to 30
            )
        )

        assertFalse(settings.danmuEnable)
        assertEquals(22.0, settings.danmuSize, 0.0)
        assertFalse(settings.hardwareDecode)
        assertTrue(settings.playerForceHttps)
        assertFalse(settings.liveRoomQuickAccessEnabled)
        assertEquals(30, settings.roomAutoExitDuration)
    }
}
