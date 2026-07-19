package com.mylive.app.ui.screen.room.player

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight in-process frame metrics seam for live-room hot paths.
 * Not a substitute for Perfetto; gives unit-testable counters and a place to
 * attach future JankStats / FrameMetrics observers without UI rewrite.
 *
 * Baseline scenarios (record with dumpsys gfxinfo / Perfetto before claiming wins):
 * 1) cold enter room 5s
 * 2) steady danmaku/chat 30–60s (or [LiveDanmakuReplaySource])
 * 3) background/foreground with background-play on/off
 * 4) portrait ↔ landscape/fullscreen
 * 5) leave room → Index return
 *
 * A PR may claim FPS/jank improvement only with same-device before/after numbers.
 */
data class LiveRoomFrameMetricsSnapshot(
    val frameCount: Int,
    val jankFrameCount: Int,
    val maxFrameDurationMs: Long,
    val totalFrameDurationMs: Long
) {
    val jankRatio: Double
        get() = if (frameCount == 0) 0.0 else jankFrameCount.toDouble() / frameCount
    val averageFrameDurationMs: Double
        get() = if (frameCount == 0) 0.0 else totalFrameDurationMs.toDouble() / frameCount
}

class LiveRoomFrameMetricsRecorder(
    private val jankThresholdMs: Long = 17L
) {
    private val frameCount = AtomicInteger(0)
    private val jankFrameCount = AtomicInteger(0)
    private val maxFrameDurationMs = AtomicLong(0L)
    private val totalFrameDurationMs = AtomicLong(0L)

    fun onFrameDurationMs(durationMs: Long) {
        if (durationMs < 0L) return
        frameCount.incrementAndGet()
        totalFrameDurationMs.addAndGet(durationMs)
        maxFrameDurationMs.updateAndGet { current -> maxOf(current, durationMs) }
        if (durationMs > jankThresholdMs) {
            jankFrameCount.incrementAndGet()
        }
    }

    fun snapshot(): LiveRoomFrameMetricsSnapshot {
        return LiveRoomFrameMetricsSnapshot(
            frameCount = frameCount.get(),
            jankFrameCount = jankFrameCount.get(),
            maxFrameDurationMs = maxFrameDurationMs.get(),
            totalFrameDurationMs = totalFrameDurationMs.get()
        )
    }

    fun reset() {
        frameCount.set(0)
        jankFrameCount.set(0)
        maxFrameDurationMs.set(0L)
        totalFrameDurationMs.set(0L)
    }
}

/** Classify whether a frame duration counts as jank for the default 60fps budget. */
internal fun isJankFrameDuration(durationMs: Long, thresholdMs: Long = 17L): Boolean {
    return durationMs > thresholdMs
}
