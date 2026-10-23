package com.mylive.app.ui.screen.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.core.sync.LanSyncProtocol
import com.mylive.app.core.sync.LanSyncImportType
import com.mylive.app.core.sync.LanSyncProtocolException
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.HistoryRepository
import com.mylive.app.data.repository.ProfileBackupManager
import com.mylive.app.data.repository.ShieldRepository
import com.mylive.app.data.sync.LanSyncClient
import com.mylive.app.service.LanSyncService
import com.mylive.app.testing.ReviewRoomFixture
import fi.iki.elonen.NanoHTTPD
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sending ViewModel, client, encryption/authentication, receiving HTTP handler, job polling,
 * repositories and both Room databases are real. Only the socket transport is adapted in-process.
 * SQLite triggers supply storage failures at the actual follow/tag insert boundaries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncDeviceFollowConsistencyTest : ReviewRoomFixture() {
    private lateinit var targetDatabase: AppDatabase
    private lateinit var targetFollows: FollowRepository
    private lateinit var store: SettingsDataStore
    private lateinit var sourceManager: ProfileBackupManager
    private lateinit var service: LanSyncService
    private lateinit var receiver: NanoHTTPD
    private lateinit var client: OkHttpClient
    private val trace = CopyOnWriteArrayList<String>()
    private var emulateLegacyPeer = false
    private val observedJobStates = CopyOnWriteArrayList<String>()

    private val incomingA = FollowUserEntity(
        id = "huya_A", roomId = "A", siteId = "huya", userName = "source-A", face = "", addTime = 1L,
        tag = "源分组G", isSpecialFollow = true, liveStatus = 1, liveStartTime = 123L
    )
    private val originalA = incomingA.copy(userName = "local-A", tag = "本地分组H", liveStatus = 2)
    private val unrelatedC = FollowUserEntity(
        id = "huya_C", roomId = "C", siteId = "huya", userName = "keep-local-C", face = "", addTime = 2L,
        tag = "本地分组H", isSpecialFollow = true, liveStatus = 1, liveStartTime = 456L
    )
    private val incomingGroup = FollowUserTagEntity("source-G", "源分组G", listOf(incomingA.id))
    private val originalGroup = FollowUserTagEntity("local-H", "本地分组H", listOf(originalA.id, unrelatedC.id))

    @Before
    fun setUpPeer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = SettingsDataStore(context)
        store.clearAll()
        sourceManager = ProfileBackupManager(store, follows, history, shields, database)
        follows.addFollow(incomingA)
        follows.addTag(incomingGroup)

        targetDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        targetFollows = FollowRepository(
            targetDatabase.followUserDao(), targetDatabase.followUserTagDao(), targetDatabase
        )
        targetFollows.addFollows(listOf(originalA, unrelatedC))
        targetFollows.addTag(originalGroup)
        val targetHistory = HistoryRepository(targetDatabase.historyDao())
        val targetShields = ShieldRepository(targetDatabase.shieldDao())
        val targetManager = ProfileBackupManager(store, targetFollows, targetHistory, targetShields, targetDatabase)
        service = LanSyncService().apply {
            followRepository = targetFollows
            historyRepository = targetHistory
            shieldRepository = targetShields
            profileBackupManager = targetManager
        }
        LanSyncService.syncToken = PAIRING_TOKEN
        LanSyncService.syncDeviceId = "follow-consistency-test"
        LanSyncService.ipAddress = "127.0.0.1"
        val serverClass = LanSyncService::class.java.declaredClasses.single { it.simpleName == "HttpServer" }
        val constructor = serverClass.getDeclaredConstructor(
            LanSyncService::class.java, String::class.java, Integer.TYPE
        ).apply { isAccessible = true }
        receiver = constructor.newInstance(service, "127.0.0.1", 23234) as NanoHTTPD

        client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = request.body?.let { Buffer().also(it::writeTo).readUtf8() }.orEmpty()
            val response = receiver.serve(sessionFor(request, body))
            try {
                val status = response.status.requestStatus
                val actualBody = response.data?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val responseBody = if (emulateLegacyPeer && request.url.encodedPath == LanSyncProtocol.INFO_PATH) {
                    JSONObject(actualBody).apply { remove("follow_bundle") }.toString()
                } else actualBody
                trace += "${request.method} ${request.url.encodedPath} -> $status ${responseBody.take(240)}"
                if (request.method == "GET" && request.url.encodedPath.startsWith(LanSyncProtocol.JOB_PATH_PREFIX)) {
                    observedJobStates += JSONObject(responseBody).optString("state")
                }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(status).message(response.status.description)
                    .body(responseBody.toResponseBody("application/json".toMediaType())).build()
            } finally {
                response.close()
            }
        }.build()
    }

    @After
    fun tearDownPeer() = runBlocking {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        service.onDestroy()
        targetDatabase.close()
        store.clearAll()
    }

    @Test
    fun failedTagWriteReportsFailureAndKeepsFollowMembershipConsistent() = runBlocking {
        targetDatabase.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_source_tag BEFORE INSERT ON follow_user_tags " +
                "WHEN NEW.id = 'source-G' BEGIN SELECT RAISE(ABORT, 'controlled tag write failure'); END"
        )

        val viewModel = syncFollow()

        assertTrue(diagnostics(viewModel), viewModel.syncResults.value["follow"].orEmpty().contains("同步失败"))
        assertTrue("The receiving import job must actually fail: ${diagnostics(viewModel)}",
            "failed" in observedJobStates)
        assertEquals(unrelatedC, targetFollows.getFollow("huya", "C"))
        // A may remain unchanged or be imported consistently. This does not demand that a remote
        // operation roll back every update; it demands that any surviving tag assignment is usable.
        assertConsistentMembership(diagnostics(viewModel))
    }

    @Test
    fun successfulMergeMovesTheImportedFollowAndPreservesUnrelatedLocalMembers() = runBlocking {
        val viewModel = syncFollow()

        assertTrue(diagnostics(viewModel), viewModel.syncResults.value["follow"].orEmpty().contains("同步成功"))
        assertEquals(incomingA, targetFollows.getFollow("huya", "A"))
        assertEquals(unrelatedC, targetFollows.getFollow("huya", "C"))
        assertEquals(incomingGroup, targetFollows.getAllTags().first().single { it.id == incomingGroup.id })
        assertEquals(originalGroup.copy(userIds = listOf(unrelatedC.id)),
            targetFollows.getAllTags().first().single { it.id == originalGroup.id })
        assertConsistentMembership(diagnostics(viewModel))
    }

    @Test
    fun failedFollowWriteReportsFailureWithoutChangingEitherLocalCollection() = runBlocking {
        targetDatabase.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_source_follow BEFORE INSERT ON follow_users " +
                "WHEN NEW.id = 'huya_A' BEGIN SELECT RAISE(ABORT, 'controlled follow write failure'); END"
        )

        val viewModel = syncFollow()

        assertTrue(diagnostics(viewModel), viewModel.syncResults.value["follow"].orEmpty().contains("同步失败"))
        assertTrue("The receiving import job must actually fail: ${diagnostics(viewModel)}",
            "failed" in observedJobStates)
        assertEquals(listOf(originalA, unrelatedC).sortedBy { it.id },
            targetFollows.getAllFollows().first().sortedBy { it.id })
        assertEquals(listOf(originalGroup), targetFollows.getAllTags().first())
        assertConsistentMembership(diagnostics(viewModel))
    }

    @Test
    fun aStableGroupIdCanBeRenamedWithoutDroppingOrRewritingUnrelatedMembers() = runBlocking {
        val renamed = originalGroup.copy(tag = "改名后的分组", userIds = listOf(incomingA.id))
        follows.clearAllTags()
        follows.addTag(renamed)
        follows.addFollow(incomingA.copy(tag = renamed.tag))

        val viewModel = syncFollow()

        assertTrue(diagnostics(viewModel), viewModel.syncResults.value["follow"].orEmpty().contains("同步成功"))
        assertEquals(unrelatedC.copy(tag = renamed.tag), targetFollows.getFollow("huya", "C"))
        assertEquals(incomingA.copy(tag = renamed.tag), targetFollows.getFollow("huya", "A"))
        val stored = targetFollows.getAllTags().first().single()
        assertEquals(renamed.id, stored.id)
        assertEquals(renamed.tag, stored.tag)
        assertEquals(setOf(incomingA.id, unrelatedC.id), stored.userIds.toSet())
        assertConsistentMembership(diagnostics(viewModel))
    }

    @Test
    fun sameNamedSourceGroupsUseTheirActualMemberIdsInsteadOfTheFirstNameMatch() = runBlocking {
        follows.addTag(incomingGroup.copy(userIds = emptyList()))
        val actualGroup = incomingGroup.copy(id = "source-G-second")
        follows.addTag(actualGroup)

        val viewModel = syncFollow()

        assertTrue(diagnostics(viewModel), viewModel.syncResults.value["follow"].orEmpty().contains("同步成功"))
        val tags = targetFollows.getAllTags().first().associateBy { it.id }
        assertEquals(listOf(incomingA.id), tags.getValue(actualGroup.id).userIds)
        assertTrue(tags.getValue(incomingGroup.id).userIds.isEmpty())
        assertEquals(listOf(unrelatedC.id), tags.getValue(originalGroup.id).userIds)
        assertConsistentMembership(diagnostics(viewModel))
    }

    @Test
    fun overlayReplacesBothCollectionsTogetherAndAnEmptyBundleCanClearBoth() = runBlocking {
        val viewModel = syncFollow(overlay = true)

        assertTrue(diagnostics(viewModel), viewModel.syncResults.value["follow"].orEmpty().contains("同步成功"))
        assertEquals(listOf(incomingA), targetFollows.getAllFollows().first())
        assertEquals(listOf(incomingGroup), targetFollows.getAllTags().first())
        follows.clearAllFollows()
        follows.clearAllTags()

        val empty = syncFollow(overlay = true)

        assertTrue(diagnostics(empty), empty.syncResults.value["follow"].orEmpty().contains("同步成功"))
        assertTrue(targetFollows.getAllFollows().first().isEmpty())
        assertTrue(targetFollows.getAllTags().first().isEmpty())
    }

    @Test
    fun anEmptyMergeDoesNotEraseLocalFollowsOrTheirGroups() = runBlocking {
        follows.clearAllFollows()
        follows.clearAllTags()

        val viewModel = syncFollow()

        assertTrue(diagnostics(viewModel), viewModel.syncResults.value["follow"].orEmpty().contains("同步成功"))
        assertEquals(listOf(originalA, unrelatedC).sortedBy { it.id },
            targetFollows.getAllFollows().first().sortedBy { it.id })
        assertEquals(listOf(originalGroup), targetFollows.getAllTags().first())
    }

    @Test
    fun aLegacyPeerIsRefusedBeforeAnyFollowWriteWhileHistorySyncStillWorks() = runBlocking {
        emulateLegacyPeer = true

        val viewModel = syncFollow()

        assertTrue(diagnostics(viewModel), viewModel.syncResults.value["follow"].orEmpty().contains("更新"))
        assertTrue("An unsupported peer must not receive a partial import: $trace",
            trace.none { it.startsWith("POST ") })
        assertEquals(listOf(originalA, unrelatedC).sortedBy { it.id },
            targetFollows.getAllFollows().first().sortedBy { it.id })
        assertEquals(listOf(originalGroup), targetFollows.getAllTags().first())

        val record = HistoryEntity("huya_test", "test", "huya", "history", "", 123L)
        history.addHistory(record)
        viewModel.syncHistory("127.0.0.1", 23234, PAIRING_TOKEN)
        awaitWork(viewModel)
        assertTrue(diagnostics(viewModel), viewModel.syncResults.value["history"].orEmpty().contains("同步成功"))
        assertEquals(listOf(record), HistoryRepository(targetDatabase.historyDao()).getAllHistory().first())
    }

    @Test
    fun legacyArrayEndpointsRemainAvailableToExistingClients() = runBlocking {
        val peer = LanSyncClient(client).connect("127.0.0.1", 23234, PAIRING_TOKEN)

        peer.import(LanSyncImportType.FOLLOW, "[]", overlay = false)
        peer.import(LanSyncImportType.TAG, "[]", overlay = false)

        assertEquals(listOf(originalA, unrelatedC).sortedBy { it.id },
            targetFollows.getAllFollows().first().sortedBy { it.id })
        assertEquals(listOf(originalGroup), targetFollows.getAllTags().first())
    }

    @Test
    fun invalidSectionsOrOversizedBundleArraysAreRejectedBeforeStartingAnImportJob() = runBlocking {
        val peer = LanSyncClient(client).connect("127.0.0.1", 23234, PAIRING_TOKEN)
        val cases = listOf(
            JSONObject().put("follows", JSONArray()).toString() to 400,
            JSONObject().put("follows", JSONArray(List(2_001) { JSONObject() }))
                .put("tags", JSONArray()).toString() to 413,
            JSONObject().put("follows", JSONArray())
                .put("tags", JSONArray(List(1_001) { JSONObject() })).toString() to 413
        )

        cases.forEach { (payload, status) ->
            trace.clear()
            val failure = runCatching { peer.import(LanSyncImportType.FOLLOW, payload, overlay = true) }.exceptionOrNull()
            assertTrue("Expected a rejected request: $trace", failure is LanSyncProtocolException)
            assertTrue("Expected HTTP $status: $trace", trace.any { it.startsWith("POST /sync/follow -> $status ") })
            assertTrue("Invalid input must not enqueue an import job: $trace",
                trace.none { it.startsWith("GET /sync/job/") })
            assertEquals(listOf(originalA, unrelatedC).sortedBy { it.id },
                targetFollows.getAllFollows().first().sortedBy { it.id })
            assertEquals(listOf(originalGroup), targetFollows.getAllTags().first())
        }
    }

    private suspend fun syncFollow(overlay: Boolean = false): SyncDeviceViewModel {
        val viewModel = own(SyncDeviceViewModel(follows, history, shields, sourceManager, LanSyncClient(client)))
        viewModel.syncFollow("127.0.0.1", 23234, PAIRING_TOKEN, overlay)
        awaitWork(viewModel)
        assertNull("The operation must finish: ${diagnostics(viewModel)}", viewModel.syncingKey.value)
        return viewModel
    }

    private suspend fun assertConsistentMembership(details: String) {
        val storedFollows = targetFollows.getAllFollows().first()
        val tags = targetFollows.getAllTags().first()
        storedFollows.forEach { follow ->
            val memberships = tags.filter { follow.id in it.userIds }
            val expectedNames = if (follow.tag.isBlank()) emptyList() else listOf(follow.tag)
            assertEquals("Follow ${follow.id} claims '${follow.tag}', but its actual groups disagree. $details",
                expectedNames, memberships.map { it.tag })
        }
        tags.forEach { tag ->
            tag.userIds.forEach { memberId ->
                assertTrue("Group ${tag.id} references a missing follow $memberId. $details",
                    storedFollows.any { it.id == memberId })
            }
        }
    }

    private fun diagnostics(viewModel: SyncDeviceViewModel): String =
        "result=${viewModel.syncResults.value}; actual HTTP handler trace=$trace"

    private fun sessionFor(request: Request, body: String): NanoHTTPD.IHTTPSession {
        val headers = request.headers.names().associate { name ->
            name.lowercase() to request.header(name).orEmpty()
        }.toMutableMap()
        request.body?.contentLength()?.let { headers["content-length"] = it.toString() }
        val parameters = request.url.queryParameterNames.associateWith { name ->
            request.url.queryParameterValues(name).filterNotNull()
        }
        return mockk<NanoHTTPD.IHTTPSession> {
            every { method } returns NanoHTTPD.Method.valueOf(request.method)
            every { uri } returns request.url.encodedPath
            every { this@mockk.headers } returns headers
            every { this@mockk.parameters } returns parameters
            every { parseBody(any()) } answers {
                firstArg<MutableMap<String, String>>()["postData"] = body
            }
        }
    }

    private companion object {
        const val PAIRING_TOKEN = "4f1c35034a8e46c399e10e520eb3d9cf"
    }
}
