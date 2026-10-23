package com.mylive.app.core.site.douyin

import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.common.CoreError
import com.mylive.app.core.script.JsEngine
import com.mylive.app.core.site.LiveSite
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.service.FollowStatusRefreshCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Provider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DouyinFollowPresenceTest {

    private fun createSite(httpClient: HttpClient): DouyinSite {
        val jsEngine = mockk<JsEngine>()
        coEvery { jsEngine.execute(any(), any(), *anyVararg()) } returns "signature"
        return DouyinSite(httpClient, Provider { jsEngine }, OkHttpClient()) { "" }
    }

    @Test
    fun webRidPresenceUsesOneLightweightRequestWithoutPreparingDanmakuCookie() = runBlocking {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } returns JSONObject(
            """
            {
              "data": {
                "data": [
                  {"status": 2, "id_str": "7376429659866598196"}
                ]
              }
            }
            """.trimIndent()
        )
        val site = createSite(httpClient)

        val presence = site.getFollowPresence("416144012050")

        assertTrue(presence.isLive)
        assertNull(presence.showTime)
        coVerify(exactly = 1) { httpClient.getJson(any(), any(), any()) }
        coVerify(exactly = 0) { httpClient.head(any(), any(), any()) }
    }

    @Test
    fun explicitOfflineStatusDoesNotTriggerTheFallbackRoute() = runBlocking {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } returns JSONObject(
            """{"data":{"data":[{"status":4}]}}"""
        )

        val presence = createSite(httpClient).getFollowPresence("416144012050")

        assertFalse(presence.isLive)
        coVerify(exactly = 1) { httpClient.getJson(any(), any(), any()) }
    }

    @Test
    fun emptyWebRidRoomListIsExplicitOfflineInsteadOfLeavingTheOldLiveState() = runBlocking {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } returns JSONObject(
            """{"data":{"data":[],"user":{"nickname":"anchor"}}}"""
        )

        val presence = createSite(httpClient).getFollowPresence("416144012050")

        assertFalse(presence.isLive)
        coVerify(exactly = 1) { httpClient.getJson(any(), any(), any()) }
    }

    @Test
    fun applicationLevelErrorWithEmptyRoomsDoesNotMasqueradeAsOffline() = runBlocking {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } returns JSONObject(
            """{"status_code":2483,"data":{"data":[]}}"""
        )

        var failed = false
        try {
            createSite(httpClient).getFollowPresence("416144012050")
        } catch (_: CoreError) {
            failed = true
        }

        assertTrue(failed)
    }

    @Test
    fun bareEmptyRoomListWithoutAnchorEvidenceRemainsUnknown() = runBlocking {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } returns JSONObject(
            """{"status_code":0,"data":{"data":[]}}"""
        )

        var failed = false
        try {
            createSite(httpClient).getFollowPresence("416144012050")
        } catch (_: CoreError) {
            failed = true
        }

        assertTrue(failed)
    }

    @Test
    fun structuralFailureUsesAtMostOneFallbackRoute() = runBlocking {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } returnsMany listOf(
            JSONObject("""{"data":{"data":[{"title":"unknown"}]}}"""),
            JSONObject("""{"data":{"room":{"status":2}}}""")
        )

        val presence = createSite(httpClient).getFollowPresence("416144012050")

        assertTrue(presence.isLive)
        coVerify(exactly = 2) { httpClient.getJson(any(), any(), any()) }
    }

    @Test
    fun http444StopsWithoutTryingTheFallbackRoute() = runBlocking {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } throws
            CoreError("risk control", statusCode = 444)

        var statusCode = 0
        try {
            createSite(httpClient).getFollowPresence("416144012050")
        } catch (error: CoreError) {
            statusCode = error.statusCode
        }

        assertEquals(444, statusCode)
        coVerify(exactly = 1) { httpClient.getJson(any(), any(), any()) }
    }

    @Test
    fun endedSessionLooksUpTheOwnersCurrentRoomInsteadOfDeclaringTheAnchorOffline() = runBlocking {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } returnsMany listOf(
            JSONObject("""{"data":{"room":{"status":4,"owner":{"web_rid":"416144012050"}}}}"""),
            JSONObject("""{"data":{"data":[{"status":2,"id_str":"new-session"}]}}""")
        )

        val presence = createSite(httpClient).getFollowPresence("7376429659866598196")

        assertTrue(presence.isLive)
        coVerify(exactly = 2) { httpClient.getJson(any(), any(), any()) }
        coVerify(exactly = 1) {
            httpClient.getJson(match { it.contains("web_rid=416144012050") }, any(), any())
        }
        coVerify(exactly = 0) { httpClient.head(any(), any(), any()) }
    }

    @Test
    fun endedSessionOnlyReportsOfflineAfterCheckingTheOwnersCurrentRoom() = runBlocking {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } returnsMany listOf(
            JSONObject("""{"data":{"room":{"status":4,"owner":{"web_rid":"416144012050"}}}}"""),
            JSONObject("""{"data":{"data":[{"status":4}]}}""")
        )

        assertFalse(createSite(httpClient).getFollowPresence("7376429659866598196").isLive)
        coVerify(exactly = 2) { httpClient.getJson(any(), any(), any()) }
        coVerify(exactly = 1) {
            httpClient.getJson(match { it.contains("web_rid=416144012050") }, any(), any())
        }
    }

    @Test
    fun liveSessionDoesNotRequireAnotherLookup() = runBlocking {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } returns
            JSONObject("""{"data":{"room":{"status":2,"owner":{"web_rid":"416144012050"}}}}""")

        assertTrue(createSite(httpClient).getFollowPresence("7376429659866598196").isLive)
        coVerify(exactly = 1) { httpClient.getJson(any(), any(), any()) }
    }

    @Test
    fun unknownSessionStatusCanStillResolveTheOwnersCurrentRoom() = runBlocking {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } returnsMany listOf(
            JSONObject("""{"data":{"room":{"status":0,"owner":{"web_rid":"416144012050"}}}}"""),
            JSONObject("""{"data":{"data":[{"status":2}]}}""")
        )

        assertTrue(createSite(httpClient).getFollowPresence("7376429659866598196").isLive)
        coVerify(exactly = 2) { httpClient.getJson(any(), any(), any()) }
    }

    @Test
    fun endedSessionWithNoUsableOwnerDoesNotProveTheAnchorIsOffline() {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } returnsMany listOf(
            JSONObject("""{"data":{"room":{"status":4}}}"""),
            JSONObject("""{"data":{"data":[]}}""")
        )

        assertThrows(CoreError::class.java) {
            runBlocking { createSite(httpClient).getFollowPresence("7376429659866598196") }
        }
        coVerify(exactly = 2) { httpClient.getJson(any(), any(), any()) }
    }

    @Test
    fun ownersCurrentRoomRateLimitStopsAfterTwoRequests() {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } answers {
            if (firstArg<String>().contains("/reflow/info/")) {
                JSONObject("""{"data":{"room":{"status":4,"owner":{"web_rid":"416144012050"}}}}""")
            } else {
                throw CoreError("risk control", statusCode = 444)
            }
        }

        val error = assertThrows(CoreError::class.java) {
            runBlocking { createSite(httpClient).getFollowPresence("7376429659866598196") }
        }
        assertEquals(444, error.statusCode)
        coVerify(exactly = 2) { httpClient.getJson(any(), any(), any()) }
    }

    @Test
    fun resolvedNewSessionUpdatesTheExistingFollowToLiveWithoutChangingItsIdentity() = runBlocking {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } returnsMany listOf(
            JSONObject("""{"data":{"room":{"status":4,"owner":{"web_rid":"416144012050"}}}}"""),
            JSONObject("""{"data":{"data":[{"status":2}]}}""")
        )
        val follow = FollowUserEntity(
            "douyin_7376429659866598196", "7376429659866598196", "douyin", "anchor", "", 0L,
            tag = "favorites", isSpecialFollow = true, liveStatus = 2
        )
        val repository = mockk<FollowRepository>()
        every { repository.getAllFollows() } returns flowOf(listOf(follow))
        coEvery { repository.updateLiveStatuses(any()) } returns Unit
        val settings = mockk<SettingsRepository>()
        every { settings.updateFollowThreadCount } returns flowOf(1)

        val transitions = FollowStatusRefreshCoordinator(
            repository, settings, setOf<LiveSite>(createSite(httpClient))
        ).refreshAll()

        assertTrue(transitions.single().becameLive)
        coVerify(exactly = 1) {
            repository.updateLiveStatuses(match { users ->
                val updated = users.single()
                updated.id == follow.id && updated.roomId == follow.roomId &&
                    updated.tag == follow.tag && updated.isSpecialFollow && updated.liveStatus == 1
            })
        }
    }

    @Test
    fun failedCurrentRoomLookupDoesNotOverwriteALiveFollowWithTheOldSessionsOfflineStatus() = runBlocking {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } answers {
            if (firstArg<String>().contains("/reflow/info/")) {
                JSONObject("""{"data":{"room":{"status":4,"owner":{"web_rid":"416144012050"}}}}""")
            } else {
                throw CoreError("risk control", statusCode = 444)
            }
        }
        val follow = FollowUserEntity(
            "douyin_7376429659866598196", "7376429659866598196", "douyin", "anchor", "", 0L,
            liveStatus = 1
        )
        val repository = mockk<FollowRepository>()
        every { repository.getAllFollows() } returns flowOf(listOf(follow))
        coEvery { repository.updateLiveStatuses(any()) } returns Unit
        val settings = mockk<SettingsRepository>()
        every { settings.updateFollowThreadCount } returns flowOf(1)

        val transitions = FollowStatusRefreshCoordinator(
            repository, settings, setOf<LiveSite>(createSite(httpClient))
        ).refreshAll()

        assertTrue(transitions.isEmpty())
        coVerify(exactly = 1) { repository.updateLiveStatuses(emptyList()) }
    }

    @Test
    fun cancellationDuringTheCurrentRoomLookupDoesNotStartAnotherRequest() {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } answers {
            if (firstArg<String>().contains("/reflow/info/")) {
                JSONObject("""{"data":{"room":{"status":4,"owner":{"web_rid":"416144012050"}}}}""")
            } else {
                throw CancellationException("cancelled")
            }
        }

        assertThrows(CancellationException::class.java) {
            runBlocking { createSite(httpClient).getFollowPresence("7376429659866598196") }
        }
        coVerify(exactly = 2) { httpClient.getJson(any(), any(), any()) }
    }

    @Test
    fun reflowBusinessErrorCannotBeReportedAsLive() {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } returnsMany listOf(
            JSONObject("""{"status_code":2483,"data":{"room":{"status":2}}}"""),
            JSONObject("""{"status_code":2483,"data":{"data":[]}}""")
        )

        assertThrows(CoreError::class.java) {
            runBlocking { createSite(httpClient).getFollowPresence("7376429659866598196") }
        }
        coVerify(exactly = 2) { httpClient.getJson(any(), any(), any()) }
    }
}
