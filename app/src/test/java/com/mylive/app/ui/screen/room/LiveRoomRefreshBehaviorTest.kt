package com.mylive.app.ui.screen.room

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.core.model.*
import com.mylive.app.core.site.LiveSite
import com.mylive.app.core.site.LiveDanmaku
import com.mylive.app.core.site.LiveDanmakuSession
import com.mylive.app.data.repository.*
import com.mylive.app.ui.screen.room.player.*
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveRoomRefreshBehaviorTest {
    private val site = mockk<LiveSite>()
    private val store = ViewModelStore()
    private val engine = RecordingEngine()
    private lateinit var vm: LiveRoomViewModel
    private var revision = 1
    private var failQualities = false
    private val sessions = mutableListOf<RecordingDanmaku>()

    @Before fun setup() {
        every { site.id } returns "fake"
        coEvery { site.getRoomDetail(any()) } returns LiveRoomDetail("1", "room", "", "anchor", status = true)
        coEvery { site.getSuperChatMessage(any(), any()) } returns emptyList()
        every { site.createDanmakuSession(any(), any()) } answers {
            val danmaku = RecordingDanmaku().also(sessions::add)
            LiveDanmakuSession(danmaku, DanmakuArgs.Douyu("1"))
        }
        coEvery { site.getPlayQualites(any()) } coAnswers {
            if (failQualities) error("temporary quality failure")
            listOf("原画", "高清").map { name ->
                LivePlayQuality(name, PlayQualityData.Douyin(listOf("https://example.test/$revision/$name")))
            }
        }
        coEvery { site.getPlayUrls(any(), any()) } coAnswers {
            LivePlayUrl((secondArg<LivePlayQuality>().data as PlayQualityData.Douyin).urls)
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
        val shields = mockk<ShieldRepository> { every { getAllShields() } returns flowOf(emptyList()) }
        vm = LiveRoomViewModel(SavedStateHandle(), setOf(site), follow, mockk(relaxed = true),
            shields, settings, ApplicationProvider.getApplicationContext())
        store.put("room", vm)
        vm.bindPlaybackEngine(engine)
    }

    @After fun close() { store.clear(); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun refreshRetriesAnInitiallyEmptyQualityState() {
        failQualities = true
        vm.openRoute("1", "fake")
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(vm.uiState.value.playQualities.isEmpty())
        assertNotNull(engine.state.value.error)
        failQualities = false
        vm.refreshPlay()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("https://example.test/1/原画"), engine.urls)
        coVerify(exactly = 2) { site.getRoomDetail("1") }
    }

    @Test fun recoveryResolvesNewUrlsAndPreservesSelectedQuality() {
        vm.openRoute("1", "fake")
        shadowOf(Looper.getMainLooper()).idle()
        vm.switchQuality(1)
        shadowOf(Looper.getMainLooper()).idle()
        revision = 2
        assertTrue(vm.recoverPlaybackAfterSourceFailure())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("https://example.test/2/高清"), engine.urls)
        assertEquals(1, vm.uiState.value.currentQualityIndex)
        assertEquals(PlaybackRequestOrigin.SourceRecovery, engine.origin)
    }

    @Test fun resolvedSourceFrameRateAndHeadersReachTheEngineOnInitialPlayAndRecovery() {
        var resolved = LivePlayUrl(listOf("https://example.test/2k120.flv"),
            headers = mapOf("Referer" to "https://example.test/"), videoFrameRateHint = 120f)
        coEvery { site.getPlayUrls(any(), any()) } coAnswers { resolved }
        vm.openRoute("1", "fake")
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(resolved, engine.source)

        resolved = resolved.copy(urls = listOf("https://example.test/2k60.flv"), videoFrameRateHint = 60f)
        assertTrue(vm.recoverPlaybackAfterSourceFailure())
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(resolved, engine.source)
        assertEquals(PlaybackRequestOrigin.SourceRecovery, engine.origin)
    }

    @Test fun initialFailureIsDeliveredWhenPlayerAttachesLater() {
        vm.unbindPlaybackEngine(engine)
        failQualities = true
        vm.openRoute("1", "fake")
        shadowOf(Looper.getMainLooper()).idle()
        vm.bindPlaybackEngine(engine)
        vm.onPlaybackEngineReady()
        assertEquals("temporary quality failure", engine.state.value.error)
    }

    @Test fun refreshOfSameRoomRejectsPreviousDanmakuCallbacks() {
        vm.openRoute("1", "fake")
        shadowOf(Looper.getMainLooper()).idle()
        val oldCallback = checkNotNull(sessions.single().onMessage)
        vm.refreshPlay()
        shadowOf(Looper.getMainLooper()).idle()
        oldCallback(LiveMessage(LiveMessageType.ONLINE, "", "", onlineCount = 999))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, vm.onlineCount.value)
        sessions.last().onMessage!!(LiveMessage(LiveMessageType.ONLINE, "", "", onlineCount = 42))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(42, vm.onlineCount.value)
        store.clear()
        sessions.last().onMessage!!(LiveMessage(LiveMessageType.ONLINE, "", "", onlineCount = 777))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(42, vm.onlineCount.value)
    }

    private class RecordingDanmaku : LiveDanmaku {
        override var onMessage: ((LiveMessage) -> Unit)? = null
        override var onClose: ((String) -> Unit)? = null
        override var onReady: (() -> Unit)? = null
        override var heartbeatTime = 0
        override fun heartbeat() {}
        override suspend fun start(args: DanmakuArgs) {}
        override suspend fun stop() {}
    }

    private class RecordingEngine : LivePlaybackEngine {
        override val state = MutableStateFlow(PlayerState())
        var urls = emptyList<String>()
        var origin = PlaybackRequestOrigin.UserSelection
        var source: LivePlayUrl? = null
        override fun play(source: LivePlayUrl, origin: PlaybackRequestOrigin) {
            this.source = source
            play(source.urls, source.headers, origin = origin, startIndex = 0)
        }
        override fun play(urls: List<String>, headers: Map<String, String>?, startIndex: Int, origin: PlaybackRequestOrigin) {
            this.urls = urls
            this.origin = origin
            state.value = PlayerState(isLoading = false, error = null)
        }
        override fun stop() {}
        override fun showError(message: String) { state.value = PlayerState(isLoading = false, error = message) }
    }
}
