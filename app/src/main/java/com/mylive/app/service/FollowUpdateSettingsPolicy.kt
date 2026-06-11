package com.mylive.app.service

import kotlin.math.roundToInt

internal const val MIN_FOLLOW_UPDATE_INTERVAL_MINUTES = 15
internal const val DEFAULT_FOLLOW_UPDATE_CONCURRENCY = 8

internal fun followUpdateDurationOptions(): List<Int> =
    listOf(15, 30, 60, 120, 180, 240, 360)

internal fun coerceFollowUpdateIntervalMinutes(intervalMinutes: Int): Int =
    intervalMinutes.coerceAtLeast(MIN_FOLLOW_UPDATE_INTERVAL_MINUTES)

internal fun resolveFollowUpdateConcurrency(
    setting: Int,
    cpuCount: Int = Runtime.getRuntime().availableProcessors()
): Int {
    return if (setting == 0) {
        (cpuCount * 2.5).roundToInt().coerceIn(4, 20)
    } else {
        setting.coerceIn(1, 20)
    }
}
