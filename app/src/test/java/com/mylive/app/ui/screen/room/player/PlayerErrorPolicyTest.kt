package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayerErrorPolicyTest {

    @Test
    fun mediaCodecRendererErrorsUseCompactChineseMessage() {
        val rawError = "MediaCodecVideoRenderer error, index=0, format=Format(null, null, null, video/avc, avc1.42C014, -1, null, [100, 178, -1.0, null], [-1, -1]), format_supported=YES"

        assertTrue(isVideoDecoderPlaybackError(rawError))
        assertEquals(
            "视频解码失败，已尝试切换解码方式，请刷新重试",
            userVisiblePlaybackError(rawError)
        )
    }

    @Test
    fun longTechnicalPlaybackErrorsDoNotLeakToUi() {
        val rawError = "Source error with a very long internal stack trace and media period detail that should not be shown directly to users"

        val visible = userVisiblePlaybackError(rawError)

        assertEquals("播放失败，请刷新重试", visible)
        assertFalse(visible.contains("internal", ignoreCase = true))
    }

    @Test
    fun playerControllerEnablesDecoderFallbackAndSoftwareRetry() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerController.kt").readText()

        assertTrue(source.contains("setEnableDecoderFallback(true)"))
        assertTrue(source.contains("retryCurrentUrlWithSoftwareDecoder()"))
        assertTrue(source.contains("softwareDecoderFallbackAttempted"))
        assertTrue(source.contains("userVisiblePlaybackError("))
    }

    @Test
    fun playerRequestsFreshPlaybackSourceAfterCurrentUrlsAreExhausted() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerController.kt").readText()

        assertTrue(source.contains("onPlaybackSourceExhausted"))
        assertTrue(source.contains("sourceRefreshAttempted"))
        assertTrue(source.contains("resetSourceRefreshAttempt"))
    }

    @Test
    fun playerErrorOverlayIsConstrainedAndDoesNotCoverControlsWithRawText() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()
        val errorSource = source.substringAfter("// Error state")
            .substringBefore("// Line switcher bottom sheet")

        assertTrue(errorSource.contains("PlayerErrorOverlay("))
        assertTrue(source.contains("private fun PlayerErrorOverlay("))
        assertTrue(source.contains("widthIn(max = 320.dp)"))
        assertTrue(source.contains("maxLines = 3"))
        assertTrue(source.contains("TextOverflow.Ellipsis"))
    }
}
