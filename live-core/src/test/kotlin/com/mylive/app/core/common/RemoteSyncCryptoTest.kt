package com.mylive.app.core.common

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSyncCryptoTest {

    @Test
    fun inviteAndQrFormatsRoundTripAndRejectBareRoomId() {
        val key = RemoteSyncCrypto.generateRoomKey()
        val inviteCode = RemoteSyncCrypto.formatInviteCode("abc123", key)
        val qrPayload = RemoteSyncCrypto.buildQrPayload("abc123", key)

        val fromCode = RemoteSyncCrypto.parseInvite(inviteCode)
        val fromCompact = RemoteSyncCrypto.parseInvite("abc123${key.lowercase()}")
        val fromQr = RemoteSyncCrypto.parseInvite(qrPayload)

        assertNotNull(fromCode)
        assertEquals("ABC123", fromCode?.roomId)
        assertEquals(key, fromCode?.roomKey)
        assertEquals(fromCode, fromCompact)
        assertEquals(fromCode, fromQr)

        // Bare 6-char room ID without key must be rejected so peers never join unencrypted.
        assertNull(RemoteSyncCrypto.parseInvite("ABC123"))
    }

    @Test
    fun sealsAndOpensPayloadsWithoutExposingPlaintextCookies() {
        val key = RemoteSyncCrypto.generateRoomKey()
        val secretCookie = """{"cookie":"SESSDATA=super-secret-value"}"""

        val wirePayload = RemoteSyncCrypto.encryptPayload(
            roomId = "ROOM42",
            roomKey = key,
            channel = "SendBiliAccount",
            overlay = true,
            content = secretCookie
        )

        val wireString = wirePayload.toString()
        assertFalse(wireString.contains("SESSDATA"))
        assertFalse(wireString.contains("super-secret-value"))
        assertTrue(RemoteSyncCrypto.isEncryptedEnvelope(wirePayload.getString("envelope")))

        val opened = RemoteSyncCrypto.decryptPayload(
            roomId = "ROOM42",
            roomKey = key,
            channel = "biliAccountReceived",
            wirePayload = wirePayload
        )
        assertTrue(opened.overlay)
        assertEquals(secretCookie, opened.content)
    }

    @Test
    fun rejectsPlaintextWrongKeyCrossRoomAndCrossChannelReplay() {
        val key = RemoteSyncCrypto.generateRoomKey()
        val wrongKey = RemoteSyncCrypto.generateRoomKey()
        val sealed = RemoteSyncCrypto.encryptPayload(
            roomId = "ROOM01",
            roomKey = key,
            channel = "sendFavorite",
            overlay = false,
            content = """{"follows":[],"tags":[]}"""
        )

        // Plaintext downgrade attempt
        assertThrows(RemoteSyncCryptoException::class.java) {
            RemoteSyncCrypto.decryptPayload(
                roomId = "ROOM01",
                roomKey = key,
                channel = "favoriteReceived",
                wirePayload = JSONObject().put("overlay", false).put("content", "[]")
            )
        }

        // Wrong pairing key
        assertThrows(RemoteSyncCryptoException::class.java) {
            RemoteSyncCrypto.decryptPayload(
                roomId = "ROOM01",
                roomKey = wrongKey,
                channel = "favoriteReceived",
                wirePayload = sealed
            )
        }

        // Cross-room replay
        assertThrows(RemoteSyncCryptoException::class.java) {
            RemoteSyncCrypto.decryptPayload(
                roomId = "ROOM02",
                roomKey = key,
                channel = "favoriteReceived",
                wirePayload = sealed
            )
        }

        // Cross-channel substitution (e.g. replaying favorite ciphertext as biliAccountReceived)
        assertThrows(RemoteSyncCryptoException::class.java) {
            RemoteSyncCrypto.decryptPayload(
                roomId = "ROOM01",
                roomKey = key,
                channel = "biliAccountReceived",
                wirePayload = sealed
            )
        }
    }
}
