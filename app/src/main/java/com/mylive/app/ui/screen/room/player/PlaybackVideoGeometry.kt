package com.mylive.app.ui.screen.room.player

/** Display ratio, including non-square pixels and only rotation not already applied by Media3. */
internal fun playbackVideoAspectRatio(
    width: Int,
    height: Int,
    pixelWidthHeightRatio: Float = 1f,
    unappliedRotationDegrees: Int = 0
): Float? {
    if (width <= 0 || height <= 0 || !pixelWidthHeightRatio.isFinite() || pixelWidthHeightRatio <= 0f) {
        return null
    }
    val ratio = width.toFloat() * pixelWidthHeightRatio / height
    val displayRatio = if (unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270) {
        1f / ratio
    } else {
        ratio
    }
    return displayRatio.takeIf { it.isFinite() && it > 0f }
}

internal fun isPortraitVideo(aspectRatio: Float?): Boolean =
    aspectRatio != null && aspectRatio.isFinite() && aspectRatio > 0f && aspectRatio < 1f

internal fun playbackContainerAspectRatio(videoAspectRatio: Float?, scaleMode: Int): Float = when (scaleMode) {
    3 -> 4f / 3f
    4 -> 16f / 9f
    else -> videoAspectRatio?.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
}

/** Standard Android PiP accepts ratios between 1:2.39 and 2.39:1. */
internal fun playbackPipAspectRatio(videoAspectRatio: Float?): Float =
    // Stay inside the lower boundary after conversion to a finite-precision Rational.
    playbackContainerAspectRatio(videoAspectRatio, scaleMode = 0).coerceIn(0.42f, 2.39f)
