package com.mylive.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.HistoryRepository
import com.mylive.app.data.repository.ProfileBackupManager
import com.mylive.app.data.repository.ShieldRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileBackupManagerTest {

    private lateinit var db: AppDatabase
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var followRepository: FollowRepository
    private lateinit var historyRepository: HistoryRepository
    private lateinit var shieldRepository: ShieldRepository
    private lateinit var backupManager: ProfileBackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // In-memory Database
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            
        settingsDataStore = SettingsDataStore(context)
        
        followRepository = FollowRepository(db.followUserDao(), db.followUserTagDao())
        historyRepository = HistoryRepository(db.historyDao())
        shieldRepository = ShieldRepository(db.shieldDao())
        
        backupManager = ProfileBackupManager(
            settingsDataStore = settingsDataStore,
            followRepository = followRepository,
            historyRepository = historyRepository,
            shieldRepository = shieldRepository
        )
    }

    @After
    fun tearDown() = runBlocking {
        db.close()
        settingsDataStore.clearAll()
    }

    @Test
    fun testBackupAndRestore() = runBlocking {
        // 1. Seed settings
        settingsDataStore.setValue(SettingsDataStore.DanmuEnable, false)
        settingsDataStore.setValue(SettingsDataStore.DanmuSize, 24.5)
        
        // 2. Seed database
        val follow = FollowUserEntity(
            id = "huya_1234567",
            roomId = "1234567",
            siteId = "huya",
            userName = "Huya Streamer",
            face = "http://face.com/huya.png",
            addTime = System.currentTimeMillis()
        )
        followRepository.addFollow(follow)
        
        val history = HistoryEntity(
            id = "douyin_myroom",
            roomId = "myroom",
            siteId = "douyin",
            userName = "Douyin Streamer",
            face = "http://face.com/douyin.png",
            updateTime = System.currentTimeMillis()
        )
        historyRepository.addHistory(history)
        
        val shield = ShieldEntity(
            id = 0,
            value = "keyword:不可描述"
        )
        shieldRepository.addShield(shield)
        
        // 3. Export profile
        val backupJson = backupManager.exportProfileJson()
        assertTrue(backupJson.isNotEmpty())
        assertTrue(backupJson.contains("huya_1234567"))
        assertTrue(backupJson.contains("douyin_myroom"))
        assertTrue(backupJson.contains("keyword:不可描述"))
        
        // 4. Clear all local data
        settingsDataStore.clearAll()
        db.followUserDao().deleteAll()
        db.historyDao().deleteAll()
        db.shieldDao().clearAllKeywords()
        
        // Assert cleared
        assertTrue(settingsDataStore.getFlow(SettingsDataStore.DanmuEnable, true).first())
        assertEquals(0, followRepository.getAllFollows().first().size)
        assertEquals(0, historyRepository.getAllHistory().first().size)
        assertEquals(0, shieldRepository.getAllShields().first().size)
        
        // 5. Restore profile
        backupManager.importProfileJson(backupJson)
        
        // 6. Assert restored data matches seeded data
        val restoredDanmuEnable = settingsDataStore.getFlow(SettingsDataStore.DanmuEnable, true).first()
        assertFalse(restoredDanmuEnable)
        
        val restoredDanmuSize = settingsDataStore.getFlow(SettingsDataStore.DanmuSize, 16.0).first()
        assertEquals(24.5, restoredDanmuSize, 0.001)
        
        val follows = followRepository.getAllFollows().first()
        assertEquals(1, follows.size)
        assertEquals("Huya Streamer", follows[0].userName)
        
        val histories = historyRepository.getAllHistory().first()
        assertEquals(1, histories.size)
        assertEquals("Douyin Streamer", histories[0].userName)
        
        val shields = shieldRepository.getAllShields().first()
        assertEquals(1, shields.size)
        assertEquals("keyword:不可描述", shields[0].value)
    }
}
