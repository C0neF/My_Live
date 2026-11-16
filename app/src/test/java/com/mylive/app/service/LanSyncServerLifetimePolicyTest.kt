package com.mylive.app.service

import android.app.Service
import com.mylive.app.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The LAN sync server listens only while the user is inside the sync flow.
 *
 * It used to be started when the sync screen opened and never stopped anywhere in the codebase;
 * with `START_STICKY` that left the HTTP and UDP ports open — and the pairing code valid — until
 * the process died. Opening a screen once is not consent to keep a write endpoint reachable for
 * the rest of the session.
 */
class LanSyncServerLifetimePolicyTest {

    @Test
    fun theSyncScreenItselfKeepsTheServerUp() {
        assertTrue(
            shouldKeepLanSyncServerRunning(listOf(Route.Index, Route.Sync, Route.LocalSync))
        )
    }

    @Test
    fun descendingIntoTheFlowKeepsItUp() {
        // Both are reached from the sync screen and return to it. Stopping in between would
        // rotate the pairing code out from under a peer that already scanned the QR.
        assertTrue(
            shouldKeepLanSyncServerRunning(listOf(Route.LocalSync, Route.SyncScan))
        )
        assertTrue(
            shouldKeepLanSyncServerRunning(
                listOf(Route.LocalSync, Route.SyncDevice(address = "192.168.1.2"))
            )
        )
    }

    @Test
    fun theDeviceScreenAloneStillCounts() {
        // Reachable from the sync hub via the QR scanner without ever passing through LocalSync.
        assertTrue(
            shouldKeepLanSyncServerRunning(
                listOf(Route.Index, Route.Sync, Route.SyncDevice(address = "192.168.1.2"))
            )
        )
    }

    @Test
    fun leavingTheFlowShutsTheServerDown() {
        // Back out to the sync hub, the index, or anywhere else: nothing is listening any more.
        assertFalse(shouldKeepLanSyncServerRunning(listOf(Route.Index, Route.Sync)))
        assertFalse(shouldKeepLanSyncServerRunning(listOf(Route.Index)))
        assertFalse(shouldKeepLanSyncServerRunning(emptyList()))
    }

    @Test
    fun unrelatedScreensNeverHoldItOpen() {
        assertFalse(
            shouldKeepLanSyncServerRunning(
                listOf(Route.Index, Route.Settings, Route.LiveRoomDetail(roomId = "1"))
            )
        )
    }

    @Test
    fun serviceDoesNotRequestStickyRestartAfterProcessDeath() {
        val service = LanSyncService()
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 1))
    }

    @Test
    fun routeLevelObserverStopsServerWhenLeavingScanOrDeviceViaDeepLink() {
        // Cold start outside sync flow does not emit spurious stop calls.
        assertFalse(
            shouldStopLanSyncOnRouteChange(
                keepRunning = false,
                wasFlowActive = false,
                isServiceRunning = false
            )
        )

        // Descending from LocalSync -> SyncScan keeps the server alive.
        assertFalse(
            shouldStopLanSyncOnRouteChange(
                keepRunning = shouldKeepLanSyncServerRunning(listOf(Route.LocalSync, Route.SyncScan)),
                wasFlowActive = true,
                isServiceRunning = true
            )
        )

        // Deep-link or navigation jump from SyncScan -> LiveRoomDetail stops the server even
        // though LocalSyncScreen was already disposed earlier.
        assertTrue(
            shouldStopLanSyncOnRouteChange(
                keepRunning = shouldKeepLanSyncServerRunning(
                    listOf(Route.Index, Route.LiveRoomDetail(roomId = "100"))
                ),
                wasFlowActive = true,
                isServiceRunning = true
            )
        )

        // Recreated navigation outside sync flow still stops a lingering service instance.
        assertTrue(
            shouldStopLanSyncOnRouteChange(
                keepRunning = false,
                wasFlowActive = false,
                isServiceRunning = true
            )
        )
    }
}
