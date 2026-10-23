package com.mylive.app.ui.screen.room.player

import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerEndedRecoveryTest {
    @Test
    fun unexpectedEndSwitchesToTheNextLiveSourceWithoutWaitingForAnError() = withPlayer {
        controller.play(listOf(FIRST_URL, SECOND_URL))

        endStream()

        assertEquals(1, controller.getCurrentUrlIndex())
        assertEquals(SECOND_URL, controller.state.value.currentUrl)
        assertTrue(controller.state.value.isLoading)
        assertNull(controller.state.value.error)
        assertEquals(0, sourceRefreshes)
    }

    @Test
    fun unexpectedEndRequestsFreshSourceWhenTheCurrentLineIsExhausted() = withPlayer {
        controller.play(listOf(FIRST_URL))

        endStream()

        assertEquals(1, sourceRefreshes)
        assertTrue(controller.state.value.isLoading)
        assertNull(controller.state.value.error)
    }

    @Test
    fun repeatedEndedSourcesShareTheExistingRecoveryBudget() = withPlayer {
        controller.play(listOf(FIRST_URL))
        repeat(MAX_PLAYBACK_RECOVERY_ATTEMPTS) {
            endStream()
            assertEquals(it + 1, sourceRefreshes)
            controller.play(listOf(FIRST_URL), origin = PlaybackRequestOrigin.SourceRecovery)
        }

        endStream()

        assertEquals(MAX_PLAYBACK_RECOVERY_ATTEMPTS, sourceRefreshes)
        assertTrue(controller.state.value.error.orEmpty().contains("自动恢复已达上限"))
        assertFalse(controller.state.value.isLoading)
    }

    @Test
    fun userPauseDoesNotRestartAnEndedStream() = withPlayer {
        controller.play(listOf(FIRST_URL))
        controller.pause()

        endStream()

        assertEquals(0, sourceRefreshes)
        assertFalse(playWhenReady)
    }

    @Test
    fun queuedEndCannotRestartAfterTheRoomStops() = withPlayer {
        controller.play(listOf(FIRST_URL))
        playbackState = Player.STATE_ENDED
        listener.onPlaybackStateChanged(Player.STATE_ENDED)

        controller.stop()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, sourceRefreshes)
        assertFalse(controller.state.value.isLoading)
    }

    @Test
    fun duplicateEndNotificationsRequestOneRefreshPerSource() = withPlayer {
        controller.play(listOf(FIRST_URL))
        endStream()
        endStream()
        assertEquals(1, sourceRefreshes)
    }

    @Test
    fun queuedEndCannotReplaceANewUserSelection() = withPlayer {
        controller.play(listOf(FIRST_URL))
        playbackState = Player.STATE_ENDED
        listener.onPlaybackStateChanged(Player.STATE_ENDED)
        controller.play(listOf(SECOND_URL))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, sourceRefreshes)
        assertEquals(SECOND_URL, controller.state.value.currentUrl)
    }

    @Test
    fun queuedEndCannotRestartAfterRelease() = withPlayer {
        controller.play(listOf(FIRST_URL))
        playbackState = Player.STATE_ENDED
        listener.onPlaybackStateChanged(Player.STATE_ENDED)
        controller.release()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, sourceRefreshes)
        assertNull(controller.player)
    }

    @Test
    fun audioFocusSuppressionDefersEndRecoveryUntilFocusReturns() = withPlayer {
        controller.play(listOf(FIRST_URL))
        suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
        endStream()
        assertEquals(0, sourceRefreshes)
        suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE
        listener.onPlaybackSuppressionReasonChanged(suppressionReason)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, sourceRefreshes)
    }

    @Test
    fun playButtonReconnectsAStreamThatEndedWhilePaused() = withPlayer {
        controller.play(listOf(FIRST_URL))
        controller.pause()
        endStream()
        controller.togglePlayPause()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(playWhenReady)
        assertEquals(1, sourceRefreshes)
    }

    @Test
    fun unavailableFreshSourceShowsAnErrorInsteadOfSilentPause() = withPlayer {
        refreshAccepted = false
        controller.play(listOf(FIRST_URL))
        endStream()
        assertEquals(1, sourceRefreshes)
        assertEquals("直播流已结束，请刷新重试", controller.state.value.error)
        assertFalse(controller.state.value.isLoading)
    }

    private fun withPlayer(block: Fixture.() -> Unit) {
        val fixture = Fixture()
        try {
            fixture.block()
        } finally {
            fixture.controller.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private class Fixture {
        var sourceRefreshes = 0
        var refreshAccepted = true
        var suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE
        var playbackState = Player.STATE_READY
        var playWhenReady = false
        val controller = PlayerController(
            ApplicationProvider.getApplicationContext(),
            onPlaybackSourceExhausted = { sourceRefreshes++; refreshAccepted }
        )
        val listener = PlayerController::class.java.getDeclaredField("playerListener")
            .apply { isAccessible = true }.get(controller) as Player.Listener

        init {
            controller.player!!.removeListener(listener)
            controller.player!!.release()
            val player = mockk<ExoPlayer>(relaxed = true)
            every { player.playWhenReady } answers { playWhenReady }
            every { player.playWhenReady = any() } answers { playWhenReady = firstArg() }
            every { player.playbackState } answers { playbackState }
            every { player.playbackSuppressionReason } answers { suppressionReason }
            every { player.prepare() } answers { playbackState = Player.STATE_BUFFERING }
            every { player.stop() } answers { playbackState = Player.STATE_IDLE }
            PlayerController::class.java.getDeclaredField("player")
                .apply { isAccessible = true }.set(controller, player)
        }

        fun endStream() {
            playbackState = Player.STATE_ENDED
            listener.onPlaybackStateChanged(Player.STATE_ENDED)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private companion object {
        const val FIRST_URL = "https://example.invalid/first.flv"
        const val SECOND_URL = "https://example.invalid/second.flv"
    }
}
