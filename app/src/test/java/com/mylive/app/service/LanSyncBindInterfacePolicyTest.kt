package com.mylive.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sync server binds to one interface and advertises that address in the pairing QR.
 *
 * Picking "the first non-loopback IPv4" published the endpoints over VPN tunnels and USB/Bluetooth
 * tethering links — networks well outside the same-Wi-Fi trust boundary the pairing model assumes,
 * and exactly what moving off a `0.0.0.0` bind was meant to prevent.
 */
class LanSyncBindInterfacePolicyTest {

    @Test
    fun vpnTunnelsAreNeverBoundTo() {
        listOf("tun0", "tun1", "tap0", "ppp0", "ipsec0", "wg0", "nordlynx").forEach { name ->
            assertTrue("expected $name to be excluded", isExcludedSyncInterface(name))
        }
    }

    @Test
    fun cellularInterfacesAreNeverBoundTo() {
        listOf("rmnet0", "rmnet_data2", "ccmni0", "pdp_ip0", "clat4").forEach { name ->
            assertTrue("expected $name to be excluded", isExcludedSyncInterface(name))
        }
    }

    @Test
    fun tetheringAndUsbLinksAreNeverBoundTo() {
        listOf("ap0", "swlan0", "rndis0", "usb0", "bt-pan").forEach { name ->
            assertTrue("expected $name to be excluded", isExcludedSyncInterface(name))
        }
    }

    @Test
    fun realLanInterfacesAreAllowedAndPreferred() {
        listOf("wlan0", "wlan1", "eth0").forEach { name ->
            assertFalse("expected $name to be allowed", isExcludedSyncInterface(name))
            assertTrue("expected $name to be preferred", isPreferredSyncInterface(name))
        }
    }

    @Test
    fun anUnknownInterfaceIsUsableButNotPreferred() {
        // Unknown names must not be excluded outright — an unrecognised OEM Wi-Fi interface
        // should still work, just after the ones we are sure about.
        assertFalse(isExcludedSyncInterface("p2p0-oem"))
        assertFalse(isPreferredSyncInterface("p2p0-oem"))
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertTrue(isExcludedSyncInterface("TUN0"))
        assertTrue(isPreferredSyncInterface("WLAN0"))
    }
}
