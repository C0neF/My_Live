package com.mylive.app.ui.screen.room.player

import android.graphics.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Process-scoped host signals for playback that Activity must observe without
 * LiveRoom knowing MainActivity internals.
 *
 * Replaces scattered `MainActivity` static mutations for auto-PiP eligibility.
 */
object LivePlaybackHostSignals {
    private val legacyOwner = Any()
    private val autoPipOwners = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())

    private val _autoPipOnLeave = MutableStateFlow(false)

    /**
     * Observable form of [autoPipOnLeave].
     *
     * The Activity needs to *react* to eligibility changes, not just read the flag when the user
     * happens to leave: from Android 12 the reliable way into picture-in-picture is
     * `setAutoEnterEnabled`, which has to be armed ahead of time because `onUserLeaveHint` is not
     * delivered on every path out of the app (notably gesture navigation to recents).
     */
    val autoPipOnLeaveFlow: StateFlow<Boolean> = _autoPipOnLeave.asStateFlow()

    val autoPipOnLeave: Boolean
        get() = _autoPipOnLeave.value

    /**
     * Bounds of the video surface in window coordinates, used as the picture-in-picture source
     * rect so the system can animate from the actual video instead of cross-fading the whole
     * window. Null whenever no player surface is on screen.
     */
    @Volatile
    var sourceRectHint: Rect? = null
        private set

    fun setAutoPipOnLeave(active: Boolean) {
        setAutoPipOnLeave(legacyOwner, active)
    }

    @Synchronized
    fun setAutoPipOnLeave(owner: Any, active: Boolean) {
        if (active) {
            autoPipOwners.add(owner)
        } else {
            autoPipOwners.remove(owner)
        }
        _autoPipOnLeave.value = autoPipOwners.isNotEmpty()
    }

    fun setSourceRectHint(rect: Rect?) {
        sourceRectHint = rect
    }
}
