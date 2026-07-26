package com.mylive.app

import android.os.Build
import com.mylive.app.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto-PiP must be scoped to the live room detail screen: leaving for the launcher from any other
 * destination has to close/background the app normally instead of shrinking it into a floating
 * window.
 */
class MainActivityPipPolicyTest {

    @Test
    fun liveRoomDetailIsTheOnlyDestinationThatEntersPipOnLeave() {
        assertTrue(
            shouldEnterPipOnUserLeave(
                autoPipOnLeave = true,
                topRoute = Route.LiveRoomDetail(roomId = "123", siteId = "douyu")
            )
        )
    }

    @Test
    fun otherDestinationsNeverEnterPipEvenWhenTheHostSignalIsStillOn() {
        val otherRoutes = listOf(
            Route.Index,
            Route.Search,
            Route.History,
            Route.Settings,
            Route.SettingsPlay,
            Route.Sync,
            Route.LocalSync,
            Route.Tools,
            Route.CategoryDetail(siteId = "douyu", categoryId = "1"),
            Route.RemoteSyncRoom(roomId = "123")
        )

        for (route in otherRoutes) {
            assertFalse(
                "$route must not trigger picture-in-picture",
                shouldEnterPipOnUserLeave(autoPipOnLeave = true, topRoute = route)
            )
        }
        assertFalse(shouldEnterPipOnUserLeave(autoPipOnLeave = true, topRoute = null))
    }

    @Test
    fun disabledPreferenceKeepsTheLiveRoomOutOfPipToo() {
        assertFalse(
            shouldEnterPipOnUserLeave(
                autoPipOnLeave = false,
                topRoute = Route.LiveRoomDetail(roomId = "123")
            )
        )
    }

    @Test
    fun modernAndroidArmsAutoEnterInsteadOfWaitingForUserLeaveHint() {
        // onUserLeaveHint is not delivered on every way out of the app; from Android 12 the
        // reliable route is arming setAutoEnterEnabled ahead of time.
        assertEquals(PipEntryMechanism.AutoEnter, pipEntryMechanism(Build.VERSION_CODES.S))
        assertEquals(
            PipEntryMechanism.AutoEnter,
            pipEntryMechanism(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        )
    }

    @Test
    fun olderAndroidStillEntersFromUserLeaveHint() {
        // minSdk is 26, and 26-30 have no auto-enter flag at all.
        assertEquals(PipEntryMechanism.ManualOnUserLeave, pipEntryMechanism(Build.VERSION_CODES.O))
        assertEquals(PipEntryMechanism.ManualOnUserLeave, pipEntryMechanism(Build.VERSION_CODES.R))
    }

    @Test
    fun theDestinationGateAppliesToBothEntryMechanisms() {
        // Whichever mechanism the device uses, eligibility is the same predicate: the user has
        // to actually be on a live room, with the preference on.
        assertTrue(
            shouldEnterPipOnUserLeave(
                autoPipOnLeave = true,
                topRoute = Route.LiveRoomDetail(roomId = "123")
            )
        )
        assertFalse(shouldEnterPipOnUserLeave(autoPipOnLeave = true, topRoute = Route.Index))
        assertFalse(shouldEnterPipOnUserLeave(autoPipOnLeave = true, topRoute = null))
    }
}
