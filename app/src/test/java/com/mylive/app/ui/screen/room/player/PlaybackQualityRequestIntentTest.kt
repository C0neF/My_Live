package com.mylive.app.ui.screen.room.player

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
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
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real ViewModel, Session, Controller and ExoPlayer; only site responses and repositories are faked. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlaybackQualityRequestIntentTest {
    @Test
    fun qualityResolutionAfterANewerManualPauseKeepsPlaybackPaused() = withPlayback {
        requestReplacementQuality()
        session.playerController.pause()
        assertFalse("The player controls must pause the actual player before the response arrives", playWhenReady)

        resolveReplacementQuality()

        assertFalse("A quality request started before the user's pause must not resume playback", playWhenReady)
    }

    @Test
    fun qualityResolutionWithoutANewerPauseStartsTheSelectedQuality() = withPlayback {
        requestReplacementQuality()

        resolveReplacementQuality()

        assertTrue("Selecting a quality must keep the existing playback intent without a later pause", playWhenReady)
    }

    @Test
    fun selectingQualityAfterManualPauseSuppliesNewPlaybackIntent() = withPlayback {
        session.playerController.pause()
        assertFalse(playWhenReady)
        requestReplacementQuality()
        assertFalse("The previous pause remains in effect while the newly selected quality is resolving", playWhenReady)

        resolveReplacementQuality()

        assertTrue("A quality selection made after the pause is a new explicit playback request", playWhenReady)
    }

    @Test
    fun refreshResponseAfterPauseDuringRoomDetailKeepsPlaybackPaused() = withPlayback {
        beginRefreshWithPendingRoomDetail()
        session.playerController.pause()

        releasePendingRoomDetail()
        resolveReplacementQuality(expectedQualityIndex = 0)

        assertFalse("A refresh must retain its pause boundary from before room-detail acquisition", playWhenReady)
    }

    @Test
    fun refreshResponseAfterPauseDuringQualityListKeepsPlaybackPaused() = withPlayback {
        beginRefreshWithPendingQualityList()
        session.playerController.pause()

        releasePendingQualityList()
        resolveReplacementQuality(expectedQualityIndex = 0)

        assertFalse("A refresh must retain its pause boundary from before quality-list acquisition", playWhenReady)
    }

    @Test
    fun anEngineReadyCallbackAfterANewerPauseDoesNotRenewThePendingSelection() = withPlayback {
        requestReplacementQuality()
        session.playerController.pause()

        deliverEngineReadyAgain()
        resolveReplacementQuality()

        assertFalse("Engine readiness must not turn an old quality selection into a new playback request", playWhenReady)
    }

    @Test
    fun replacingTheEngineDuringRoomDetailUsesTheNewSessionsPauseBoundary() = withPlayback {
        session.playerController.pause()
        beginRefreshWithPendingRoomDetail()

        replaceSession()
        releasePendingRoomDetail()
        resolveReplacementQuality(expectedQualityIndex = 0)

        assertTrue("A new Session must not inherit a different player's pause-generation token", playWhenReady)
    }

    @Test
    fun pausingANewlyBoundEngineDuringRoomDetailStillWinsOverThePendingRefresh() = withPlayback {
        session.playerController.pause()
        beginRefreshWithPendingRoomDetail()
        replaceSession()
        session.playerController.pause()

        releasePendingRoomDetail()
        resolveReplacementQuality(expectedQualityIndex = 0)

        assertFalse("An early bind must capture the new Session before a later pause and detail response", playWhenReady)
    }

    private fun withPlayback(test: Fixture.() -> Unit) {
        val fixture = Fixture()
        try {
            fixture.openRoom()
            fixture.test()
        } finally {
            fixture.close()
        }
    }

    private class Fixture {
        private val context = ApplicationProvider.getApplicationContext<Context>()
        private val replacementSource = CompletableDeferred<LivePlayUrl>()
        private val replacementRequestStarted = CompletableDeferred<Unit>()
        private var sourceRequests = 0
        private var roomDetailGate: CompletableDeferred<Unit>? = null
        private var qualityListGate: CompletableDeferred<Unit>? = null
        private var roomDetailSuspended = false
        private var qualityListSuspended = false
        private val server = MockWebServer().apply {
            val media = silentWav()
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
                    .setHeader("Content-Type", "audio/wav")
                    .setBody(Buffer().write(media))
            }
            start(InetAddress.getByName("127.0.0.1"), 0)
        }
        private val originalUrl = server.url("/original.wav").toString()
        private val replacementUrl = server.url("/replacement.wav").toString()
        private val detail = LiveRoomDetail("1", "room", "", "anchor", status = true)
        private val qualities = listOf(
            LivePlayQuality("原画", PlayQualityData.Douyin(listOf(originalUrl))),
            LivePlayQuality("高清", PlayQualityData.Douyin(listOf(replacementUrl)))
        )
        private val site = mockk<LiveSite> {
            every { id } returns "fake"
            coEvery { getRoomDetail(any()) } coAnswers {
                roomDetailGate?.let { gate ->
                    roomDetailSuspended = true
                    gate.await()
                }
                detail
            }
            coEvery { getSuperChatMessage(any(), any()) } returns emptyList()
            coEvery { getPlayQualites(any()) } coAnswers {
                qualityListGate?.let { gate ->
                    qualityListSuspended = true
                    gate.await()
                }
                qualities
            }
            coEvery { getPlayUrls(any(), any()) } coAnswers {
                sourceRequests += 1
                if (sourceRequests == 1) {
                    LivePlayUrl(listOf(originalUrl))
                } else {
                    replacementRequestStarted.complete(Unit)
                    replacementSource.await()
                }
            }
            every { createDanmakuSession(any(), any()) } answers {
                LiveDanmakuSession(mockk(relaxed = true), DanmakuArgs.Douyu("1"))
            }
        }
        private val follow = mockk<FollowRepository>(relaxed = true) {
            coEvery { isFollowing(any(), any()) } returns false
            every { observeFollowing(any(), any()) } returns flowOf(false)
        }
        private val settings = mockk<SettingsRepository> {
            every { danmuShieldEnable } returns flowOf(true)
            every { danmuKeywordShieldEnable } returns flowOf(true)
            every { danmuUserShieldEnable } returns flowOf(true)
            every { qualityLevel } returns flowOf(1)
            every { qualityLevelCellular } returns flowOf(1)
        }
        private val shields = mockk<ShieldRepository> {
            every { getAllShields() } returns flowOf(emptyList())
        }
        private val vm = LiveRoomViewModel(
            SavedStateHandle(), setOf(site), follow, mockk(relaxed = true), shields, settings, context
        )
        private val store = ViewModelStore().also { it.put("room", vm) }
        var session = LivePlaybackSession.create(context, true, false, vm::recoverPlaybackAfterSourceFailure)
            private set
        val playWhenReady: Boolean
            get() = checkNotNull(session.playerController.player).playWhenReady

        fun openRoom() {
            // The media source is a loopback WAV and the real player's output is muted. No
            // production Player instance or listener is replaced with a recording test double.
            checkNotNull(session.playerController.player).volume = 0f
            session.attach(vm)
            vm.openRoute("1", "fake")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("The initial room must finish resolving its original source", 1, sourceRequests)
            assertEquals(originalUrl, session.state.value.currentUrl)
            assertEquals(0, vm.uiState.value.currentQualityIndex)
            assertTrue("The original source must request playback before the test action", playWhenReady)
        }

        fun requestReplacementQuality() {
            vm.switchQuality(1)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("The actual getPlayUrls call must be suspended before applying a later pause",
                replacementRequestStarted.isCompleted)
            assertEquals(2, sourceRequests)
            assertEquals("The old source must remain current until the selected quality resolves",
                originalUrl, session.state.value.currentUrl)
        }

        fun resolveReplacementQuality(expectedQualityIndex: Int = 1) {
            replacementSource.complete(LivePlayUrl(listOf(replacementUrl)))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("The selected quality response must reach the actual player session",
                replacementUrl, session.state.value.currentUrl)
            assertEquals(expectedQualityIndex, vm.uiState.value.currentQualityIndex)
        }

        fun beginRefreshWithPendingRoomDetail() {
            roomDetailGate = CompletableDeferred()
            vm.refreshPlay()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("The refresh must be suspended in its actual room-detail call", roomDetailSuspended)
            assertEquals("URL resolution must not have begun before the tested pause", 1, sourceRequests)
        }

        fun releasePendingRoomDetail() {
            checkNotNull(roomDetailGate).complete(Unit)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("The refreshed detail must proceed to the controlled source response",
                replacementRequestStarted.isCompleted)
        }

        fun beginRefreshWithPendingQualityList() {
            qualityListGate = CompletableDeferred()
            vm.refreshPlay()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("The refresh must be suspended in its actual quality-list call", qualityListSuspended)
            assertEquals("URL resolution must not have begun before the tested pause", 1, sourceRequests)
        }

        fun releasePendingQualityList() {
            checkNotNull(qualityListGate).complete(Unit)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("The refreshed qualities must proceed to the controlled source response",
                replacementRequestStarted.isCompleted)
        }

        fun deliverEngineReadyAgain() {
            vm.onPlaybackEngineReady()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("The readiness callback must exercise the existing request-restart path", 3, sourceRequests)
        }

        fun replaceSession() {
            session.release()
            session = LivePlaybackSession.create(context, true, false, vm::recoverPlaybackAfterSourceFailure)
            checkNotNull(session.playerController.player).volume = 0f
            session.attach(vm)
        }

        fun close() {
            session.release()
            store.clear()
            shadowOf(Looper.getMainLooper()).idle()
            server.shutdown()
        }

        private companion object {
            fun silentWav(): ByteArray {
                val bytes = 8_000 * 2 * 20
                return ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put("RIFF".toByteArray(Charsets.US_ASCII))
                    putInt(36 + bytes)
                    put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
                    putInt(16)
                    putShort(1)
                    putShort(1)
                    putInt(8_000)
                    putInt(16_000)
                    putShort(2)
                    putShort(16)
                    put("data".toByteArray(Charsets.US_ASCII))
                    putInt(bytes)
                }.array()
            }
        }
    }
}
