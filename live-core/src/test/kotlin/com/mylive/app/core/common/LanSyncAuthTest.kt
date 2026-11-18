package com.mylive.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * These tests stand in for the three ways the LAN sync server used to be defeated by anyone with a
 * packet capture on the same Wi-Fi:
 *
 * 1. the pairing code travelled in a header next to the ciphertext it unlocked,
 * 2. `?overlay=1` (which wipes the receiving device's data) was unauthenticated,
 * 3. nothing was request-specific, so a captured request could be replayed forever.
 *
 * They drive the real signing and verification code, not the shape of the source file.
 */
class LanSyncAuthTest {

    private val pairingCode = "7f4c1b2d9a0e4f5b8c3d2e1f0a9b8c7d"
    private val otherPairingCode = "0000000000000000000000000000dead"
    private val now = 1_700_000_000_000L
    private val sealedBody = "MLSYNC2.AAAAAAAAAAAAAAAA.BBBBBBBBBBBBBBBB"

    @Test
    fun aRequestSignedWithThePairingCodeIsAccepted() {
        val proof = proofFor(overlay = false)

        val context = LanSyncAuth.verifyProof(
            token = pairingCode,
            headerValue = proof.headerValue,
            method = "POST",
            path = "/sync/follow",
            overlay = false,
            body = sealedBody,
            nowMillis = now,
            replayGuard = LanSyncReplayGuard()
        )

        assertEquals(proof.requestContext, context)
    }

    /** The regression guard for the leak: nothing derived from the code may appear on the wire. */
    @Test
    fun theAuthorizationHeaderNeverCarriesThePairingCode() {
        val proof = proofFor(overlay = false)
        val wireBytes = "POST /sync/follow HTTP/1.1\n" +
            "${LanSyncAuth.HEADER_NAME}: ${proof.headerValue}\n\n" +
            sealedBody

        assertFalse(
            "the pairing code must never be transmitted; only a proof of possession may travel",
            wireBytes.contains(pairingCode)
        )
        assertFalse(wireBytes.contains("X-Sync-Token", ignoreCase = true))
        assertTrue(proof.headerValue.startsWith("${LanSyncAuth.SCHEME} "))
    }

    @Test
    fun flippingTheOverlayFlagInFlightIsRejected() {
        val proof = proofFor(overlay = false)

        // A man-in-the-middle rewrites the query string to ?overlay=1, which would wipe follows,
        // history and blocked words on the receiving device before importing.
        assertRejected {
            LanSyncAuth.verifyProof(
                token = pairingCode,
                headerValue = proof.headerValue,
                method = "POST",
                path = "/sync/follow",
                overlay = true,
                body = sealedBody,
                nowMillis = now,
                replayGuard = LanSyncReplayGuard()
            )
        }
    }

    @Test
    fun movingASignedRequestToAnotherEndpointIsRejected() {
        val proof = proofFor(overlay = false)

        assertRejected {
            LanSyncAuth.verifyProof(
                token = pairingCode,
                headerValue = proof.headerValue,
                method = "POST",
                path = "/sync/profile",
                overlay = false,
                body = sealedBody,
                nowMillis = now,
                replayGuard = LanSyncReplayGuard()
            )
        }
    }

    @Test
    fun swappingTheBodyIsRejected() {
        val proof = proofFor(overlay = false)

        assertRejected {
            LanSyncAuth.verifyProof(
                token = pairingCode,
                headerValue = proof.headerValue,
                method = "POST",
                path = "/sync/follow",
                overlay = false,
                body = sealedBody + "tampered",
                nowMillis = now,
                replayGuard = LanSyncReplayGuard()
            )
        }
    }

    @Test
    fun aCapturedRequestCannotBeReplayed() {
        val proof = proofFor(overlay = false)
        val replayGuard = LanSyncReplayGuard()

        LanSyncAuth.verifyProof(
            token = pairingCode,
            headerValue = proof.headerValue,
            method = "POST",
            path = "/sync/follow",
            overlay = false,
            body = sealedBody,
            nowMillis = now,
            replayGuard = replayGuard
        )

        assertRejected {
            LanSyncAuth.verifyProof(
                token = pairingCode,
                headerValue = proof.headerValue,
                method = "POST",
                path = "/sync/follow",
                overlay = false,
                body = sealedBody,
                nowMillis = now + 1_000,
                replayGuard = replayGuard
            )
        }
    }

    @Test
    fun requestsOutsideTheClockSkewWindowAreRejected() {
        val proof = proofFor(overlay = false)
        val tooOld = now + LanSyncAuth.MAX_CLOCK_SKEW_MS + 1
        val tooNew = now - LanSyncAuth.MAX_CLOCK_SKEW_MS - 1

        assertRejected { verifyAt(proof.headerValue, tooOld) }
        assertRejected { verifyAt(proof.headerValue, tooNew) }
    }

    @Test
    fun aProofSignedWithAnotherPairingCodeIsRejected() {
        val proof = LanSyncAuth.createProof(
            token = otherPairingCode,
            method = "POST",
            path = "/sync/follow",
            overlay = false,
            body = sealedBody,
            timestampMillis = now,
            nonce = "nonce-one"
        )

        assertRejected { verifyAt(proof.headerValue, now) }
    }

    @Test
    fun authorizationFailsClosedForMissingMalformedAndUnpairedRequests() {
        val proof = proofFor(overlay = false)

        assertRejected { verifyAt(null, now) }
        assertRejected { verifyAt("", now) }
        assertRejected { verifyAt("Bearer ${proof.headerValue}", now) }
        assertRejected { verifyAt("${LanSyncAuth.SCHEME} ts=abc nonce=x mac=y", now) }
        assertRejected { verifyAt("${LanSyncAuth.SCHEME} nonce=x mac=y", now) }
        assertRejected { verifyAt("${LanSyncAuth.SCHEME} ts=$now nonce= mac=", now) }
        // A nonce that is not base64url, and one long enough to be a memory-growth vector.
        assertRejected { verifyAt("${LanSyncAuth.SCHEME} ts=$now nonce=${"a".repeat(200)} mac=x", now) }

        // The service clears its pairing code when it stops: everything must be refused after that.
        assertRejected {
            LanSyncAuth.verifyProof(
                token = "",
                headerValue = proof.headerValue,
                method = "POST",
                path = "/sync/follow",
                overlay = false,
                body = sealedBody,
                nowMillis = now
            )
        }
    }

    @Test
    fun differentRequestsProduceDifferentProofs() {
        assertNotEquals(proofFor(overlay = false).headerValue, proofFor(overlay = true).headerValue)

        val first = LanSyncAuth.createProof(
            token = pairingCode,
            method = "POST",
            path = "/sync/follow",
            overlay = false,
            body = sealedBody
        )
        val second = LanSyncAuth.createProof(
            token = pairingCode,
            method = "POST",
            path = "/sync/follow",
            overlay = false,
            body = sealedBody
        )
        assertNotEquals("each request must get a fresh nonce", first.headerValue, second.headerValue)
    }

    /** End to end: sign, seal, verify, open — the way the client and the service actually pair up. */
    @Test
    fun theVerifiedContextIsTheOnlyOneThatOpensTheBody() {
        val payload = """[{"siteId":"bilibili","roomId":"300"}]"""
        val clientContext = LanSyncAuth.requestContext(
            method = "POST",
            path = "/sync/follow",
            overlay = true,
            timestampMillis = now,
            nonce = "nonce-two"
        )
        val sealed = LanSyncCrypto.encrypt(pairingCode, payload, clientContext)
        val proof = LanSyncAuth.createProof(
            token = pairingCode,
            method = "POST",
            path = "/sync/follow",
            overlay = true,
            body = sealed,
            timestampMillis = now,
            nonce = "nonce-two"
        )

        val serverContext = LanSyncAuth.verifyProof(
            token = pairingCode,
            headerValue = proof.headerValue,
            method = "POST",
            path = "/sync/follow",
            overlay = true,
            body = sealed,
            nowMillis = now,
            replayGuard = LanSyncReplayGuard()
        )

        assertEquals(payload, LanSyncCrypto.decrypt(pairingCode, sealed, serverContext))
    }

    @Test
    fun theReplayGuardExpiresEntriesAndFailsClosedWhenSaturated() {
        val guard = LanSyncReplayGuard(ttlMillis = 1_000, maxEntries = 2)

        assertTrue(guard.accept("a", now))
        assertFalse("the same nonce is never accepted twice", guard.accept("a", now))
        assertTrue(guard.accept("b", now))
        assertFalse("a saturated guard refuses rather than evicting and re-opening replays", guard.accept("c", now))

        // Once the window has passed the entries expire and the guard accepts again.
        assertTrue(guard.accept("c", now + 2_000))
        assertEquals(1, guard.size())
        assertTrue("an expired nonce is outside the accepted clock skew anyway", guard.accept("a", now + 2_000))
    }

    private fun proofFor(overlay: Boolean): LanSyncProof = LanSyncAuth.createProof(
        token = pairingCode,
        method = "POST",
        path = "/sync/follow",
        overlay = overlay,
        body = sealedBody,
        timestampMillis = now,
        nonce = "nonce-one"
    )

    private fun verifyAt(headerValue: String?, nowMillis: Long): String = LanSyncAuth.verifyProof(
        token = pairingCode,
        headerValue = headerValue,
        method = "POST",
        path = "/sync/follow",
        overlay = false,
        body = sealedBody,
        nowMillis = nowMillis,
        replayGuard = LanSyncReplayGuard()
    )

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("expected LanSyncAuthException")
        } catch (expected: LanSyncAuthException) {
            // Expected.
        }
    }
}
