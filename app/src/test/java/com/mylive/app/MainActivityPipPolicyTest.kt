package com.mylive.app

import com.mylive.app.ui.navigation.Route
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
    fun userLeaveHintChecksTheCurrentDestinationBeforeEnteringPip() {
        val source = File("src/main/java/com/mylive/app/MainActivity.kt").readText()
        val leaveHint = source.substringAfter("override fun onUserLeaveHint()")
            .substringBefore("companion object")

        assertTrue(leaveHint.contains("shouldEnterPipOnUserLeave("))
        assertTrue(leaveHint.contains("topRoute = navigator.backStack.lastOrNull()"))
        assertTrue(leaveHint.contains("enterPictureInPictureMode(params)"))
        // The old unguarded check must not come back.
        assertFalse(leaveHint.contains("if (LivePlaybackHostSignals.autoPipOnLeave) {"))
    }
}
