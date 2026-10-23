package com.mylive.app.ui.screen.room

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.core.model.DanmakuArgs
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageType
import com.mylive.app.core.model.LiveRoomDetail
import com.mylive.app.core.site.LiveDanmaku
import com.mylive.app.core.site.LiveDanmakuSession
import com.mylive.app.core.site.LiveSite
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.data.repository.ShieldRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class LiveRoomQueuedMessageGenerationTest {
    private val store = ViewModelStore()
    private val sessions = mutableListOf<RecordingDanmaku>()
    private lateinit var vm: LiveRoomViewModel

    @Before
    fun setup() {
        val site = mockk<LiveSite> {
            every { id } returns "fake"
            coEvery { getRoomDetail(any()) } returns LiveRoomDetail("1", "room", "", "anchor", status = true)
            coEvery { getSuperChatMessage(any(), any()) } returns emptyList()
            coEvery { getPlayQualites(any()) } returns emptyList()
            every { createDanmakuSession(any(), any()) } answers {
                LiveDanmakuSession(RecordingDanmaku().also(sessions::add), DanmakuArgs.Douyu("1"))
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
        vm = LiveRoomViewModel(
            SavedStateHandle(), setOf(site), follow, mockk(relaxed = true),
            shields, settings, ApplicationProvider.getApplicationContext()
        )
        store.put("room", vm)
    }

    @After
    fun close() {
        store.clear()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun messageQueuedBeforeRefreshCannotUpdateTheNewRoomGeneration() {
        vm.openRoute("1", "fake")
        shadowOf(Looper.getMainLooper()).idle()
        val oldCallback = checkNotNull(sessions.single().onMessage)
        emitOnBackgroundThread {
            oldCallback(LiveMessage(LiveMessageType.ONLINE, "", "", onlineCount = 999))
        }

        // The producer has completed, but the main consumer has not drained its queue.
        vm.refreshPlay()
        assertEquals(0, vm.onlineCount.value)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Refreshing must discard messages already queued by the old connection", 0, vm.onlineCount.value)
        val currentCallback = checkNotNull(sessions.last().onMessage)
        currentCallback(LiveMessage(LiveMessageType.ONLINE, "", "", onlineCount = 42))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("The current connection must remain usable", 42, vm.onlineCount.value)
    }

    @Test
    fun chatQueuedBeforeRefreshCannotReappearInTheClearedPanel() {
        vm.openRoute("1", "fake")
        shadowOf(Looper.getMainLooper()).idle()
        val oldCallback = checkNotNull(sessions.single().onMessage)
        emitOnBackgroundThread {
            oldCallback(LiveMessage(LiveMessageType.CHAT, "old", "stale chat"))
        }

        vm.refreshPlay()
        assertTrue(vm.danmakuMessages.value.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))

        assertTrue("Queued old chat must not reappear after the panel was cleared", vm.danmakuMessages.value.isEmpty())
        val currentCallback = checkNotNull(sessions.last().onMessage)
        currentCallback(LiveMessage(LiveMessageType.CHAT, "current", "fresh chat"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
        assertEquals(listOf("fresh chat"), vm.danmakuMessages.value.map { it.message.message })
    }

    @Test
    fun externalDanmakuCollectorDoesNotReceiveOldMessagesQueuedBeforeRefresh() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val received = mutableListOf<String>()
        try {
            scope.launch {
                vm.newDanmakuMessages.collect { received += it.message }
            }
            vm.openRoute("1", "fake")
            shadowOf(Looper.getMainLooper()).idle()
            val oldCallback = checkNotNull(sessions.single().onMessage)
            emitOnBackgroundThread {
                oldCallback(LiveMessage(LiveMessageType.CHAT, "old", "stale overlay"))
            }

            vm.refreshPlay()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))

            assertTrue("External overlay consumers must discard the old queued generation", received.isEmpty())
            val currentCallback = checkNotNull(sessions.last().onMessage)
            currentCallback(LiveMessage(LiveMessageType.CHAT, "current", "fresh overlay"))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("fresh overlay"), received)
        } finally {
            scope.cancel()
        }
    }

    private fun emitOnBackgroundThread(emit: () -> Unit) {
        val failure = AtomicReference<Throwable?>()
        val thread = Thread({
            try {
                emit()
            } catch (error: Throwable) {
                failure.set(error)
            }
        }, "pre-refresh-test-danmaku")
        thread.start()
        thread.join(5_000)
        assertFalse("Synthetic producer did not finish", thread.isAlive)
        failure.get()?.let { throw AssertionError("Synthetic producer failed", it) }
    }

    private class RecordingDanmaku : LiveDanmaku {
        override var onMessage: ((LiveMessage) -> Unit)? = null
        override var onClose: ((String) -> Unit)? = null
        override var onReady: (() -> Unit)? = null
        override var heartbeatTime: Int = 0
        override fun heartbeat() {}
        override suspend fun start(args: DanmakuArgs) {}
        override suspend fun stop() {}
    }
}
