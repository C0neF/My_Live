package com.mylive.app.ui.screen.room.player

/**
 * Process-scoped host signals for playback that Activity must observe without
 * LiveRoom knowing MainActivity internals.
 *
 * Replaces scattered `MainActivity` static mutations for auto-PiP eligibility.
 */
object LivePlaybackHostSignals {
    @Volatile
    var autoPipOnLeave: Boolean = false
        private set

    fun setAutoPipOnLeave(active: Boolean) {
        autoPipOnLeave = active
    }
}
