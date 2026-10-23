package com.mylive.app.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylive.app.core.common.MAX_IMPORTED_JSON_BYTES
import com.mylive.app.core.common.readUtf8TextWithinLimit
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.HistoryRepository
import com.mylive.app.data.repository.ProfileBackupManager
import com.mylive.app.data.repository.ShieldRepository
import com.mylive.app.ui.screen.sync.ProfileBackupViewModel
import com.mylive.app.ui.screen.sync.ProfileMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android Main, ViewModel, Manager, DataStore, Room and bounded restore reader are all real.
 * Requires -PisolatedDeviceTests=true. Each test restores the audit app's settings snapshot.
 * Many individually small history rows cross 5 MiB without exceeding SQLite's CursorWindow.
 */
@RunWith(AndroidJUnit4::class)
class ProfileBackupLimitInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var settings: SettingsDataStore
    private lateinit var history: HistoryRepository
    private lateinit var manager: ProfileBackupManager
    private var settingsBefore: Preferences? = null
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assertEquals("Build with -PisolatedDeviceTests=true before running this backup test",
            "com.mylive.app.audit", context.packageName)
        // The package assertion precedes every DataStore write. Restore this exact snapshot later.
        settings = SettingsDataStore(context)
        settingsBefore = settings.preferencesFlow().first().toPreferences()
        settings.clearAll()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        history = HistoryRepository(database.historyDao())
        manager = ProfileBackupManager(settings,
            FollowRepository(database.followUserDao(), database.followUserTagDao(), database),
            history, ShieldRepository(database.shieldDao()), database)
    }

    @After
    fun tearDown() = runBlocking {
        try {
            withContext(Dispatchers.Main.immediate) { stores.forEach(ViewModelStore::clear) }
        } finally {
            try {
                if (::database.isInitialized) database.close()
            } finally {
                settingsBefore?.let { before -> settings.edit { restoreSnapshot(it, before) } }
            }
        }
    }

    @Test
    fun cumulativeUtf8HistoryIsRejectedBeforeTheFileCallbackCanReplaceAValidBackup() = runBlocking {
        val previousBackup = manager.exportProfileJson()
        val rows = cumulativeHistory()
        val minimumJson = historyPayload(rows)
        val minimumJsonBytes = minimumJson.toByteArray(Charsets.UTF_8).size
        println("Cumulative backup fixture: rows=${rows.size}, compactHistoryChars=${minimumJson.length}, " +
            "compactHistoryUtf8Bytes=$minimumJsonBytes, restoreByteLimit=$MAX_IMPORTED_JSON_BYTES")
        assertTrue("The complete compact history JSON has fewer characters than the byte limit",
            minimumJson.length < MAX_IMPORTED_JSON_BYTES)
        assertTrue("History JSON alone must exceed the restore limit, even before profile metadata",
            minimumJsonBytes > MAX_IMPORTED_JSON_BYTES)
        assertTrue("Every single stored row must be far below a CursorWindow's size",
            rows.all { it.userName.toByteArray(Charsets.UTF_8).size + it.face.toByteArray(Charsets.UTF_8).size < 4 * 1024 })
        assertBoundedReaderRejects(minimumJson)

        history.addHistories(rows)
        val stored = history.getAllHistory().first()
        assertEquals("The fixture must be readable through Android Room before the export assertion",
            rows.toSet(), stored.toSet())
        val viewModel = newViewModel()
        var callbackCount = 0
        var savedFile = previousBackup

        act(viewModel) { exportProfile { callbackCount++; savedFile = it } }

        assertEquals("Unrestorable output must be rejected before a file can be overwritten", 0, callbackCount)
        assertEquals(previousBackup, savedFile)
        val state = viewModel.uiState.value
        assertTrue("The real ViewModel must report a failed export", state.message is ProfileMessage.ExportFailed)
        assertTrue((state.message as ProfileMessage.ExportFailed).reason.isNotBlank())
        assertFalse(state.isExporting)
        assertEquals(rows.size, history.getAllHistory().first().size)
    }

    @Test
    fun normalUtf8BackupRoundTripsThroughTheFileCallbackBoundedReaderAndRealStores() = runBlocking {
        val originals = List(12) { index -> HistoryEntity(
            id = "huya_normal_$index", roomId = "normal_$index", siteId = "huya",
            userName = "主播🌟直播间 $index", face = "https://example.test/avatar/$index.jpg",
            updateTime = 10_000L + index
        ) }
        settings.setValue(SettingsDataStore.DanmuEnable, false)
        history.addHistories(originals)
        val viewModel = newViewModel()
        var callbackCount = 0
        var savedFile: String? = null

        act(viewModel) { exportProfile { callbackCount++; savedFile = it } }

        assertEquals(1, callbackCount)
        assertEquals(ProfileMessage.ExportOk, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isExporting)
        val json = requireNotNull(savedFile)
        val encoded = json.toByteArray(Charsets.UTF_8)
        assertTrue(encoded.size <= MAX_IMPORTED_JSON_BYTES)
        val boundedJson = encoded.inputStream().use { it.readUtf8TextWithinLimit() }
        assertEquals(json, boundedJson)
        history.clearAllHistory()
        settings.setValue(SettingsDataStore.DanmuEnable, true)
        assertTrue(history.getAllHistory().first().isEmpty())
        assertTrue(settings.getFlow(SettingsDataStore.DanmuEnable, true).first())

        act(viewModel) { importProfile(boundedJson) }

        assertEquals(ProfileMessage.ImportOk, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isImporting)
        assertEquals(originals.toSet(), history.getAllHistory().first().toSet())
        assertFalse(settings.getFlow(SettingsDataStore.DanmuEnable, true).first())
    }

    private suspend fun newViewModel(): ProfileBackupViewModel = withContext(Dispatchers.Main.immediate) {
        ProfileBackupViewModel(manager).also { viewModel ->
            stores += ViewModelStore().also { it.put("backup-limit-test", viewModel) }
        }
    }

    /** Join only this action, so an assertion always observes the completed real ViewModel call. */
    private suspend fun act(viewModel: ProfileBackupViewModel, action: ProfileBackupViewModel.() -> Unit) {
        val actions = withContext(Dispatchers.Main.immediate) {
            val parent = requireNotNull(viewModel.viewModelScope.coroutineContext[Job])
            val before = parent.children.toSet()
            viewModel.action()
            parent.children.filterNot { it in before }.toList()
        }
        withTimeout(30_000) { actions.joinAll() }
        actions.forEach { assertFalse("The backup action must finish successfully", it.isCancelled) }
    }

    private fun cumulativeHistory(): List<HistoryEntity> {
        // ~2.8 KiB per row, split across a long avatar URL and a UTF-8 name, rather than one huge row.
        val name = "主播".repeat(72)
        val avatar = "https://example.test/avatar/" + "a".repeat(2200) + ".jpg"
        return List(2000) { index -> HistoryEntity(
            id = "huya_limit_$index", roomId = "limit_$index", siteId = "huya",
            userName = name, face = avatar, updateTime = 10_000L + index
        ) }
    }

    /** Compact history-only JSON is a lower byte bound on the actual exported complete profile. */
    private fun historyPayload(rows: List<HistoryEntity>): String = JSONObject().put("histories",
        JSONArray().apply { rows.forEach { row -> put(JSONObject()
            .put("id", row.id).put("roomId", row.roomId).put("siteId", row.siteId)
            .put("userName", row.userName).put("face", row.face).put("updateTime", row.updateTime)) } }
    ).toString()

    private fun assertBoundedReaderRejects(json: String) {
        var rejected = false
        try {
            json.byteInputStream(Charsets.UTF_8).use { it.readUtf8TextWithinLimit() }
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("The fixture must exceed the production restore reader's UTF-8 byte limit", rejected)
    }

    @Suppress("UNCHECKED_CAST")
    private fun restoreSnapshot(target: MutablePreferences, before: Preferences) {
        target.clear()
        before.asMap().forEach { (key, value) -> target[key as Preferences.Key<Any>] = value }
    }
}
