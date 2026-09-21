package com.mylive.app.ui.screen.room.player

import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackRecoveryIntegrationTest {
    private fun replacePlayer(controller: PlayerController) {
        controller.player?.release()
        PlayerController::class.java.getDeclaredField("player").apply { isAccessible = true }
            .set(controller, mockk<ExoPlayer>(relaxed = true))
    }

    @Test fun freshSourcesThatFailBeforeReadyCanRetryUntilTheSharedBudgetIsExhausted() {
        val urls = listOf("https://example.invalid/live.flv")
        var refreshes = 0
        lateinit var session: LivePlaybackSession
        session = LivePlaybackSession.create(
            context = ApplicationProvider.getApplicationContext(),
            hardwareDecodeEnabled = true,
            forceHttps = false,
            onPlaybackSourceExhausted = {
                refreshes++
                session.stop()
                session.play(urls, null, 0, PlaybackRequestOrigin.SourceRecovery)
                true
            }
        )
        val controller = session.playerController
        replacePlayer(controller)
        try {
            val listener = PlayerController::class.java.getDeclaredField("playerListener")
                .apply { isAccessible = true }.get(controller) as Player.Listener
            session.play(urls, null, 0, PlaybackRequestOrigin.UserSelection)
            val failure = PlaybackException("source unavailable", IOException(), PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
            repeat(MAX_PLAYBACK_RECOVERY_ATTEMPTS) {
                // Deliberately never dispatch READY for any of the replacement sources.
                listener.onPlayerError(failure)
                assertEquals(it + 1, refreshes)
                assertNull(controller.state.value.error)
            }
            listener.onPlayerError(failure)
            assertEquals(MAX_PLAYBACK_RECOVERY_ATTEMPTS, refreshes)
            assertTrue(controller.state.value.error.orEmpty().contains("自动恢复已达上限"))
        } finally { session.release(); shadowOf(Looper.getMainLooper()).idle() }
    }

    @Test fun stopAndSourceRecoveryPlayKeepTheExhaustedBudget() {
        val controller = PlayerController(ApplicationProvider.getApplicationContext())
        replacePlayer(controller)
        try {
            val budget = PlayerController::class.java.getDeclaredField("recoveryBudget")
                .apply { isAccessible = true }.get(controller) as PlaybackRecoveryBudget
            repeat(MAX_PLAYBACK_RECOVERY_ATTEMPTS) { assertTrue(budget.tryAcquire()) }
            controller.stop()
            controller.play(listOf("https://example.invalid/live.flv"), origin = PlaybackRequestOrigin.SourceRecovery)
            assertFalse(budget.tryAcquire())
            controller.play(listOf("https://example.invalid/live.flv"), origin = PlaybackRequestOrigin.UserSelection)
            assertTrue(budget.tryAcquire())
        } finally { controller.release(); shadowOf(Looper.getMainLooper()).idle() }
    }

    @Test fun repeatedFreshSourcesAndReadyCallbacksStillReachTheRecoveryLimit() {
        val urls = listOf("https://example.invalid/live.flv")
        var refreshes = 0
        lateinit var controller: PlayerController
        controller = PlayerController(ApplicationProvider.getApplicationContext(), onPlaybackSourceExhausted = {
            refreshes++
            controller.stop()
            controller.play(urls, origin = PlaybackRequestOrigin.SourceRecovery)
            true
        })
        replacePlayer(controller)
        try {
            val listener = PlayerController::class.java.getDeclaredField("playerListener")
                .apply { isAccessible = true }.get(controller) as Player.Listener
            controller.play(urls)
            val failure = PlaybackException("network unavailable", IOException(), PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
            repeat(MAX_PLAYBACK_RECOVERY_ATTEMPTS) {
                listener.onPlaybackStateChanged(Player.STATE_READY)
                listener.onPlayerError(failure)
                assertNull(controller.state.value.error)
            }
            listener.onPlaybackStateChanged(Player.STATE_READY)
            listener.onPlayerError(failure)
            assertEquals(MAX_PLAYBACK_RECOVERY_ATTEMPTS, refreshes)
            assertTrue(controller.state.value.error.orEmpty().contains("自动恢复已达上限"))
        } finally { controller.release(); shadowOf(Looper.getMainLooper()).idle() }
    }
}
