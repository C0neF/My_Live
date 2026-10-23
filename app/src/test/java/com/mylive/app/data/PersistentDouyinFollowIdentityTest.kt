package com.mylive.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.script.JsEngine
import com.mylive.app.core.site.FollowPresence
import com.mylive.app.core.site.LiveSite
import com.mylive.app.core.site.douyin.DouyinSite
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.data.local.entity.playbackRoomId
import com.mylive.app.data.repository.*
import com.mylive.app.service.FollowStatusRefreshCoordinator
import com.mylive.app.service.decodeFollowsForLanSync
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import javax.inject.Provider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistentDouyinFollowIdentityTest {
    private val databaseName = "douyin-identity-${UUID.randomUUID()}"
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FollowRepository
    private val original = FollowUserEntity(
        id = "douyin_7376429659866598196", roomId = "7376429659866598196", siteId = "douyin",
        userName = "anchor", face = "", addTime = 12, tag = "group", isSpecialFollow = true,
        liveStatus = 1, liveStartTime = 23
    )

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        openDatabase()
        repository.addFollow(original)
        repository.addTag(FollowUserTagEntity("tag", "group", listOf(original.id)))
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).allowMainThreadQueries().build()
        repository = FollowRepository(database.followUserDao(), database.followUserTagDao(), database)
    }

    @After fun tearDown() { database.close(); context.deleteDatabase(databaseName) }

    @Test fun learnedIdentitySurvivesDatabaseReopenAndAvoidsTheExpiredSessionRequest() = runBlocking {
        val http = mockk<HttpClient>()
        coEvery { http.getJson(match { it.contains("/reflow/info/") }, any(), any()) } returns
            JSONObject("""{"data":{"room":{"status":2,"owner":{"web_rid":"416144012050"}}}}""")
        coEvery { http.getJson(match { it.contains("web_rid=416144012050") }, any(), any()) } returns
            JSONObject("""{"data":{"data":[{"status":2}]}}""")
        val script = mockk<JsEngine>()
        coEvery { script.execute(any(), any(), *anyVararg()) } returns "signature"
        val site = DouyinSite(http, Provider { script }, OkHttpClient()) { "" }

        assertTrue(coordinator(site).refreshAll().isEmpty())
        database.close()
        openDatabase()
        val learned = repository.getAllFollows().first().single()
        assertEquals(original.copy(canonicalRoomId = "416144012050"), learned)
        assertEquals(original.id, repository.getFollow("douyin", "416144012050")?.id)
        assertEquals(original.id, repository.getFollow("douyin", original.roomId)?.id)
        assertTrue(repository.observeFollowing("douyin", "416144012050").first())
        assertEquals(listOf(original.id), repository.getAllTags().first().single().userIds)
        assertTrue(coordinator(site).refreshAll().isEmpty())
        coVerify(exactly = 1) { http.getJson(match { it.contains("/reflow/info/") }, any(), any()) }
        coVerify(exactly = 1) { http.getJson(match { it.contains("web_rid=416144012050") }, any(), any()) }
    }

    @Test fun failedLookupKeepsTheLearnedIdentityAndCurrentStatus() = runBlocking {
        repository.rememberCanonicalRoomId("douyin", original.roomId, "416144012050")
        val site = mockk<LiveSite>()
        every { site.id } returns "douyin"
        coEvery { site.getFollowPresence("416144012050") } throws IllegalStateException("network failed")
        val refresh = coordinator(site)
        assertTrue(refresh.refreshAll().isEmpty())
        val follow = repository.getAllFollows().first().single()
        assertEquals(original.copy(canonicalRoomId = "416144012050"), follow)
        assertTrue(refresh.entries.value.getValue(original.id).failed)
        coVerify(exactly = 0) { site.getFollowPresence(original.roomId) }
    }

    @Test fun legacyImportDoesNotEraseALearnedAliasButDoesNotAttachItToAnotherRoom() = runBlocking {
        repository.rememberCanonicalRoomId("douyin", original.roomId, "416144012050")
        repository.addFollows(listOf(original.copy(userName = "restored name")))
        assertEquals("416144012050", repository.getAllFollows().first().single().canonicalRoomId)
        repository.addFollows(listOf(original.copy(roomId = "another-room")))
        assertNull(repository.getAllFollows().first().single().canonicalRoomId)
        assertEquals("another-room", repository.getAllFollows().first().single().playbackRoomId)
    }

    @Test fun backupFollowExportAndLanPayloadPreserveBothIdentityForms() = runBlocking {
        repository.rememberCanonicalRoomId("douyin", original.roomId, "416144012050")
        val followExport = repository.exportToJson()
        val lanFollow = decodeFollowsForLanSync(JSONObject(followExport).getJSONArray("follows")).single()
        assertEquals("416144012050", lanFollow.playbackRoomId)
        assertEquals(original.id, lanFollow.id)
        val settings = SettingsDataStore(context)
        settings.clearAll()
        try {
            val manager = ProfileBackupManager(settings, repository, HistoryRepository(database.historyDao()),
                ShieldRepository(database.shieldDao()), database)
            val profile = manager.exportProfileJson()
            assertEquals(3, JSONObject(profile).getInt("schemaVersion"))
            repository.clearAllFollows()
            manager.importProfileJson(profile)
            assertEquals(original.copy(canonicalRoomId = "416144012050"), repository.getAllFollows().first().single())
            repository.clearAllFollows()
            repository.importFromJson(followExport)
            assertEquals(original.copy(canonicalRoomId = "416144012050"), repository.getAllFollows().first().single())
        } finally { settings.clearAll() }
    }

    @Test fun canonicalLookupDoesNotChangeUnrelatedPlatforms() = runBlocking {
        val other = original.copy(id = "other", siteId = "bilibili", roomId = "6", canonicalRoomId = "999")
        repository.addFollow(other)
        assertEquals("6", other.playbackRoomId)
        assertNull(repository.getFollow("bilibili", "999"))
    }

    private fun coordinator(site: LiveSite) = FollowStatusRefreshCoordinator(repository, mockk<SettingsRepository> {
        every { updateFollowThreadCount } returns flowOf(1)
    }, setOf(site))
}
