package com.mylive.app.ui.screen.room.player

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

    @Volatile
    var autoPipOnLeave: Boolean = false
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
        autoPipOnLeave = autoPipOwners.isNotEmpty()
    }
}
