package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun forceHttpsExcludesOriginalHttpUrl() {
        assertEquals(
            listOf(
                "https://example.com/live.flv",
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

    @Test
    fun volumeGestureControlsSystemMediaStreamWithoutChangingPlayerGain() {
        val source = readMainSource("com/mylive/app/ui/screen/room/player/PlayerController.kt")
        val systemVolumeSource = source.substringAfter("fun setSystemVolumeDirect(")
            .substringBefore("fun toggleMute()")

        assertTrue(source.contains("fun setSystemVolumeDirect("))
        assertTrue(systemVolumeSource.contains("audioManager.setStreamVolume("))
        assertTrue(systemVolumeSource.contains("AudioManager.STREAM_MUSIC"))
        assertFalse(systemVolumeSource.contains("player?.volume"))
    }

    @Test
    fun systemVolumeGestureReadsTheSystemMediaStream() {
        val source = readMainSource("com/mylive/app/ui/screen/room/player/PlayerController.kt")

        assertTrue(source.contains("fun currentSystemMediaVolume(): Float"))
        assertTrue(source.contains("audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)"))
        assertTrue(source.contains("streamVolumeIndexFor(volume)"))
    }

    @Test
    fun appDeclaresAudioSettingsPermissionForMediaVolumeGestures() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.permission.MODIFY_AUDIO_SETTINGS"))
    }

    @Test
    fun idlePlaybackStateClearsLoadingAndPlayingFlags() {
        val source = readMainSource("com/mylive/app/ui/screen/room/player/PlayerController.kt")
        val stateHandler = source.substringAfter("override fun onPlaybackStateChanged")
            .substringBefore("override fun onIsPlayingChanged")

        assertTrue(stateHandler.contains("Player.STATE_IDLE"))
        assertTrue(stateHandler.contains("isLoading = false"))
        assertTrue(stateHandler.contains("isPlaying = false"))
    }

    @Test
    fun stallWatchdogSamplesOnMainThreadAndReusesTheExistingRecoveryCallback() {
        val source = readMainSource("com/mylive/app/ui/screen/room/player/PlayerController.kt")

        assertTrue(source.contains("Handler(Looper.getMainLooper())"))
        assertTrue(source.contains("stallMonitor.sample("))
        assertTrue(source.contains("playbackStallRecoveryAction("))
        assertTrue(source.contains("onPlaybackSourceExhausted?.invoke()"))
        assertFalse(source.contains("Timer(\"playback-stall"))
    }

    @Test
    fun playbackDiagnosticsUseMedia3EventsAndNeverExposeRequestHeaders() {
        val source = readMainSource("com/mylive/app/ui/screen/room/player/PlayerController.kt")
        val diagnostics = source.substringAfter("fun playbackDiagnostics(")

        assertTrue(source.contains("AnalyticsListener"))
        assertTrue(source.contains("onVideoDecoderInitialized"))
        assertTrue(source.contains("onVideoInputFormatChanged"))
        assertTrue(source.contains("onDroppedVideoFrames"))
        assertTrue(diagnostics.contains("buildPlaybackDiagnosticSnapshot("))
        assertFalse(diagnostics.substringBefore("private fun").contains("headers"))
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
