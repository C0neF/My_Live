package com.mylive.app.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanSyncProtocolTest {

    @Test
    fun discoveryMessagesRoundTripThroughOneParser() {
        val hello = LanSyncProtocol.decodeDiscoveryMessage(
            LanSyncProtocol.encodeDiscoveryHello("device-1")
        )
        val peer = LanSyncProtocol.decodeDiscoveryMessage(
            LanSyncProtocol.encodeDiscoveryPeer("device-2", "Living room")
        )

        assertEquals(LanSyncDiscoveryMessage.Hello("device-1"), hello)
        assertEquals(
            LanSyncDiscoveryMessage.Peer(
                deviceId = "device-2",
                name = "Living room",
                platform = "android",
                protocol = LanSyncProtocol.CURRENT_PROTOCOL
            ),
            peer
        )
        assertNull(LanSyncProtocol.decodeDiscoveryMessage("not-json"))
    }

    @Test
    fun peerInfoNegotiatesTheCurrentProtocol() {
        val info = LanSyncProtocol.decodePeerInfo(
            LanSyncProtocol.encodePeerInfo(
                paired = true,
                port = 23234,
                deviceId = "device-1",
                name = "Pixel",
                appVersion = "2.0",
                address = "192.168.1.2"
            )
        )

        assertEquals(LanSyncProtocol.CURRENT_PROTOCOL, info.protocol)
        assertTrue(info.paired)
        assertEquals("device-1", info.deviceId)
        assertEquals("Pixel", info.name)
    }

    @Test
    fun peerInfoRejectsUnknownOrMissingProtocol() {
        assertThrows(LanSyncProtocolException::class.java) {
            LanSyncProtocol.decodePeerInfo("""{"protocol":"MLSYNC3","paired":true}""")
        }
        assertThrows(LanSyncProtocolException::class.java) {
            LanSyncProtocol.decodePeerInfo("""{"paired":true}""")
        }
    }

    @Test
    fun a401KeepsTheReasonThePeerGave() {
        // All three are 401s, but only one of them is fixed by re-scanning the QR code. Reporting
        // a clock skew as "wrong pairing code" sent users round a loop that could not help.
        val stale = LanSyncProtocol.unauthorizedException("""{"message":"stale sync request"}""")
        val replay = LanSyncProtocol.unauthorizedException("""{"message":"replayed sync request"}""")
        val unpaired = LanSyncProtocol.unauthorizedException(
            """{"message":"unauthorized: pairing required"}"""
        )

        assertTrue(stale.message.orEmpty().contains("时间"))
        assertTrue(replay.message.orEmpty().contains("重复"))
        assertEquals(LanSyncProtocol.PAIRING_REQUIRED_MESSAGE, unpaired.message)
    }

    @Test
    fun a401WithNoUsableBodyFallsBackToThePairingHint() {
        assertEquals(
            LanSyncProtocol.PAIRING_REQUIRED_MESSAGE,
            LanSyncProtocol.unauthorizedException(null).message
        )
        assertEquals(
            LanSyncProtocol.PAIRING_REQUIRED_MESSAGE,
            LanSyncProtocol.unauthorizedException("not json at all").message
        )
        assertEquals(
            LanSyncProtocol.PAIRING_REQUIRED_MESSAGE,
            LanSyncProtocol.unauthorizedException("{").message
        )
    }

    @Test
    fun importPathsAndJobStatesAreTyped() {
        assertEquals(
            LanSyncImportType.FOLLOW,
            LanSyncImportType.fromPath("/sync/follow")
        )
        assertNull(LanSyncImportType.fromPath("/sync/unknown"))
        assertEquals(
            LanSyncJobStatus(LanSyncJobState.RUNNING, "running"),
            LanSyncProtocol.decodeJobStatus(
                """{"status":true,"state":"running","message":"running"}"""
            )
        )
    }
}
