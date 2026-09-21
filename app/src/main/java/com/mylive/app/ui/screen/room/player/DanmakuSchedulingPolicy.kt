package com.mylive.app.ui.screen.room.player

/** Keeps the trailing gap safe until the leading message has fully left the viewport. */
internal fun canFollowDanmaku(
    leadingRight: Float,
    leadingSpeed: Float,
    followingLeft: Float,
    followingSpeed: Float,
    minimumGap: Float
): Boolean {
    val gap = followingLeft - leadingRight
    if (gap < minimumGap) return false
    if (followingSpeed <= leadingSpeed) return true
    if (leadingSpeed <= 0f) return false
    val remainingTime = leadingRight.coerceAtLeast(0f) / leadingSpeed
    return gap - minimumGap >= (followingSpeed - leadingSpeed) * remainingTime
}
