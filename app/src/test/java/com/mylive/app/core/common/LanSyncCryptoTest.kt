package com.mylive.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * LAN sync payloads cross a plain-HTTP Wi-Fi hop, so these tests exercise the real cipher: what a
 * sniffer sees, what a peer with the wrong code sees, and what happens when a request is rewritten
 * in flight.
 */
class LanSyncCryptoTest {

    private val pairingCode = "7f4c1b2d9a0e4f5b8c3d2e1f0a9b8c7d"
    private val otherPairingCode = "0000000000000000000000000000dead"

    private val context = LanSyncAuth.requestContext(
        method = "POST",
        path = "/sync/follow",
        overlay = false,
        timestampMillis = 1_700_000_000_000L,
        nonce = "nonce-one"
    )

    @Test
    fun sealedPayloadRoundTripsWithTheSamePairingCodeAndContext() {
        val payload = """[{"siteId":"bilibili","roomId":"300","userName":"streamer"}]"""

        val sealed = LanSyncCrypto.encrypt(pairingCode, payload, context)

        assertEquals(payload, LanSyncCrypto.decrypt(pairingCode, sealed, context))
    }

    @Test
    fun sealedPayloadDoesNotLeakPlaintextOnTheWire() {
        val sealed = LanSyncCrypto.encrypt(pairingCode, """{"userName":"streamer"}""", context)

        assertTrue(sealed.startsWith(LanSyncCrypto.ENVELOPE_PREFIX))
        assertFalse("the body a sniffer captures must not contain the payload", sealed.contains("streamer"))
        assertFalse("the body must never carry the pairing code either", sealed.contains(pairingCode))
    }

    @Test
    fun eachSealUsesAFreshIvSoIdenticalBodiesDiffer() {
        val payload = """{"userName":"streamer"}"""

        assertNotEquals(
            LanSyncCrypto.encrypt(pairingCode, payload, context),
            LanSyncCrypto.encrypt(pairingCode, payload, context)
        )
    }

    @Test
    fun anotherPairingCodeCannotOpenThePayload() {
        val sealed = LanSyncCrypto.encrypt(pairingCode, """{"userName":"streamer"}""", context)

        assertRejected { LanSyncCrypto.decrypt(otherPairingCode, sealed, context) }
    }

    @Test
    fun tamperedPayloadFailsAuthentication() {
        val sealed = LanSyncCrypto.encrypt(pairingCode, """{"userName":"streamer"}""", context)
        val cipherStart = sealed.lastIndexOf('.') + 1
        val flipped = sealed.substring(0, cipherStart) +
            (if (sealed[cipherStart] == 'A') 'B' else 'A') +
            sealed.substring(cipherStart + 1)

        assertRejected { LanSyncCrypto.decrypt(pairingCode, flipped, context) }
    }

    /**
     * `?overlay=1` wipes the receiving device's collection before importing. Binding it into the
     * AEAD associated data is what stops a man-in-the-middle from turning an append into a wipe.
     */
    @Test
    fun aBodySealedWithoutOverlayCannotBeOpenedAsAnOverlayImport() {
        val sealed = LanSyncCrypto.encrypt(pairingCode, """[]""", context)
        val overlayContext = LanSyncAuth.requestContext(
            method = "POST",
            path = "/sync/follow",
            overlay = true,
            timestampMillis = 1_700_000_000_000L,
            nonce = "nonce-one"
        )

        assertRejected { LanSyncCrypto.decrypt(pairingCode, sealed, overlayContext) }
    }

    @Test
    fun aBodySealedForOneEndpointCannotBeReplayedAtAnother() {
        val sealed = LanSyncCrypto.encrypt(pairingCode, """[]""", context)
        val otherEndpoint = LanSyncAuth.requestContext(
            method = "POST",
            path = "/sync/profile",
            overlay = false,
            timestampMillis = 1_700_000_000_000L,
            nonce = "nonce-one"
        )

        assertRejected { LanSyncCrypto.decrypt(pairingCode, sealed, otherEndpoint) }
    }

    @Test
    fun plainJsonBodyIsNotTreatedAsAnEnvelope() {
        assertFalse(LanSyncCrypto.isEncryptedEnvelope("""[{"siteId":"bilibili"}]"""))
        assertRejected { LanSyncCrypto.decrypt(pairingCode, """[{"siteId":"bilibili"}]""", context) }
    }

    @Test
    fun malformedEnvelopesAreRejected() {
        assertRejected { LanSyncCrypto.decrypt(pairingCode, "${LanSyncCrypto.ENVELOPE_PREFIX}only-one-part", context) }
        assertRejected { LanSyncCrypto.decrypt(pairingCode, "${LanSyncCrypto.ENVELOPE_PREFIX}..", context) }
        assertRejected { LanSyncCrypto.decrypt(pairingCode, "${LanSyncCrypto.ENVELOPE_PREFIX}not!base64.abc", context) }
        // A 4-byte IV is not a 96-bit GCM IV.
        assertRejected { LanSyncCrypto.decrypt(pairingCode, "${LanSyncCrypto.ENVELOPE_PREFIX}AAAA.AAAA", context) }
    }

    @Test
    fun anEmptyPairingCodeCannotSealOrOpenAnything() {
        assertRejected { LanSyncCrypto.encrypt("", "payload", context) }
        assertRejected { LanSyncCrypto.decrypt("", "${LanSyncCrypto.ENVELOPE_PREFIX}a.b", context) }
    }

    @Test
    fun payloadsMustAlwaysBeBoundToARequestContext() {
        assertRejected { LanSyncCrypto.encrypt(pairingCode, "payload", "") }
        assertRejected { LanSyncCrypto.encrypt(pairingCode, "payload", "   ") }
    }

    @Test
    fun derivedKeysAreTwoIndependentAes256KeysAndPairingCodeSpecific() {
        val keys = LanSyncCrypto.deriveKeys(pairingCode)
        val otherKeys = LanSyncCrypto.deriveKeys(otherPairingCode)

        assertEquals(32, keys.encryptionKey.size)
        assertEquals(32, keys.authenticationKey.size)
        assertFalse(
            "signing and encryption must not share key material",
            keys.encryptionKey.contentEquals(keys.authenticationKey)
        )
        assertFalse(keys.encryptionKey.contentEquals(otherKeys.encryptionKey))
        assertFalse(keys.authenticationKey.contentEquals(otherKeys.authenticationKey))
    }

    @Test
    fun keyDerivationIsStableForTheSamePairingCode() {
        val first = LanSyncCrypto.deriveKeys(pairingCode)
        LanSyncCrypto.clearCachedKeys()
        val second = LanSyncCrypto.deriveKeys(pairingCode)

        assertTrue(first.encryptionKey.contentEquals(second.encryptionKey))
        assertTrue(first.authenticationKey.contentEquals(second.authenticationKey))
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("expected LanSyncCryptoException")
        } catch (expected: LanSyncCryptoException) {
            // Expected.
        }
    }
}
