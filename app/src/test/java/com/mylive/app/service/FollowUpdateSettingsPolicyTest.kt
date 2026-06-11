package com.mylive.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpdateSettingsPolicyTest {

    @Test
    fun durationOptionsDoNotOfferValuesBelowWorkManagerMinimum() {
        val options = followUpdateDurationOptions()

        assertFalse(options.contains(5))
        assertFalse(options.contains(10))
        assertTrue(options.all { it >= 15 })
    }

    @Test
    fun scheduleIntervalIsCoercedToWorkManagerMinimum() {
        assertEquals(15, coerceFollowUpdateIntervalMinutes(5))
        assertEquals(15, coerceFollowUpdateIntervalMinutes(15))
        assertEquals(60, coerceFollowUpdateIntervalMinutes(60))
    }

    @Test
    fun explicitConcurrencySettingIsHonored() {
        assertEquals(8, resolveFollowUpdateConcurrency(setting = 8, cpuCount = 4))
        assertEquals(12, resolveFollowUpdateConcurrency(setting = 12, cpuCount = 4))
    }

    @Test
    fun autoConcurrencyUsesCpuCountWithinBounds() {
        assertEquals(4, resolveFollowUpdateConcurrency(setting = 0, cpuCount = 1))
        assertEquals(10, resolveFollowUpdateConcurrency(setting = 0, cpuCount = 4))
        assertEquals(20, resolveFollowUpdateConcurrency(setting = 0, cpuCount = 16))
    }
}
