package com.mylive.app.ui.screen.room.player

import android.os.Looper
import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.service.PlaybackForegroundService
import com.mylive.app.core.model.DanmakuArgs
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.core.model.LivePlayUrl
import com.mylive.app.core.model.LiveRoomDetail
import com.mylive.app.core.model.PlayQualityData
import com.mylive.app.core.site.LiveDanmakuSession
import com.mylive.app.core.site.LiveSite
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.data.repository.ShieldRepository
import com.mylive.app.ui.screen.room.LiveRoomViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerErrorPauseIntentTest {
    @Test
    fun networkErrorAfterManualPauseDoesNotResumePlayback() {
        val controller = PlayerController(ApplicationProvider.getApplicationContext())
        val fixture = Fixture(controller)
        try {
            controller.play(listOf(FIRST_URL, SECOND_URL))
            controller.pause()
            assertFalse(fixture.playWhenReady)

            fixture.networkError()

            assertFalse("An old connection error must preserve the newer manual pause", fixture.playWhenReady)
            controller.resume()
            assertTrue("An explicit resume must still be accepted", fixture.playWhenReady)
        } finally {
            controller.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun networkErrorAfterBackgroundPlaybackIsDisallowedStaysPaused() {
        val session = LivePlaybackSession.create(
            ApplicationProvider.getApplicationContext(),
            hardwareDecodeEnabled = true,
            forceHttps = false,
            onPlaybackSourceExhausted = null
        )
        val fixture = Fixture(session.playerController)
        try {
            session.play(listOf(FIRST_URL, SECOND_URL))
            session.onHostPause(
                allowBackgroundPlayback = false,
                playerAutoPause = true,
                roomTitle = "room",
                anchorName = "anchor",
                platform = "fake"
            )
            assertFalse(fixture.playWhenReady)

            fixture.networkError()

            assertFalse("Error recovery must not bypass the host's background ban", fixture.playWhenReady)
            session.onHostResume()
            assertTrue("Host resume must restore the lifecycle-paused session", fixture.playWhenReady)
        } finally {
            session.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun networkErrorWhilePlaybackIsRequestedStillRecoversToTheNextLine() {
        val controller = PlayerController(ApplicationProvider.getApplicationContext())
        val fixture = Fixture(controller)
        try {
            controller.play(listOf(FIRST_URL, SECOND_URL))

            fixture.networkError()

            assertTrue(fixture.playWhenReady)
            assertEquals(1, controller.getCurrentUrlIndex())
            assertEquals(SECOND_URL, controller.state.value.currentUrl)
        } finally {
            controller.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun sourceRecoveryDeliveredAfterANewerManualPauseDoesNotResumePlayback() {
        var sourceRefreshes = 0
        val session = LivePlaybackSession.create(
            ApplicationProvider.getApplicationContext(),
            hardwareDecodeEnabled = true,
            forceHttps = false,
            onPlaybackSourceExhausted = { sourceRefreshes++; true }
        )
        val fixture = Fixture(session.playerController)
        try {
            session.play(listOf(FIRST_URL))
            fixture.networkError()
            assertEquals("The old playing connection requested an asynchronous refresh", 1, sourceRefreshes)

            session.pause()
            // Source acquisition completes after the user's newer pause, as in ViewModel's
            // suspend getPlayUrls call. SourceRecovery must not introduce new user intent.
            session.play(listOf(SECOND_URL), origin = PlaybackRequestOrigin.SourceRecovery)

            assertFalse("A late recovery result must preserve the newer manual pause", fixture.playWhenReady)
            session.resume()
            assertTrue(fixture.playWhenReady)
        } finally {
            session.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun aRecoveryDeferredByTheHostResumesOnlyTheLifecyclePausedPlayback() = withSession { session, fixture ->
        session.play(listOf(FIRST_URL))
        session.onHostPause(false, true, "room", "anchor", "fake")
        session.play(listOf(SECOND_URL), origin = PlaybackRequestOrigin.SourceRecovery)

        assertFalse(fixture.playWhenReady)
        assertEquals(FIRST_URL, session.state.value.currentUrl)

        session.onHostResume()

        assertTrue("A lifecycle pause should be restored after the host resumes", fixture.playWhenReady)
        assertEquals(SECOND_URL, session.state.value.currentUrl)
    }

    @Test
    fun aRecoveryDeferredWhileManuallyPausedRemainsPausedOnHostResume() = withSession { session, fixture ->
        session.play(listOf(FIRST_URL))
        session.playerController.pause()
        session.onHostPause(false, true, "room", "anchor", "fake")
        session.play(listOf(SECOND_URL), origin = PlaybackRequestOrigin.SourceRecovery)

        session.onHostResume()

        assertFalse("The host must not turn an existing manual pause into a lifecycle pause", fixture.playWhenReady)
        assertEquals(SECOND_URL, session.state.value.currentUrl)
    }

    @Test
    fun aManualPauseAfterDeferralCancelsItsAutomaticForegroundResume() = withSession { session, fixture ->
        session.play(listOf(FIRST_URL))
        session.onHostPause(false, true, "room", "anchor", "fake")
        session.play(listOf(SECOND_URL), origin = PlaybackRequestOrigin.SourceRecovery)
        // The player controls call Controller directly; the session must observe this newer intent.
        session.playerController.pause()

        session.onHostResume()

        assertFalse("A newer manual pause must win over a queued lifecycle resume", fixture.playWhenReady)
        assertEquals(SECOND_URL, session.state.value.currentUrl)
    }

    @Test
    fun aManualPauseAfterHostPauseCancelsResumeWithoutANewSource() = withSession { session, fixture ->
        session.play(listOf(FIRST_URL))
        session.onHostPause(false, true, "room", "anchor", "fake")
        session.playerController.pause()

        session.onHostResume()

        assertFalse("Returning to the foreground must preserve the newer manual pause", fixture.playWhenReady)
    }

    @Test
    fun lateForegroundPromotionDoesNotResumeANewerManualPause() = withSession { session, fixture ->
        session.play(listOf(FIRST_URL))
        session.onHostPause(true, false, "room", "anchor", "fake")
        session.playerController.pause()

        promoteForegroundService()

        assertFalse("The FGS ready callback must not restore its captured, stale play intent", fixture.playWhenReady)
    }

    @Test
    fun lateForegroundPromotionDoesNotStartAPendingSourceAfterManualPause() = withSession { session, fixture ->
        session.onHostPause(true, false, "room", "anchor", "fake")
        session.play(listOf(SECOND_URL))
        session.playerController.pause()

        promoteForegroundService()

        assertFalse("A pending source must not bypass a pause delivered before FGS readiness", fixture.playWhenReady)
        session.onHostResume()
        assertFalse("Host resume must also preserve that pause", fixture.playWhenReady)
    }

    @Test
    fun permanentAudioFocusLossInvalidatesTheOlderPendingSourceStart() {
        assertSystemPauseInvalidatesPendingSource(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
    }

    @Test
    fun unpluggingTheAudioRouteInvalidatesTheOlderPendingSourceStart() {
        assertSystemPauseInvalidatesPendingSource(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)
    }

    private fun assertSystemPauseInvalidatesPendingSource(reason: Int) = withSession { session, fixture ->
        session.play(listOf(FIRST_URL))
        session.onHostPause(true, false, "room", "anchor", "fake")
        session.play(listOf(SECOND_URL), origin = PlaybackRequestOrigin.SourceRecovery)
        fixture.pauseForSystemReason(reason)
        assertFalse(fixture.playWhenReady)

        promoteForegroundService()

        assertFalse("A pending source must preserve the newer system pause reason=$reason", fixture.playWhenReady)
        session.onHostResume()
        assertFalse("Returning to the host must also preserve that system pause", fixture.playWhenReady)
    }

    @Test
    fun aDecoderFallbackPreservesTheManualPauseWhenItRebuildsThePlayer() {
        val controller = PlayerController(ApplicationProvider.getApplicationContext())
        val fixture = Fixture(controller)
        try {
            controller.play(listOf(FIRST_URL))
            controller.pause()
            val originalPlayer = controller.player

            fixture.decoderError()

            assertTrue("The failed hardware decoder must be rebuilt", controller.player !== originalPlayer)
            assertFalse("A decoder replacement must preserve the user's pause", controller.player!!.playWhenReady)
        } finally {
            controller.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun lateErrorCannotRestartAStoppedConnection() = withSession { session, fixture ->
        session.play(listOf(FIRST_URL, SECOND_URL))
        session.stop()
        val stoppedUrl = session.state.value.currentUrl

        fixture.networkError()

        assertEquals("A late error must not switch a stopped session to another source", stoppedUrl, session.state.value.currentUrl)
        assertEquals(0, session.playerController.getCurrentUrlIndex())
        assertFalse(session.state.value.isLoading)
    }

    @Test
    fun anExplicitUserSelectionAfterPauseStillStartsPlayback() = withSession { session, fixture ->
        session.play(listOf(FIRST_URL))
        session.playerController.pause()

        session.play(listOf(SECOND_URL), origin = PlaybackRequestOrigin.UserSelection)

        assertTrue("An explicit source selection is a new playback request", fixture.playWhenReady)
        assertEquals(SECOND_URL, session.state.value.currentUrl)
    }

    @Test
    fun roomSourceRecoveryKeepsTheForegroundPlaybackIntentAcrossItsStop() {
        assertRoomRecoveryKeepsPlaybackIntent(hostPaused = false, manuallyPausedDuringRefresh = false)
    }

    @Test
    fun roomSourceRecoveryKeepsTheLifecycleResumeIntentAcrossItsStop() {
        assertRoomRecoveryKeepsPlaybackIntent(hostPaused = true, manuallyPausedDuringRefresh = false)
    }

    @Test
    fun roomSourceRecoveryStillHonorsANewerManualPauseAfterItsStop() {
        assertRoomRecoveryKeepsPlaybackIntent(hostPaused = true, manuallyPausedDuringRefresh = true)
    }

    private fun assertRoomRecoveryKeepsPlaybackIntent(hostPaused: Boolean, manuallyPausedDuringRefresh: Boolean) {
        val freshSource = CompletableDeferred<LivePlayUrl>()
        var sourceRequests = 0
        val site = mockk<LiveSite> {
            every { id } returns "fake"
            coEvery { getRoomDetail(any()) } returns LiveRoomDetail("1", "room", "", "anchor", status = true)
            coEvery { getSuperChatMessage(any(), any()) } returns emptyList()
            coEvery { getPlayQualites(any()) } returns listOf(
                LivePlayQuality("original", PlayQualityData.Douyin(listOf(FIRST_URL)))
            )
            coEvery { getPlayUrls(any(), any()) } coAnswers {
                sourceRequests += 1
                if (sourceRequests == 1) LivePlayUrl(listOf(FIRST_URL)) else freshSource.await()
            }
            every { createDanmakuSession(any(), any()) } answers {
                LiveDanmakuSession(mockk(relaxed = true), DanmakuArgs.Douyu("1"))
            }
        }
        val follow = mockk<FollowRepository>(relaxed = true) {
            coEvery { isFollowing(any(), any()) } returns false
            every { observeFollowing(any(), any()) } returns flowOf(false)
        }
        val settings = mockk<SettingsRepository> {
            every { danmuShieldEnable } returns flowOf(true)
            every { danmuKeywordShieldEnable } returns flowOf(true)
            every { danmuUserShieldEnable } returns flowOf(true)
            every { qualityLevel } returns flowOf(1)
            every { qualityLevelCellular } returns flowOf(1)
        }
        val shields = mockk<ShieldRepository> {
            every { getAllShields() } returns flowOf(emptyList())
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = LiveRoomViewModel(SavedStateHandle(), setOf(site), follow, mockk(relaxed = true),
            shields, settings, context)
        val store = ViewModelStore().also { it.put("room", vm) }
        val session = LivePlaybackSession.create(context, true, false, vm::recoverPlaybackAfterSourceFailure)
        val fixture = Fixture(session.playerController)
        try {
            session.attach(vm)
            vm.openRoute("1", "fake")
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("The initial room source must be playing", fixture.playWhenReady)
            if (hostPaused) session.onHostPause(false, true, "room", "anchor", "fake")

            // Exercise the real Controller -> Session -> ViewModel recovery chain, including
            // openRoute's stop before the asynchronous replacement source is available.
            fixture.networkError()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Recovery must request a fresh source through the bound room", 2, sourceRequests)
            if (manuallyPausedDuringRefresh) session.playerController.pause()
            freshSource.complete(LivePlayUrl(listOf(SECOND_URL)))
            shadowOf(Looper.getMainLooper()).idle()

            if (hostPaused) {
                assertFalse("Source delivery must not bypass the background playback ban", fixture.playWhenReady)
                session.onHostResume()
            }

            assertEquals("The refresh stop must preserve intent while a newer manual pause wins",
                !manuallyPausedDuringRefresh, fixture.playWhenReady)
            assertEquals(SECOND_URL, session.state.value.currentUrl)
        } finally {
            session.release()
            store.clear()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun withSession(block: (LivePlaybackSession, Fixture) -> Unit) {
        val session = LivePlaybackSession.create(
            ApplicationProvider.getApplicationContext(), true, false, null
        )
        val fixture = Fixture(session.playerController)
        try {
            block(session, fixture)
        } finally {
            session.release()
            PlaybackForegroundService.activePlayer = null
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun promoteForegroundService() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = Robolectric.buildService(PlaybackForegroundService::class.java).create()
        try {
            service.get().onStartCommand(Intent(context, PlaybackForegroundService::class.java), 0, 1)
        } finally {
            service.destroy()
        }
    }

    private class Fixture(controller: PlayerController) {
        var playWhenReady = false
        private var playbackState = Player.STATE_READY
        private val listener = PlayerController::class.java.getDeclaredField("playerListener")
            .apply { isAccessible = true }.get(controller) as Player.Listener

        init {
            controller.player!!.removeListener(listener)
            controller.player!!.release()
            val player = mockk<ExoPlayer>(relaxed = true)
            every { player.playWhenReady } answers { playWhenReady }
            every { player.playWhenReady = any() } answers { playWhenReady = firstArg() }
            every { player.playbackState } answers { playbackState }
            every { player.playbackSuppressionReason } returns Player.PLAYBACK_SUPPRESSION_REASON_NONE
            every { player.prepare() } answers { playbackState = Player.STATE_BUFFERING }
            every { player.stop() } answers {
                playbackState = Player.STATE_IDLE
            }
            PlayerController::class.java.getDeclaredField("player")
                .apply { isAccessible = true }.set(controller, player)
        }

        fun networkError() {
            listener.onPlayerError(
                PlaybackException(
                    "synthetic offline",
                    IOException("synthetic offline"),
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                )
            )
        }

        fun decoderError() {
            listener.onPlayerError(
                PlaybackException(
                    "MediaCodecVideoRenderer decoder failure",
                    IllegalStateException("synthetic video decoder failure"),
                    PlaybackException.ERROR_CODE_DECODING_FAILED
                )
            )
        }

        fun pauseForSystemReason(reason: Int) {
            playWhenReady = false
            listener.onPlayWhenReadyChanged(false, reason)
        }
    }

    private companion object {
        const val FIRST_URL = "https://example.invalid/first.flv"
        const val SECOND_URL = "https://example.invalid/second.flv"
    }
}
