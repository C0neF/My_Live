package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.LiveMessageColor
import kotlin.math.pow
import kotlin.math.roundToInt

internal data class ChatMessageColorPolicy(
    val applyMessageColorToUserName: Boolean,
    val applyMessageColorToText: Boolean
)

internal fun resolveChatMessageColorPolicy(
    color: LiveMessageColor
): ChatMessageColorPolicy {
    val hasCustomMessageColor = color != LiveMessageColor.WHITE &&
        color != LiveMessageColor(r = 0, g = 0, b = 0)
    return ChatMessageColorPolicy(
        applyMessageColorToUserName = false,
        applyMessageColorToText = hasCustomMessageColor
    )
}

/**
 * WCAG-ish relative luminance in [0, 1] for sRGB 0–255 channels.
 */
internal fun relativeLuminance(color: LiveMessageColor): Double {
    fun channel(c: Int): Double {
        val s = (c.coerceIn(0, 255)) / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.r) +
        0.7152 * channel(color.g) +
        0.0722 * channel(color.b)
}

/**
 * Contrast ratio of [foreground] against a solid white surface.
 * Yellow (~#FFFF00) scores ~1.07 and is unreadable on pure white chat backgrounds.
 */
internal fun contrastRatioAgainstWhite(foreground: LiveMessageColor): Double {
    return contrastRatio(foreground, backgroundLuminance = 1.0)
}

internal fun contrastRatio(
    foreground: LiveMessageColor,
    backgroundLuminance: Double
): Double {
    val foregroundLuminance = relativeLuminance(foreground)
    val background = backgroundLuminance.coerceIn(0.0, 1.0)
    val lighter = maxOf(foregroundLuminance, background)
    val darker = minOf(foregroundLuminance, background)
    return (lighter + 0.05) / (darker + 0.05)
}

/**
 * Keep platform danmaku hues, but darken light colors (yellow / lime / cyan …)
 * when the chat list sits on a light surface so body text stays readable.
 *
 * Dark theme leaves the original color untouched — light text is expected there.
 */
internal fun resolveReadableChatMessageColor(
    color: LiveMessageColor,
    backgroundLuminance: Double,
    /** ~3.0 is suitable for the relatively large chat text used by this screen. */
    minContrast: Double = 3.0
): LiveMessageColor {
    val background = backgroundLuminance.coerceIn(0.0, 1.0)
    if (contrastRatio(color, background) >= minContrast) return color

    val blackContrast = (background + 0.05) / 0.05
    val whiteContrast = 1.05 / (background + 0.05)
    val target = if (whiteContrast >= blackContrast) 255 else 0

    // Find the smallest adjustment toward black/white that reaches the target contrast.
    var lo = 0.0
    var hi = 1.0
    var best = blendLiveMessageColor(color, target, hi)
    repeat(14) {
        val mid = (lo + hi) / 2.0
        val candidate = blendLiveMessageColor(color, target, mid)
        if (contrastRatio(candidate, background) >= minContrast) {
            best = candidate
            hi = mid
        } else {
            lo = mid
        }
    }
    return best
}

internal fun blendLiveMessageColor(
    color: LiveMessageColor,
    targetChannel: Int,
    amount: Double
): LiveMessageColor {
    val target = targetChannel.coerceIn(0, 255)
    val ratio = amount.coerceIn(0.0, 1.0)
    fun blend(channel: Int): Int =
        (channel + (target - channel) * ratio).roundToInt().coerceIn(0, 255)

    return LiveMessageColor(
        r = blend(color.r),
        g = blend(color.g),
        b = blend(color.b)
    )
}
