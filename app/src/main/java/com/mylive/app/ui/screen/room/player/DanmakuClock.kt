package com.mylive.app.ui.screen.room.player

import android.os.SystemClock
import kotlin.random.Random

/** Clock used by the surface danmaku pipeline for delay release and motion. */
fun interface DanmakuClock {
    fun uptimeMillis(): Long
}

object SystemDanmakuClock : DanmakuClock {
    override fun uptimeMillis(): Long = SystemClock.uptimeMillis()
}

/** Track selection when every lane is congested. */
fun interface DanmakuOverflowTrackPicker {
    fun pick(trackCount: Int): Int
}

object RandomDanmakuOverflowTrackPicker : DanmakuOverflowTrackPicker {
    override fun pick(trackCount: Int): Int {
        if (trackCount <= 0) return 0
        return Random.nextInt(trackCount)
    }
}

class FixedDanmakuOverflowTrackPicker(private val track: Int = 0) : DanmakuOverflowTrackPicker {
    override fun pick(trackCount: Int): Int {
        if (trackCount <= 0) return 0
        return track.coerceIn(0, trackCount - 1)
    }
}

/** Mutable clock for deterministic schedule tests. */
class FakeDanmakuClock(private var nowMs: Long = 0L) : DanmakuClock {
    override fun uptimeMillis(): Long = nowMs
    fun advance(deltaMs: Long) {
        nowMs += deltaMs
    }
    fun set(nowMs: Long) {
        this.nowMs = nowMs
    }
}
