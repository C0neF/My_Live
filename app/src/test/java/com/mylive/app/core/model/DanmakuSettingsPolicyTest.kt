package com.mylive.app.core.model

import org.junit.Assert.*
import org.junit.Test

class DanmakuSettingsPolicyTest {
    @Test
    fun legacyProfileValuesHaveTheSameMeaningAsMultiplierSettings() {
        assertEquals(1.0, DanmakuSettingsPolicy.sizeMultiplier(16.0), 0.0)
        assertEquals(1.25, DanmakuSettingsPolicy.sizeMultiplier(20.0), 0.0)
        assertEquals(1.0, DanmakuSettingsPolicy.speedMultiplier(10.0), 0.0)
        assertEquals(400, DanmakuSettingsPolicy.fontWeight(4))
        assertEquals(600, DanmakuSettingsPolicy.fontWeight(6))
    }

    @Test
    fun sliderValuesDoNotFallBackToADifferentSetting() {
        val size = DanmakuSettingsPolicy.sizeMultiplier(1.4f.toDouble())
        assertEquals("1.4x", DanmakuSettingsPolicy.multiplierText(size))
        assertTrue(DanmakuSettingsPolicy.sizeValues.any { DanmakuSettingsPolicy.sameValue(it, size) })
        assertEquals("80%", DanmakuSettingsPolicy.percentText(DanmakuSettingsPolicy.area(0.8)))
        assertTrue(DanmakuSettingsPolicy.areaValues.contains(0.8))
        assertEquals("70%", DanmakuSettingsPolicy.percentText(DanmakuSettingsPolicy.opacity(0.7f.toDouble())))
        assertEquals("3 px", DanmakuSettingsPolicy.strokeText(3.0))
    }

    @Test
    fun customValidValuesStayVisibleAndSelected() {
        val size = DanmakuSettingsPolicy.sizeMultiplier(22.0)
        val choices = DanmakuSettingsPolicy.choicesWithCurrent(DanmakuSettingsPolicy.sizeValues, size)
        assertEquals(1, choices.count { DanmakuSettingsPolicy.sameValue(it, size) })
        assertEquals("1.375x", DanmakuSettingsPolicy.multiplierText(size))
        assertEquals(22.0, size * DanmakuSettingsPolicy.BASE_FONT_SIZE, 0.0)
    }

    @Test
    fun normalizationIsFiniteBoundedAndIdempotent() {
        val functions = listOf(DanmakuSettingsPolicy::sizeMultiplier, DanmakuSettingsPolicy::speedMultiplier,
            DanmakuSettingsPolicy::area, DanmakuSettingsPolicy::opacity, DanmakuSettingsPolicy::strokeWidth)
        for (normalize in functions) for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, -100.0, 0.333, 1.4f.toDouble(), 100.0)) {
            val safe = normalize(value)
            assertTrue(safe.isFinite())
            assertEquals(safe, normalize(safe), 0.0)
        }
    }
}
