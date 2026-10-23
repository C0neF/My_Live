package com.mylive.app.core.site.douyu

import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.site.LiveSiteAccount
import com.mylive.app.core.site.LiveSiteAccountStore
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DouyuAccountSessionTest {
    private val accounts = Accounts()
    private val http = mockk<HttpClient>()
    private var now = 100_000L
    private val session = DouyuAccountSession(accounts, http) { now }
    private val legacy = "dy_did=test-device; LTP0=renewal-secret; dy_auth=old-login"

    @Test fun legacyLoginRenewsFromPassportAndRetainsTheLongTermToken() = runBlocking {
        accounts.cookie = legacy
        coEvery { http.getResponse(any(), any(), any()) } returns response("acf_jwt_token=${jwt(3600)}; Domain=.douyu.com; HttpOnly")
        val current = session.forRequest()
        assertTrue(current.cookie.contains("acf_jwt_token="))
        assertFalse(current.cookie.contains("LTP0"))
        assertTrue(accounts.cookie.contains("LTP0=renewal-secret"))
        assertTrue(accounts.cookie.contains("acf_jwt_token="))
        coVerify {
            http.getResponse(
                "https://passport.douyu.com/lapi/passport/iframe/safeAuth", any(),
                match { it?.get("Cookie") == "dy_did=test-device; LTP0=renewal-secret" }
            )
        }
        session.forRequest()
        coVerify(exactly = 1) { http.getResponse(any(), any(), any()) }
    }

    @Test fun everyRequestReadsThePersistedAccountAndReflectsLogout() = runBlocking {
        accounts.cookie = "dy_did=test-device; acf_jwt_token=${jwt(3600)}"
        assertTrue(session.forRequest().cookie.contains("acf_jwt_token="))
        accounts.cookie = ""
        assertFalse(session.forRequest().cookie.contains("acf_jwt_token="))
        coVerify(exactly = 0) { http.getResponse(any(), any(), any()) }
    }

    @Test fun failedRenewalsRetainTheOldCookieAndUseABoundedCooldown() = runBlocking {
        accounts.cookie = legacy
        coEvery { http.getResponse(any(), any(), any()) } throws IOException("offline")
        repeat(5) { assertTrue(session.forRequest().cookie.contains("dy_auth=old-login")) }
        assertEquals(legacy, accounts.cookie)
        coVerify(exactly = 1) { http.getResponse(any(), any(), any()) }
        now += 60_001
        session.forRequest()
        coVerify(exactly = 2) { http.getResponse(any(), any(), any()) }
    }

    @Test fun concurrentRequestsShareOneRenewal() = runBlocking {
        accounts.cookie = legacy
        coEvery { http.getResponse(any(), any(), any()) } coAnswers {
            delay(10)
            response("acf_jwt_token=${jwt(3600)}; Domain=.douyu.com")
        }
        coroutineScope { (1..5).map { async { session.forRequest() } }.forEach { it.await() } }
        coVerify(exactly = 1) { http.getResponse(any(), any(), any()) }
    }

    @Test fun lateRenewalDoesNotUndoALocalSignOut() = runBlocking {
        accounts.cookie = legacy
        coEvery { http.getResponse(any(), any(), any()) } answers {
            accounts.cookie = ""
            response("acf_jwt_token=${jwt(3600)}")
        }
        assertFalse(session.forRequest().cookie.contains("acf_jwt_token="))
        assertEquals("", accounts.cookie)
    }

    @Test fun lateRenewalCannotReplaceAnotherLogin() = runBlocking {
        accounts.cookie = legacy
        val newer = "dy_did=new-device; acf_jwt_token=${jwt(7200)}"
        coEvery { http.getResponse(any(), any(), any()) } answers {
            accounts.cookie = newer
            response("acf_jwt_token=${jwt(3600)}")
        }
        assertEquals("new-device", session.forRequest().deviceId)
        assertEquals(newer, accounts.cookie)
    }

    @Test fun emptyRenewalResponseDoesNotEraseCredentials() = runBlocking {
        accounts.cookie = legacy
        coEvery { http.getResponse(any(), any(), any()) } returns response("dy_auth=; Max-Age=0")
        session.forRequest()
        assertEquals(legacy, accounts.cookie)
        assertTrue(session.message.value.orEmpty().contains("未成功"))
    }

    @Test fun expiredLoginWithoutRenewalDoesNotSilentlyDowngradeToAnonymous() = runBlocking {
        accounts.cookie = "dy_did=test-device; acf_jwt_token=${jwt(1)}"
        assertTrue(runCatching { session.forRequest() }.exceptionOrNull() is IllegalStateException)
    }

    @Test fun cancellationPropagatesWithoutReplacingTheCookie() = runBlocking {
        accounts.cookie = legacy
        coEvery { http.getResponse(any(), any(), any()) } throws CancellationException("cancelled")
        assertTrue(runCatching { session.forRequest() }.exceptionOrNull() is CancellationException)
        assertEquals(legacy, accounts.cookie)
        coEvery { http.getResponse(any(), any(), any()) } returns response("acf_jwt_token=${jwt(3600)}")
        assertTrue(session.forRequest().cookie.contains("acf_jwt_token="))
        coVerify(exactly = 2) { http.getResponse(any(), any(), any()) }
    }

    @Test fun serverRejectionRenewsEvenWhenTheJwtHasNotExpired() = runBlocking {
        accounts.cookie = "$legacy; acf_jwt_token=${jwt(3600)}"
        val rejected = session.forRequest()
        coVerify(exactly = 0) { http.getResponse(any(), any(), any()) }
        coEvery { http.getResponse(any(), any(), any()) } returns response("dy_auth=new-login")

        assertTrue(session.renewAfterRejection(rejected)!!.cookie.contains("dy_auth=new-login"))
        assertTrue(accounts.cookie.contains("LTP0=renewal-secret"))
        coVerify(exactly = 1) { http.getResponse(any(), any(), any()) }
    }

    @Test fun concurrentRejectionsReuseTheFirstRenewedSession() = runBlocking {
        accounts.cookie = "$legacy; acf_jwt_token=${jwt(3600)}"
        val rejected = session.forRequest()
        coEvery { http.getResponse(any(), any(), any()) } coAnswers {
            delay(10)
            response("dy_auth=new-login")
        }
        val recovered = coroutineScope {
            (1..5).map { async { session.renewAfterRejection(rejected) } }.map { it.await() }
        }
        assertTrue(recovered.all { it?.cookie?.contains("dy_auth=new-login") == true })
        coVerify(exactly = 1) { http.getResponse(any(), any(), any()) }
    }

    @Test fun failedRejectionRenewalHasACooldownButAllowsLaterRecovery() = runBlocking {
        accounts.cookie = "$legacy; acf_jwt_token=${jwt(3600)}"
        val original = accounts.cookie
        val rejected = session.forRequest()
        coEvery { http.getResponse(any(), any(), any()) } throws IOException("offline")
        repeat(5) { assertNull(session.renewAfterRejection(rejected)) }
        assertEquals(original, accounts.cookie)
        coVerify(exactly = 1) { http.getResponse(any(), any(), any()) }
        now += 60_001
        coEvery { http.getResponse(any(), any(), any()) } returns response("dy_auth=new-login")
        assertNotNull(session.renewAfterRejection(rejected))
        coVerify(exactly = 2) { http.getResponse(any(), any(), any()) }
    }

    @Test fun rejectionAfterLogoutDoesNotRefreshOrReturnAnAnonymousRetry() = runBlocking {
        accounts.cookie = "$legacy; acf_jwt_token=${jwt(3600)}"
        val rejected = session.forRequest()
        accounts.cookie = ""
        assertNull(session.renewAfterRejection(rejected))
        coVerify(exactly = 0) { http.getResponse(any(), any(), any()) }
    }

    @Test fun signingOutDuringRejectionRenewalCannotRestoreTheOldAccount() = runBlocking {
        accounts.cookie = "$legacy; acf_jwt_token=${jwt(3600)}"
        val rejected = session.forRequest()
        coEvery { http.getResponse(any(), any(), any()) } answers {
            accounts.cookie = ""
            response("dy_auth=new-login")
        }
        assertNull(session.renewAfterRejection(rejected))
        assertEquals("", accounts.cookie)
    }

    @Test fun rejectionRenewalCancellationPropagates() = runBlocking {
        accounts.cookie = "$legacy; acf_jwt_token=${jwt(3600)}"
        val rejected = session.forRequest()
        coEvery { http.getResponse(any(), any(), any()) } throws CancellationException("cancelled")
        assertTrue(runCatching { session.renewAfterRejection(rejected) }.exceptionOrNull() is CancellationException)
        coEvery { http.getResponse(any(), any(), any()) } returns response("dy_auth=new-login")
        assertNotNull(session.renewAfterRejection(rejected))
        coVerify(exactly = 2) { http.getResponse(any(), any(), any()) }
    }

    private fun response(vararg cookies: String): Response = Response.Builder()
        .request(Request.Builder().url("https://passport.douyu.com/").build())
        .protocol(Protocol.HTTP_1_1).code(200).message("OK").body("ok".toResponseBody())
        .apply { cookies.forEach { addHeader("Set-Cookie", it) } }.build()

    private fun jwt(exp: Long): String = "header." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString("""{"exp":$exp}""".toByteArray()) + ".signature"

    private class Accounts : LiveSiteAccountStore {
        var cookie = ""
        override suspend fun cookieFor(account: LiveSiteAccount): String = cookie
        override suspend fun replaceCookieIfUnchanged(account: LiveSiteAccount, expected: String, replacement: String): Boolean {
            if (cookie != expected) return false
            cookie = replacement
            return true
        }
    }
}
