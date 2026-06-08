package com.mylive.app.core.site.douyin

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DouyinCrashPolicyTest {

    @Test
    fun douyinSigningBoundaryCatchesNativeLoaderErrors() {
        val source = File("src/main/java/com/mylive/app/core/site/douyin/DouyinSign.kt").readText()

        assertTrue(
            "QuickJS native loader failures are Throwable, not Exception, and must not crash the app",
            source.contains("catch (e: Throwable)")
        )
    }

    @Test
    fun liveRoomDanmakuCreationCannotCrashRoomEntry() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomViewModel.kt").readText()

        assertTrue(
            "site.getDanmaku() must be inside a Throwable boundary so Douyin danmaku failures do not crash playback",
            source.contains("Failed to create danmaku")
        )
        assertTrue(
            "danmaku start failures can include native loader Errors and must not escape",
            source.contains("catch (e: Throwable)")
        )
    }
}
