package com.mylive.app.ui.screen.room.player

import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.core.model.LivePlayUrl
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@androidx.annotation.OptIn(UnstableApi::class)
class LivePlaybackFrameRateHintTest {
    @Test fun changingCdnKeepsTheSameSourcesFrameRateHint() = withSession { session ->
        session.engine.play(source(120f))
        clearMocks(checkNotNull(session.playerController.player), answers = false)
        session.playerController.changeLine(1)

        assertEquals("https://example.invalid/b.flv", session.state.value.currentUrl)
        assertEquals(120f, frameRateHint(session))
        verify(exactly = 0) { session.playerController.player?.stop() }
    }

    @Test fun aDifferentSourceRateResetsTheRetainedDecoderBeforePreparingTheNewStream() = withSession { session ->
        session.engine.play(source(60f))
        val player = checkNotNull(session.playerController.player)
        clearMocks(player, answers = false)

        session.engine.play(source(120f))

        verify(exactly = 1) { player.stop() }
        verifyOrder {
            player.stop()
            player.setMediaSource(any())
            player.prepare()
        }
        clearMocks(player, answers = false)
        session.engine.play(source(null))
        verify(exactly = 1) { player.stop() }
    }

    @Test fun aDeferredSourceKeepsItsOwnHintUntilForegroundPlaybackStarts() = withSession { session ->
        session.engine.play(source(120f))
        session.onHostPause(false, true, "room", "anchor", "douyu")
        session.engine.play(source(60f).copy(urls = listOf("https://example.invalid/new.flv")),
            PlaybackRequestOrigin.SourceRecovery)

        assertEquals("https://example.invalid/a.flv", session.state.value.currentUrl)
        assertEquals(120f, frameRateHint(session))
        session.onHostResume()
        assertEquals("https://example.invalid/new.flv", session.state.value.currentUrl)
        assertEquals(60f, frameRateHint(session))
    }

    @Test fun changingToASourceWithoutAHintClearsThePreviousSourcesHint() = withSession { session ->
        session.engine.play(source(120f))
        session.engine.play(source(null))
        assertNull(frameRateHint(session))

        session.engine.play(source(60f))
        session.engine.play(listOf("https://example.invalid/other.flv"))
        assertNull(frameRateHint(session))
    }

    private fun source(fps: Float?) = LivePlayUrl(
        listOf("https://example.invalid/a.flv", "https://example.invalid/b.flv"),
        videoFrameRateHint = fps
    )

    // The real session/controller own the request and lifecycle; the network player is isolated.
    private fun frameRateHint(session: LivePlaybackSession) = PlayerController::class.java
        .getDeclaredField("sourceVideoFrameRateHint").apply { isAccessible = true }
        .get(session.playerController) as Float?

    private fun withSession(block: (LivePlaybackSession) -> Unit) {
        val session = LivePlaybackSession.create(ApplicationProvider.getApplicationContext(), true, false, null)
        session.playerController.player?.release()
        PlayerController::class.java.getDeclaredField("player").apply { isAccessible = true }
            .set(session.playerController, mockk<ExoPlayer>(relaxed = true))
        try { block(session) } finally {
            session.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
