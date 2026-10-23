package com.mylive.app.core.site.douyu

import com.mylive.app.core.common.HttpClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DouyuPlaybackPolicyTest {
    private val http = mockk<HttpClient>()
    private var now = 1_000_000L
    private val signer = DouyuPlaybackSigner(http) { now }
    private val anonymous = DouyuRequestSession("")

    private fun respond() {
        coEvery { http.getJson(any(), any(), any()) } answers {
            JSONObject("""{"error":0,"data":{"key":"key","rand_str":"random","enc_time":1,"enc_data":"descriptor","is_special":0}}""")
        }
    }

    @Test fun cachedDescriptorStillProducesFreshTimestampAndRoomSpecificSignatures() = runBlocking {
        respond()
        val first = signer.sign("100", 0, "hw-h5", anonymous)
        now += 2000
        val second = signer.sign("200", 2, "hs-h5", anonymous)
        coVerify(exactly = 1) { http.getJson(any(), any(), any()) }
        assertEquals("1000", first["tt"])
        assertEquals("1002", second["tt"])
        assertNotEquals(first["auth"], second["auth"])
        assertEquals(md5(md5("randomkey") + "key" + "1001000"), first["auth"])
        assertEquals("Douyu_new", second["ver"])
        assertEquals("2", second["rate"])
        assertEquals("0", second["hevc"])
    }

    @Test fun expiredDescriptorAndClockRollbackFetchNewDescriptors() = runBlocking {
        respond()
        signer.sign("100", 0, "", anonymous)
        now += 300_000
        signer.sign("100", 0, "", anonymous)
        now = 500_000
        signer.sign("100", 0, "", anonymous)
        coVerify(exactly = 3) { http.getJson(any(), any(), any()) }
    }

    @Test fun loginAndLogoutInvalidateTheAnonymousDescriptor() = runBlocking {
        respond()
        signer.sign("100", 0, "", anonymous)
        val signedIn = DouyuRequestSession("dy_did=test-device; dy_auth=test-login")
        assertEquals("test-device", signer.sign("100", 0, "", signedIn)["did"])
        signer.sign("100", 0, "", anonymous)
        coVerify(exactly = 3) { http.getJson(any(), any(), any()) }
    }

    @Test fun concurrentCdnRequestsShareTheDescriptorFetch() = runBlocking {
        respond()
        coroutineScope { (1..5).map { async { signer.sign("100", 0, "cdn$it", anonymous) } }.forEach { it.await() } }
        coVerify(exactly = 1) { http.getJson(any(), any(), any()) }
    }

    @Test fun rejectedEncryptionResponseDoesNotProduceASignedRequest() = runBlocking {
        coEvery { http.getJson(any(), any(), any()) } returns JSONObject("""{"error":403,"data":{}}""")
        val error = runCatching { signer.sign("100", 0, "", anonymous) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }

    private fun md5(text: String) = MessageDigest.getInstance("MD5").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
