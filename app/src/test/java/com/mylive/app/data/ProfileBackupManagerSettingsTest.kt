package com.mylive.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.HistoryRepository
import com.mylive.app.data.repository.ProfileBackupManager
import com.mylive.app.data.repository.ShieldRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileBackupManagerSettingsTest {

    private lateinit var db: AppDatabase
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var backupManager: ProfileBackupManager
    private lateinit var followRepository: FollowRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsDataStore = SettingsDataStore(context)
        settingsDataStore.clearAll()
        followRepository = FollowRepository(db.followUserDao(), db.followUserTagDao())
        backupManager = ProfileBackupManager(
            settingsDataStore = settingsDataStore,
            followRepository = followRepository,
            historyRepository = HistoryRepository(db.historyDao()),
            shieldRepository = ShieldRepository(db.shieldDao())
        )
    }

    @After
    fun tearDown() = runBlocking {
        db.close()
        settingsDataStore.clearAll()
    }

    @Test
    fun exportProfileIncludesDedupeStrictModeAndRestoresIt() = runBlocking {
        settingsDataStore.setValue(SettingsDataStore.DanmuDedupeStrictMode, true)

        val backupJson = backupManager.exportProfileJson()
        val settings = JSONObject(backupJson).getJSONObject("settings")

        assertTrue(settings.getBoolean(SettingsDataStore.DanmuDedupeStrictMode.name))

        settingsDataStore.clearAll()
        backupManager.importProfileJson(backupJson)

        assertTrue(settingsDataStore.getFlow(SettingsDataStore.DanmuDedupeStrictMode, false).first())
    }

    @Test
    fun exportProfileUsesFollowUpdateRepositoryDefaultsWhenUnset() = runBlocking {
        val backupJson = backupManager.exportProfileJson()
        val settings = JSONObject(backupJson).getJSONObject("settings")

        assertTrue(settings.getBoolean(SettingsDataStore.AutoUpdateFollowEnable.name))
        assertEquals(60, settings.getInt(SettingsDataStore.AutoUpdateFollowDuration.name))
        assertEquals(8, settings.getInt(SettingsDataStore.UpdateFollowThreadCount.name))
    }

    @Test
    fun exportProfileDefaultsRoomAutoExitToNever() = runBlocking {
        val backupJson = backupManager.exportProfileJson()
        val settings = JSONObject(backupJson).getJSONObject("settings")

        assertEquals(0, settings.getInt(SettingsDataStore.RoomAutoExitDuration.name))
    }

    @Test
    fun profileRoundTripPreservesFollowShowTime() = runBlocking {
        followRepository.addFollow(
            FollowUserEntity(
                id = "douyu_200",
                roomId = "200",
                siteId = "douyu",
                userName = "streamer",
                face = "",
                addTime = 123L,
                liveStatus = 1,
                liveStartTime = 456L,
                showTime = "789"
            )
        )

        val backup = backupManager.exportProfileJson()
        followRepository.clearAllFollows()
        backupManager.importProfileJson(backup)

        val restored = followRepository.getAllFollows().first().single()
        assertEquals("789", restored.showTime)
    }
}
