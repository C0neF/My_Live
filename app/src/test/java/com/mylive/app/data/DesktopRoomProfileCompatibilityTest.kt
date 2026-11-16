package com.mylive.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.repository.ProfileBackupManager
import com.mylive.app.testing.ReviewRoomFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DesktopRoomProfileCompatibilityTest : ReviewRoomFixture() {
    @Test fun fullWindowsBackupRestoresGroupsNotesShieldsAndSupportedSettings() = runBlocking {
        val settings = SettingsDataStore(ApplicationProvider.getApplicationContext<Context>())
        settings.clearAll()
        try {
            val manager = ProfileBackupManager(settings, follows, history, shields, database)
            val path = "desktop/src/test/resources/android-compatible-full-profile.json"
            manager.importProfileJson(listOf(File("../$path"), File(path)).first { it.isFile }.readText())
            assertEquals("douyu_9999", follows.getAllFollows().first().single().id)
            assertEquals(true, follows.getAllFollows().first().single().isSpecialFollow)
            assertEquals(setOf("Games", "Favorites"), follows.getAllTags().first().map { it.tag }.toSet())
            assertEquals(setOf("keyword:blocked-keyword", "user:douyu:blocked-user"), shields.getAllShields().first().map { it.value }.toSet())
            assertEquals(1, settings.getFlow(SettingsDataStore.ThemeMode, 0).first())
            assertEquals(false, settings.getFlow(SettingsDataStore.HardwareDecode, true).first())
            assertEquals(false, settings.getFlow(SettingsDataStore.DanmuEnable, true).first())
            assertEquals("Contract note", org.json.JSONObject(settings.getFlow(SettingsDataStore.UserRemarks, "{}").first()).getString("douyu::Contract Anchor"))
        } finally { settings.clearAll() }
    }
    @Test fun desktopRoomExportContractRestoresIntoAndroidRoomWithoutChangingSettings() = runBlocking {
        val settings = SettingsDataStore(ApplicationProvider.getApplicationContext<Context>())
        settings.clearAll()
        try {
            settings.setValue(SettingsDataStore.ThemeMode, 2)
            val manager = ProfileBackupManager(settings, follows, history, shields, database)
            val path = "desktop/src/test/resources/android-compatible-room-profile.json"
            val fixture = listOf(File("../$path"), File(path)).first { it.isFile }.readText()
            manager.importProfileJson(fixture)
            assertEquals(setOf("douyu_9999", "huya_660000"), follows.getAllFollows().first().map { it.id }.toSet())
            val restored = history.getAllHistory().first().single()
            assertEquals("douyu_9999", restored.id)
            assertEquals(400L, restored.updateTime)
            assertEquals(2, settings.getFlow(SettingsDataStore.ThemeMode, 0).first())
        } finally { settings.clearAll() }
    }
}
