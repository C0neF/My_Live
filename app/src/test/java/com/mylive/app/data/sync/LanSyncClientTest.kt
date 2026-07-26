package com.mylive.app.data.sync

import com.mylive.app.core.common.LanSyncAuth
import com.mylive.app.core.sync.LanSyncImportType
import com.mylive.app.core.sync.LanSyncProtocol
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanSyncClientTest {

    @Test
    fun negotiatesBeforeSendingASealedImportAndPollingItsJob() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                LanSyncProtocol.encodePeerInfo(
                    paired = true,
                    port = 23234,
                    deviceId = "peer-1",
                    name = "Peer"
                )
            )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .setBody(
                    """{"status":true,"jobUrl":"/sync/job/job-1","state":"queued"}"""
                )
        )
        server.enqueue(
            MockResponse().setBody(
                """{"status":true,"state":"succeeded","message":"success"}"""
            )
        )
        server.start()

        try {
            val client = LanSyncClient(OkHttpClient())
            val token = "pairing-secret"
            val peer = client.connect("127.0.0.1", server.port, token)
            peer.import(
                type = LanSyncImportType.PROFILE,
                payload = """{"private":"value"}""",
                overlay = false
            )

            val infoRequest = server.takeRequest()
            val importRequest = server.takeRequest()
            val jobRequest = server.takeRequest()
            val sealedBody = importRequest.body.readUtf8()

            assertEquals("/info", infoRequest.path)
            assertNotNull(infoRequest.getHeader(LanSyncAuth.HEADER_NAME))
            assertEquals("/sync/profile", importRequest.path)
            assertTrue(sealedBody.startsWith("MLSYNC2."))
            assertFalse(sealedBody.contains("private"))
            assertEquals("/sync/job/job-1", jobRequest.path)
            assertNotNull(jobRequest.getHeader(LanSyncAuth.HEADER_NAME))
            assertFalse(
                listOf(infoRequest, importRequest, jobRequest)
                    .any { it.getHeader("X-Sync-Token") == token }
            )
        } finally {
            server.shutdown()
        }
    }
}
