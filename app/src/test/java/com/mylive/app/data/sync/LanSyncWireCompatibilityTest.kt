package com.mylive.app.data.sync

import com.mylive.app.core.common.LanSyncAuth
import com.mylive.app.core.common.LanSyncAuthException
import com.mylive.app.core.common.LanSyncCrypto
import com.mylive.app.core.common.LanSyncReplayGuard
import com.mylive.app.core.sync.LanSyncImportType
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round-trips a real [LanSyncClient] request through the exact derivation `LanSyncService` does.
 *
 * The client signs a canonical string built from what it *intends* to send; the service rebuilds
 * that string from what actually arrived over HTTP. Nothing tested the two together — the client
 * test drives hand-written MockWebServer responses, and the service is only reachable through
 * Android's service lifecycle — so a canonicalisation drift on either side (a path that gets
 * normalised, an `overlay` flag read differently) would have silently 401'd every sync with no
 * failing test.
 *
 * The assertions below therefore reproduce `LanSyncService.serve`'s derivation verbatim:
 * method from the request line, path *without* the query, `overlay` from the query parameter, and
 * the raw body bytes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanSyncWireCompatibilityTest {

    private val token = "0f9a1c2b3d4e5f60718293a4b5c6d7e8"

    @Test
    fun aSealedImportVerifiesAndOpensUsingTheServersOwnDerivation() {
        val payload = """[{"siteId":"bilibili","roomId":"123","userName":"anchor"}]"""
        val recorded = sendImport(LanSyncImportType.FOLLOW, payload, overlay = false)

        val context = verifyAsService(recorded, LanSyncReplayGuard())
        val opened = LanSyncCrypto.decrypt(token, recorded.bodyText, context)

        assertEquals(payload, opened)
    }

    @Test
    fun theDestructiveOverlayFlagIsCoveredByTheSignature() {
        val payload = """["blocked"]"""
        val recorded = sendImport(LanSyncImportType.BLOCKED_WORD, payload, overlay = true)

        // Signed as sent: verifies.
        verifyAsService(recorded, LanSyncReplayGuard())

        // A man-in-the-middle clearing `overlay` (or a peer replaying the body onto the
        // non-overlay route) changes the canonical string, so the MAC no longer matches.
        assertThrows(LanSyncAuthException::class.java) {
            verifyAsService(recorded, LanSyncReplayGuard(), overlayOverride = false)
        }
    }

    @Test
    fun aBodyCapturedFromOneRouteCannotBeReplayedOntoAnother() {
        val payload = """[{"siteId":"douyu","roomId":"9"}]"""
        val recorded = sendImport(LanSyncImportType.HISTORY, payload, overlay = false)

        assertThrows(LanSyncAuthException::class.java) {
            verifyAsService(
                recorded,
                LanSyncReplayGuard(),
                pathOverride = LanSyncImportType.PROFILE.path
            )
        }
    }

    @Test
    fun theSameRequestIsAcceptedOnlyOnce() {
        val recorded = sendImport(LanSyncImportType.TAG, """[]""", overlay = false)
        val guard = LanSyncReplayGuard()

        verifyAsService(recorded, guard)
        assertThrows(LanSyncAuthException::class.java) { verifyAsService(recorded, guard) }
    }

    @Test
    fun thePairingCodeNeverAppearsOnTheWire() {
        val recorded = sendImport(LanSyncImportType.FOLLOW, """[{"secret":"x"}]""", overlay = false)

        val header = recorded.getHeader(LanSyncAuth.HEADER_NAME).orEmpty()
        assertNotEquals("", header)
        assert(!header.contains(token)) { "pairing code leaked into the auth header" }
        assert(!recorded.bodyText.contains(token)) { "pairing code leaked into the body" }
        assert(!recorded.path.orEmpty().contains(token)) { "pairing code leaked into the URL" }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Drives a real client import and returns the request the peer would have received. */
    private fun sendImport(
        type: LanSyncImportType,
        payload: String,
        overlay: Boolean
    ): RecordedRequest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(peerInfoBody()))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":true}"""))
        server.start()

        return try {
            runBlocking {
                val peer = LanSyncClient(OkHttpClient()).connect("127.0.0.1", server.port, token)
                peer.import(type = type, payload = payload, overlay = overlay)
            }
            server.takeRequest() // /info
            server.takeRequest() // the import
        } finally {
            server.shutdown()
        }
    }

    private fun peerInfoBody(): String = com.mylive.app.core.sync.LanSyncProtocol.encodePeerInfo(
        paired = true,
        port = 23234,
        deviceId = "peer",
        name = "Peer"
    )

    /**
     * Reproduces `LanSyncService.serve` -> `authorizeRequest`: NanoHTTPD exposes the path without
     * the query string, the `overlay` flag as a query parameter, and the body as raw text.
     */
    private fun verifyAsService(
        recorded: RecordedRequest,
        replayGuard: LanSyncReplayGuard,
        pathOverride: String? = null,
        overlayOverride: Boolean? = null
    ): String {
        val url = requireNotNull(recorded.requestUrl)
        return LanSyncAuth.verifyProof(
            token = token,
            headerValue = recorded.getHeader(LanSyncAuth.HEADER_NAME),
            method = recorded.method.orEmpty(),
            path = pathOverride ?: url.encodedPath,
            overlay = overlayOverride ?: (url.queryParameter("overlay") == "1"),
            body = recorded.bodyText,
            replayGuard = replayGuard
        )
    }

    private val RecordedRequest.bodyText: String
        get() = body.clone().readUtf8()
}
