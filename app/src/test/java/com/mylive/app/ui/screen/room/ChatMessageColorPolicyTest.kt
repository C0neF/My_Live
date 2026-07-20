package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.LiveMessageColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageColorPolicyTest {

    @Test
    fun customDanmakuColorAppliesToMessageTextOnly() {
        val policy = resolveChatMessageColorPolicy(
            LiveMessageColor(r = 255, g = 0, b = 0)
        )

        assertFalse(policy.applyMessageColorToUserName)
        assertTrue(policy.applyMessageColorToText)
    }

    @Test
    fun defaultWhiteDoesNotOverrideChatTextColor() {
        val policy = resolveChatMessageColorPolicy(LiveMessageColor.WHITE)

        assertFalse(policy.applyMessageColorToUserName)
        assertFalse(policy.applyMessageColorToText)
    }

    @Test
    fun pureYellowHasPoorContrastAgainstWhite() {
        val yellow = LiveMessageColor(r = 255, g = 255, b = 0)
        assertTrue(contrastRatioAgainstWhite(yellow) < 2.0)
        assertTrue(relativeLuminance(yellow) > 0.8)
    }

    @Test
    fun lightThemeDarkensYellowForReadableChatText() {
        val yellow = LiveMessageColor(r = 255, g = 255, b = 0)
        val adjusted = resolveReadableChatMessageColor(yellow, backgroundLuminance = 1.0)

        assertNotEquals(yellow, adjusted)
        assertTrue(contrastRatioAgainstWhite(adjusted) >= 3.0)
        // Hue preserved: still yellow-ish (R and G dominant, B low).
        assertTrue(adjusted.r > adjusted.b)
        assertTrue(adjusted.g > adjusted.b)
        assertTrue(adjusted.r < yellow.r || adjusted.g < yellow.g)
    }

    @Test
    fun darkThemeKeepsOriginalYellow() {
        val yellow = LiveMessageColor(r = 255, g = 255, b = 0)
        val adjusted = resolveReadableChatMessageColor(yellow, backgroundLuminance = 0.0)
        assertEquals(yellow, adjusted)
    }

    @Test
    fun lightThemeLeavesAlreadyDarkRedUnchanged() {
        val red = LiveMessageColor(r = 255, g = 0, b = 0)
        val adjusted = resolveReadableChatMessageColor(red, backgroundLuminance = 1.0)
        assertEquals(red, adjusted)
        assertTrue(contrastRatioAgainstWhite(red) >= 3.0)
    }

    @Test
    fun lightThemeDarkensPaleCyan() {
        val cyan = LiveMessageColor(r = 0, g = 255, b = 255)
        val adjusted = resolveReadableChatMessageColor(cyan, backgroundLuminance = 1.0)
        assertTrue(contrastRatioAgainstWhite(adjusted) >= 3.0)
        assertTrue(adjusted.g < cyan.g || adjusted.b < cyan.b)
    }

    @Test
    fun actualDarkSurfaceLightensLowContrastBlueRegardlessOfSystemTheme() {
        val blue = LiveMessageColor(r = 0, g = 0, b = 160)
        val adjusted = resolveReadableChatMessageColor(blue, backgroundLuminance = 0.01)

        assertNotEquals(blue, adjusted)
        assertTrue(contrastRatio(adjusted, backgroundLuminance = 0.01) >= 3.0)
        assertTrue(adjusted.r > blue.r || adjusted.g > blue.g || adjusted.b > blue.b)
    }
}
