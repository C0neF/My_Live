package com.mylive.app.ui.screen.room.player

import android.os.SystemClock

/** Clock used by the surface danmaku pipeline for delay release and motion. */
fun interface DanmakuClock {
    fun uptimeMillis(): Long
}

object SystemDanmakuClock : DanmakuClock {
    override fun uptimeMillis(): Long = SystemClock.uptimeMillis()
}
