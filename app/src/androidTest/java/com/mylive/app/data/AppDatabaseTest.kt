package com.mylive.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.dao.FollowUserDao
import com.mylive.app.data.local.dao.HistoryDao
import com.mylive.app.data.local.dao.ShieldDao
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.data.local.entity.ShieldPresetEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var followUserDao: FollowUserDao
    private lateinit var historyDao: HistoryDao
    private lateinit var shieldDao: ShieldDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        followUserDao = db.followUserDao()
        historyDao = db.historyDao()
        shieldDao = db.shieldDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun writeAndReadFollowUser() = runBlocking {
        val user = FollowUserEntity(
            id = "bilibili_123456",
            roomId = "123456",
            siteId = "bilibili",
            userName = "Test Anchor",
            face = "http://face.com/1.png",
            addTime = System.currentTimeMillis(),
            isSpecialFollow = true,
            liveStatus = 1
        )
        followUserDao.insert(user)

        val retrieved = followUserDao.getBySiteAndRoom("bilibili", "123456")
        assertNotNull(retrieved)
        assertEquals("Test Anchor", retrieved?.userName)
        assertTrue(retrieved?.isSpecialFollow == true)

        // Test update status
        followUserDao.updateLiveStatus("bilibili_123456", 2, null)
        val updated = followUserDao.getBySiteAndRoom("bilibili", "123456")
        assertEquals(2, updated?.liveStatus)
        assertNull(updated?.liveStartTime)

        // Test list
        val list = followUserDao.getAll().first()
        assertEquals(1, list.size)
        assertEquals("bilibili_123456", list[0].id)

        // Test delete
        followUserDao.delete("bilibili_123456")
        val emptyList = followUserDao.getAll().first()
        assertEquals(0, emptyList.size)
    }

    @Test
    fun writeAndReadHistory() = runBlocking {
        val history = HistoryEntity(
            id = "douyu_78910",
            roomId = "78910",
            siteId = "douyu",
            userName = "Game Room",
            face = "http://face.com/2.png",
            updateTime = System.currentTimeMillis()
        )
        historyDao.insert(history)

        val retrieved = historyDao.getById("douyu_78910")
        assertNotNull(retrieved)
        assertEquals("Game Room", retrieved?.userName)

        val list = historyDao.getAll().first()
        assertEquals(1, list.size)

        historyDao.delete("douyu_78910")
        val emptyList = historyDao.getAll().first()
        assertEquals(0, emptyList.size)
    }

    @Test
    fun writeAndReadShield() = runBlocking {
        val shield = ShieldEntity(
            id = 0, // autoGenerate
            value = "keyword:广告"
        )
        shieldDao.insertShield(shield)

        val list = shieldDao.getAllShields().first()
        assertEquals(1, list.size)
        assertTrue(list[0].value.startsWith("keyword:"))

        // Test preset
        val preset = ShieldPresetEntity(
            name = "Default Preset",
            value = "[\"keyword:广告\", \"keyword:测试\"]"
        )
        shieldDao.insertPreset(preset)

        val presets = shieldDao.getAllPresets().first()
        assertEquals(1, presets.size)
        assertEquals("Default Preset", presets[0].name)

        shieldDao.deletePreset("Default Preset")
        val emptyPresets = shieldDao.getAllPresets().first()
        assertEquals(0, emptyPresets.size)
    }
}
