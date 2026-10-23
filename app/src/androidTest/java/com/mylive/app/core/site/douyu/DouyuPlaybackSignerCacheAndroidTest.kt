package com.mylive.app.core.site.douyu

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylive.app.core.common.HttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/** Production signer, a controlled clock and locally terminated HTTP; no live account or server. */
@RunWith(AndroidJUnit4::class)
class DouyuPlaybackSignerCacheAndroidTest {
    @Test fun missingKeyIsRejectedWithoutPoisoningNextRoomRequest() = runBlocking {
        assertRejectedDescriptorIsRetried(validData().apply { remove("key") })
    }

    @Test fun emptyKeyIsRejectedWithoutPoisoningNextRoomRequest() = runBlocking {
        assertRejectedDescriptorIsRetried(validData().put("key", ""))
    }

    @Test fun zeroRoundsAreRejectedWithoutPoisoningNextRoomRequest() = runBlocking {
        assertRejectedDescriptorIsRetried(validData().put("enc_time", 0))
    }

    @Test fun excessiveRoundsAreRejectedWithoutPoisoningNextRoomRequest() = runBlocking {
        assertRejectedDescriptorIsRetried(validData().put("enc_time", 101))
    }

    @Test fun missingRandomInputIsRejectedWithoutPoisoningNextRoomRequest() = runBlocking {
        assertRejectedDescriptorIsRetried(validData().apply { remove("rand_str") })
    }

    @Test fun missingEncryptedDataIsRejectedWithoutPoisoningNextRoomRequest() = runBlocking {
        assertRejectedDescriptorIsRetried(validData().apply { remove("enc_data") })
    }

    @Test fun validDescriptorIsReusedWithFreshRoomSpecificSignatures() = runBlocking {
        Fixture(validResponse()).use { fixture ->
            val first = fixture.signer.sign("100", 0, "first-cdn", fixture.session)
            fixture.now += 2_000L
            val second = fixture.signer.sign("200", 4, "second-cdn", fixture.session)
            assertEquals(1, fixture.fetches.get())
            assertEquals("1000", first["tt"])
            assertEquals("1002", second["tt"])
            assertEquals(md5(md5("randomkey") + "key1001000"), first["auth"])
            assertNotEquals(first["auth"], second["auth"])
            assertEquals("second-cdn", second["cdn"])
            assertEquals("4", second["rate"])
        }
    }

    @Test fun descriptorExpiresAtFiveMinutesButNotEarlier() = runBlocking {
        Fixture(validResponse()).use { fixture ->
            fixture.signer.sign("100", 0, "first-cdn", fixture.session)
            fixture.now += 299_999L
            fixture.signer.sign("100", 0, "first-cdn", fixture.session)
            assertEquals(1, fixture.fetches.get())
            fixture.now += 1L
            fixture.signer.sign("200", 0, "second-cdn", fixture.session)
            assertEquals(2, fixture.fetches.get())
        }
    }

    @Test fun explicitBusinessRejectionIsNotCached() = runBlocking {
        Fixture("""{"error":403,"data":{}}""").use { fixture ->
            val first = runCatching { fixture.signer.sign("100", 0, "first-cdn", fixture.session) }
            assertTrue(first.exceptionOrNull() is IllegalStateException)
            val next = fixture.signer.sign("200", 0, "second-cdn", fixture.session)
            assertEquals("descriptor", next["enc_data"])
            assertEquals(2, fixture.fetches.get())
        }
    }

    private suspend fun assertRejectedDescriptorIsRetried(data: JSONObject) {
        Fixture(JSONObject().put("error", 0).put("data", data).toString()).use { fixture ->
            val first = runCatching { fixture.signer.sign("100", 0, "first-cdn", fixture.session) }
            assertTrue("The malformed descriptor itself must be rejected", first.isFailure)
            fixture.now += 1_000L
            val next = runCatching { fixture.signer.sign("200", 4, "second-cdn", fixture.session) }
            assertEquals("The next request must fetch a new descriptor instead of reusing a rejected one",
                2, fixture.fetches.get())
            assertTrue("The valid next response must recover immediately, before the cache TTL", next.isSuccess)
            assertEquals("descriptor", next.getOrThrow()["enc_data"])
            assertEquals("1001", next.getOrThrow()["tt"])
        }
    }

    private class Fixture(private val firstResponse: String) : AutoCloseable {
        var now = 1_000_000L
        val fetches = AtomicInteger()
        private val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            check(request.url.host == "www.douyu.com" && request.url.encodedPath.endsWith("/getEncryption"))
            val body = if (fetches.incrementAndGet() == 1) firstResponse else validResponse()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("")
                .body(body.toResponseBody()).build()
        }.build()
        val signer = DouyuPlaybackSigner(HttpClient(client)) { now }
        val session = DouyuRequestSession("")

        override fun close() {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    companion object {
        private fun validData() = JSONObject(
            """{"key":"key","rand_str":"random","enc_time":1,"enc_data":"descriptor","is_special":0}""")
        private fun validResponse() = JSONObject().put("error", 0).put("data", validData()).toString()
        private fun md5(text: String) = MessageDigest.getInstance("MD5")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
