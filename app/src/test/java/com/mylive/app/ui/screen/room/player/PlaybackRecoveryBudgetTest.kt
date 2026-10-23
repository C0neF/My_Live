package com.mylive.app.ui.screen.room.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackRecoveryBudgetTest {
    @Test fun changingSourcesDoesNotReplenishTheSessionBudget() {
        val budget = PlaybackRecoveryBudget()
        for (token in 1L..3L) {
            assertTrue(budget.tryAcquire())
            budget.observe(token * 1000L, token, 0, playing = true, videoFrames = token)
        }
        assertEquals(3, budget.attempts)
        assertFalse(budget.tryAcquire())
    }

    @Test fun readyStateAloneDoesNotResetFailedAttempts() {
        val budget = PlaybackRecoveryBudget()
        repeat(3) { assertTrue(budget.tryAcquire()) }
        for (now in 0L..60_000L step 3_000L) budget.observe(now, 1, 0, true, 0)
        assertFalse(budget.tryAcquire())
    }

    @Test fun thirtySecondsOfContinuousVideoProgressRestoresTheBudget() {
        val budget = PlaybackRecoveryBudget()
        repeat(3) { budget.tryAcquire() }
        for (second in 0L..31L) budget.observe(second * 1000L, 1, second * 1000L, true, second * 30L)
        assertEquals(0, budget.attempts)
        assertTrue(budget.tryAcquire())
    }

    @Test fun aBufferingInterruptionRestartsTheStabilityWindow() {
        val budget = PlaybackRecoveryBudget()
        repeat(3) { budget.tryAcquire() }
        for (second in 0L..20L) budget.observe(second * 1000L, 1, second * 1000L, true, second)
        budget.observe(21_000, 1, 20_000, false, 20)
        for (second in 22L..45L) budget.observe(second * 1000L, 1, second * 1000L, true, second)
        assertFalse(budget.tryAcquire())
    }

    @Test fun userSelectionExplicitlyStartsAFreshBudget() {
        val budget = PlaybackRecoveryBudget()
        repeat(3) { budget.tryAcquire() }
        budget.resetForUserSelection()
        assertTrue(budget.tryAcquire())
        assertEquals(1, budget.attempts)
    }

    @Test fun liveWindowPositionDiscontinuitiesStillCountWhenFramesAdvance() {
        val budget = PlaybackRecoveryBudget()
        budget.tryAcquire()
        for (second in 0L..31L) budget.observe(second * 1000L, 1, (second % 5) * 1000L, true, second * 30L)
        assertEquals(0, budget.attempts)
    }
}
