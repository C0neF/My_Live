package com.mylive.app.core.site.douyu

import com.mylive.app.core.common.CoreError
import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.core.model.LiveRoomDetail
import com.mylive.app.core.model.PlayQualityData
import com.mylive.app.core.site.LiveSiteAccount
import com.mylive.app.core.site.LiveSiteAccountStore
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DouyuPlaybackAuthRecoveryTest {
    private val jwt = "header." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString("""{"exp":3600}""".toByteArray()) + ".signature"
    private val accounts = Accounts("dy_did=test-device; LTP0=renewal-secret; dy_auth=old-login; acf_jwt_token=$jwt")
    private val renewals = AtomicInteger()
    private val descriptors = AtomicInteger()
    private val playbackRequests = AtomicInteger()
    private val detail = LiveRoomDetail("100", "Test room", "", "Test anchor")
    private val quality = LivePlayQuality("Test quality", PlayQualityData.Douyu(0, listOf("a", "b", "c")))
    private var renewalStatus = 200
    private var playback: (Request) -> Int = { if (it.header("Cookie").orEmpty().contains("dy_auth=new-login")) 200 else 403 }

    private fun site(): DouyuSite {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("")
            when {
                request.url.host == "passport.douyu.com" -> {
                    renewals.incrementAndGet()
                    assertEquals("dy_did=test-device; LTP0=renewal-secret", request.header("Cookie"))
                    response.code(renewalStatus).addHeader("Set-Cookie", "dy_auth=new-login; Domain=.douyu.com; HttpOnly")
                        .body("ok".toResponseBody()).build()
                }
                request.url.encodedPath.endsWith("getEncryption") -> {
                    descriptors.incrementAndGet()
                    response.code(200).body("""{"error":0,"data":{"key":"key","rand_str":"random","enc_time":1,"enc_data":"descriptor","is_special":0}}""".toResponseBody()).build()
                }
                else -> {
                    playbackRequests.incrementAndGet()
                    assertFalse(request.header("Cookie").orEmpty().contains("LTP0"))
                    assertEquals(if (accounts.cookie.isEmpty()) DOUYU_ANONYMOUS_DID else "test-device", request.formValue("did"))
                    val status = playback(request)
                    response.code(status).body(
                        (if (status == 200) """{"error":0,"data":{"cdnsWithName":[{"cdn":"a"},{"cdn":"b"},{"cdn":"c"}],"multirates":[{"name":"Test quality","rate":0}],"rtmp_url":"https://stream.example","rtmp_live":"live.flv"}}"""
                        else "\"鉴权失败\"").toResponseBody()
                    ).build()
                }
            }
        }.build()
        val http = HttpClient(client)
        return DouyuSite(http, client, DouyuAccountSession(accounts, http) { 100_000L }, DouyuPlaybackSigner(http) { 100_000L })
    }

    @Test fun unexpiredButRejectedLoginRenewsAndReentryReusesThePersistedCredential() = runBlocking {
        val site = site()
        assertEquals(1, site.getPlayQualites(detail).size)
        assertEquals(1, renewals.get())
        assertEquals(2, playbackRequests.get())
        assertEquals(2, descriptors.get())

        assertEquals(3, site.getPlayUrls(detail, quality).urls.size)
        assertEquals(1, site.getPlayQualites(detail).size)
        assertEquals(1, renewals.get())
        assertTrue(accounts.cookie.contains("dy_auth=new-login"))
    }

    @Test fun allRejectedCdnsShareOneRenewalAndRetryTheBatchOnce() = runBlocking {
        val result = site().getPlayUrls(detail, quality)
        assertEquals(List(3) { "https://stream.example/live.flv" }, result.urls)
        assertEquals(6, playbackRequests.get())
        assertEquals(1, renewals.get())
        assertEquals("dy_did=test-device; acf_did=test-device", result.headers?.get("Cookie"))
    }

    @Test fun oneRejectedCdnDoesNotRenewOrDiscardOtherWorkingLines() = runBlocking {
        playback = { request -> if (request.formValue("cdn") == "b") 403 else 200 }
        val result = site().getPlayUrls(detail, quality)
        assertEquals(2, result.urls.size)
        assertEquals(0, renewals.get())
    }

    @Test fun rejectionAfterRenewalStopsAfterOneRetryAndReportsTheStatus() = runBlocking {
        playback = { 403 }
        val error = runCatching { site().getPlayQualites(detail) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message.orEmpty().contains("403"))
        assertFalse(error.message.orEmpty().contains("old-login"))
        assertEquals(2, playbackRequests.get())
        assertEquals(1, renewals.get())
    }

    @Test fun failedRenewalRetainsTheCredentialAndDoesNotStormOnRepeatedEntry() = runBlocking {
        renewalStatus = 503
        val original = accounts.cookie
        val site = site()
        repeat(3) {
            assertTrue(runCatching { site.getPlayQualites(detail) }.exceptionOrNull()!!.message.orEmpty().contains("403"))
        }
        assertEquals(1, renewals.get())
        assertEquals(3, playbackRequests.get())
        assertEquals(original, accounts.cookie)
    }

    @Test fun nonAuthServerFailuresDoNotRenew() = runBlocking {
        playback = { 503 }
        val error = runCatching { site().getPlayQualites(detail) }.exceptionOrNull()
        assertEquals(503, (error as CoreError).statusCode)
        assertEquals(1, playbackRequests.get())
        assertEquals(0, renewals.get())
    }

    @Test fun anonymousRejectionDoesNotUsePassport() = runBlocking {
        accounts.cookie = ""
        val error = runCatching { site().getPlayQualites(detail) }.exceptionOrNull()
        assertEquals(403, (error as CoreError).statusCode)
        assertEquals(1, playbackRequests.get())
        assertEquals(0, renewals.get())
    }

    private class Accounts(@Volatile var cookie: String) : LiveSiteAccountStore {
        override suspend fun cookieFor(account: LiveSiteAccount): String = cookie
        override suspend fun replaceCookieIfUnchanged(account: LiveSiteAccount, expected: String, replacement: String): Boolean = synchronized(this) {
            if (cookie != expected) false else { cookie = replacement; true }
        }
    }

    private fun Request.formValue(name: String): String? = (body as FormBody).let { form ->
        (0 until form.size).firstOrNull { form.name(it) == name }?.let(form::value)
    }
}
