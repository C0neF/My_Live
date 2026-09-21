package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDiagnosticsTest {

    @Test
    fun snapshotKeepsUsefulMediaFactsButStripsThePlaybackUrlSecret() {
        val snapshot = buildPlaybackDiagnosticSnapshot(
            PlaybackDiagnosticInput(
                qualityName = "原画",
                currentUrl = "https://cdn.example.com/live.flv?token=top-secret&expires=1",
                currentLineIndex = 1,
                lineCount = 3,
                playbackState = "READY",
                sampleMimeType = "video/avc",
                codecs = "avc1.640028",
                width = 1920,
                height = 1080,
                frameRate = 60f,
                bitrate = 8_000_000,
                decoderName = "c2.vendor.avc.decoder",
                droppedFrames = 2L,
                bufferingCount = 1,
                stallRecoveryCount = 0
            )
        )

        assertEquals("cdn.example.com", snapshot.valueFor("线路域名"))
        assertEquals("2 / 3", snapshot.valueFor("线路"))
        assertTrue(snapshot.asText().contains("1920 × 1080"))
        assertFalse(snapshot.asText().contains("top-secret"))
        assertFalse(snapshot.asText().contains("expires="))
    }
    @Test
    fun diagnosticsDistinguishSourceRateOutputRateAndRebufferTime() {
        val snapshot = buildPlaybackDiagnosticSnapshot(PlaybackDiagnosticInput(
            qualityName = "原画", currentUrl = null, currentLineIndex = 0, lineCount = 1,
            playbackState = "READY", sampleMimeType = "video/avc", codecs = null,
            width = 1920, height = 1080, frameRate = 60f, bitrate = 0, decoderName = null,
            droppedFrames = 5, bufferingCount = 2, stallRecoveryCount = 1,
            firstFrameMillis = 450, outputFrameRate = 45f, playMillis = 8_000, rebufferMillis = 2_000,
            bufferedMillis = 1_200, recoveryAttempts = 2, lastRecoveryReason = "播放停滞换线"
        ))
        assertEquals("60.0 fps", snapshot.valueFor("源帧率"))
        assertEquals("45.0 fps", snapshot.valueFor("视频输出帧率（近段）"))
        assertEquals("450 ms", snapshot.valueFor("播放器首帧"))
        assertEquals("2.0 s / 20.0%", snapshot.valueFor("播放阶段再缓冲"))
        assertEquals("2 / 3", snapshot.valueFor("当前恢复预算已用"))
    }

    @Test
    fun danmakuDiagnosticsExposeLoadWithoutMessageContents() {
        val snapshot = PlaybackDiagnosticSnapshot(emptyList()).withDanmaku(
            DanmakuRenderDiagnostics(8, 12, 4, 1.5, 1024 * 1024))
        assertEquals("8 / 12", snapshot.valueFor("弹幕屏内 / 排队"))
        assertEquals("4", snapshot.valueFor("弹幕丢弃"))
        assertEquals("1.50 ms", snapshot.valueFor("弹幕最近绘制耗时"))
        assertEquals("1.00 MB", snapshot.valueFor("弹幕图片缓存"))
    }
}
