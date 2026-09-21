package com.mylive.app.core.site.douyin

import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.common.CoreError
import com.mylive.app.core.script.JsEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
}
