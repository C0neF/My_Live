package com.mylive.app.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.HistoryRepository
import com.mylive.app.data.repository.ProfileBackupManager
import com.mylive.app.data.repository.ShieldRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileRestoreIntegrityTest {
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsDataStore
    private lateinit var follows: FollowRepository
    private lateinit var manager: ProfileBackupManager

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsDataStore(context)
        settings.clearAll()
        follows = FollowRepository(db.followUserDao(), db.followUserTagDao(), db)
        manager = manager(settings)
        settings.setValue(SettingsDataStore.DanmuEnable, true)
        follows.addFollow(FollowUserEntity("old", "old", "huya", "old", "", 1))
    }

    @After
    fun tearDown() = runBlocking { db.close(); settings.clearAll() }

    @Test
    fun malformedLaterSectionNeverChangesEarlierSections() = runBlocking {
        val cases = listOf(
            profile().put("histories", "broken"),
            profile().put("histories", JSONArray().put(JSONObject().put("siteId", "huya"))),
            profile().put("followUserTags", JSONArray().put(JSONObject().put("tag", "tag").put("userIds", JSONArray().put(12)))),
            profile().put("shieldPresets", JSONArray().put(JSONObject().put("name", ""))),
            profile().put("settings", JSONObject().put("DanmuEnable", false).put("DanmuSize", "broken")),
            profile().put("followUsers", JSONArray().put(JSONObject().put("siteId", "huya").put("roomId", JSONObject())))
        )
        for (payload in cases) {
            expectRejected { manager.importProfileJson(payload.toString()) }
            assertOriginalData()
        }
        expectRejected { manager.importProfileJson(profile().toString() + " trailing data") }
        assertOriginalData()
    }

    @Test
    fun unsupportedVersionAndWrongProfileTypeAreRejectedBeforeWrites() = runBlocking {
        for (payload in listOf(profile().put("schemaVersion", 99), profile().put("schemaVersion", 2.5),
            profile().put("type", "other"))) {
            expectRejected { manager.importProfileJson(payload.toString()) }
            assertOriginalData()
        }
    }

    @Test
    fun databaseFailureRollsBackFollowAndTagInserts() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_import BEFORE INSERT ON histories BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )
        val payload = profile()
            .put("followUserTags", JSONArray().put(JSONObject().put("id", "tag").put("tag", "tag")))
            .put("histories", JSONArray().put(room()))
        try {
            manager.importProfileJson(payload.toString())
            fail("Expected database failure")
        } catch (_: android.database.sqlite.SQLiteException) {
            assertOriginalData()
            assertTrue(follows.getAllTags().first().isEmpty())
            assertTrue(HistoryRepository(db.historyDao()).getAllHistory().first().isEmpty())
        }
    }

    @Test
    fun cancellationAfterSettingsWriteCompensatesSettingsAndRollsBackRoom() = runBlocking {
        settings.setValue(SettingsDataStore.DouyinCookie, "keep-local")
        settings.setValue(SettingsDataStore.ChatTextSize, 17.0)
        val intercepted = mockk<SettingsDataStore>()
        var writes = 0
        coEvery { intercepted.edit(any()) } coAnswers {
            settings.edit(firstArg<(MutablePreferences) -> Unit>())
            if (++writes == 1) throw CancellationException("cancel immediately after DataStore commit")
        }
        try {
            manager(intercepted).importProfileJson(profile().toString())
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertOriginalData()
            assertEquals("keep-local", settings.getFlow(SettingsDataStore.DouyinCookie, "").first())
            assertEquals(17.0, settings.getFlow(SettingsDataStore.ChatTextSize, 14.0).first(), 0.0)
            assertEquals(2, writes)
        }
    }

    @Test
    fun compensationPreservesALaterEditToAnImportedKey() = runBlocking {
        val intercepted = mockk<SettingsDataStore>()
        var writes = 0
        coEvery { intercepted.edit(any()) } coAnswers {
            settings.edit(firstArg<(MutablePreferences) -> Unit>())
            if (++writes == 1) {
                settings.setValue(SettingsDataStore.ChatTextSize, 22.0)
                throw CancellationException("cancelled")
            }
        }
        val payload = profile().put("settings", JSONObject().put("DanmuEnable", false).put("ChatTextSize", 18.0))
        try {
            manager(intercepted).importProfileJson(payload.toString())
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertOriginalData()
            assertEquals(22.0, settings.getFlow(SettingsDataStore.ChatTextSize, 14.0).first(), 0.0)
        }
    }

    @Test
    fun legacyBoxesAndArrayImportsStillWorkWithoutRestoringCredentialsOrRemoteEndpoints() = runBlocking {
        settings.setValue(SettingsDataStore.SyncServerUrl, "local-server")
        settings.setValue(SettingsDataStore.DouyinCookie, "keep-local")
        val boxes = JSONObject().put("settings", JSONObject()
            .put("DanmuEnable", JSONObject().put("value", false))
            .put("SyncServerUrl", "remote-server").put("DouyinCookie", "remote-cookie"))
            .put("followUsers", JSONArray().put(room()))
            .put("danmuShield", JSONObject().put("keywords", JSONArray().put("spam").put("spam")))
        manager.importProfileJson(JSONObject().put("boxes", boxes).toString(), trusted = false)
        assertFalse(settings.getFlow(SettingsDataStore.DanmuEnable, true).first())
        assertEquals("local-server", settings.getFlow(SettingsDataStore.SyncServerUrl, "").first())
        assertEquals("keep-local", settings.getFlow(SettingsDataStore.DouyinCookie, "").first())
        assertEquals(1, ShieldRepository(db.shieldDao()).getAllShields().first().size)
        manager.importProfileJson(JSONArray().put(room().put("roomId", "another").put("id", "another")).toString())
        assertEquals(3, follows.getAllFollows().first().size)
    }

    private suspend fun assertOriginalData() {
        assertEquals(listOf("old"), follows.getAllFollows().first().map { it.id })
        assertTrue(settings.getFlow(SettingsDataStore.DanmuEnable, true).first())
    }

    private suspend fun expectRejected(block: suspend () -> Unit) {
        try { block(); fail("Expected validation rejection") } catch (_: IllegalArgumentException) { }
    }

    private fun manager(store: SettingsDataStore) = ProfileBackupManager(
        store, follows, HistoryRepository(db.historyDao()), ShieldRepository(db.shieldDao()), db
    )

    private fun room() = JSONObject().put("id", "new").put("siteId", "huya")
        .put("roomId", "new").put("userName", "new").put("addTime", 123)

    private fun profile() = JSONObject().put("type", ProfileBackupManager.PROFILE_TYPE)
        .put("schemaVersion", ProfileBackupManager.SCHEMA_VERSION)
        .put("settings", JSONObject().put("DanmuEnable", false))
        .put("followUsers", JSONArray().put(room()))
}
