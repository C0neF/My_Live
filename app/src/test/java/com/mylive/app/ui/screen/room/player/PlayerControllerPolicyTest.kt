package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class PlayerControllerPolicyTest {

    @Test
    fun missingPlaybackUrlReturnsUserVisibleError() {
        assertEquals("暂无可播放线路", missingPlaybackUrlError(emptyList(), startIndex = 0))
        assertEquals("暂无可播放线路", missingPlaybackUrlError(listOf("https://example.com/live.m3u8"), startIndex = 1))
    }

    @Test
    fun validPlaybackUrlHasNoMissingUrlError() {
        assertNull(missingPlaybackUrlError(listOf("https://example.com/live.m3u8"), startIndex = 0))
    }

    @Test
    fun rtmpUrlsAreRejectedWithoutNativeRtmpExtension() {
        assertEquals("当前版本暂不支持 RTMP 线路", unsupportedPlaybackUrlError("rtmp://example.com/live"))
        assertNull(unsupportedPlaybackUrlError("https://example.com/live.flv"))
    }

    @Test
    fun playerDoesNotReferenceMedia3RtmpNativeExtension() {
        val source = readMainSource("com/mylive/app/ui/screen/room/player/PlayerController.kt")

        assertFalse(source.contains("RtmpDataSource"))
        assertFalse(source.contains("androidx.media3.datasource.rtmp"))
    }

    @Test
    fun forceHttpsKeepsOriginalHttpUrlAsFallback() {
        assertEquals(
            listOf(
                "https://example.com/live.flv",
                "http://example.com/live.flv",
                "https://cdn.example.com/live.m3u8"
            ),
            buildPlaybackUrlCandidates(
                listOf(
                    "http://example.com/live.flv",
                    "https://cdn.example.com/live.m3u8"
                ),
                forceHttps = true
            )
        )
    }

    @Test
    fun forceHttpsDisabledKeepsOriginalUrlOrder() {
        assertEquals(
            listOf("http://example.com/live.flv", "https://cdn.example.com/live.m3u8"),
            buildPlaybackUrlCandidates(
                listOf("http://example.com/live.flv", "https://cdn.example.com/live.m3u8"),
                forceHttps = false
            )
        )
    }

    private fun readMainSource(relativePath: String): String {
        val candidates = listOf(
            File("src/main/java", relativePath),
            File("My_Live/app/src/main/java", relativePath)
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Cannot find source file for $relativePath")
        return file.readText()
    }
}
