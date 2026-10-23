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
import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.data.repository.ShieldRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
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
class LiveRoomViewModelShieldPolicyTest {
    private val store = ViewModelStore()
    private val collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val shieldEnabled = MutableStateFlow(true)
    private val keywordEnabled = MutableStateFlow(true)
    private val userEnabled = MutableStateFlow(true)
    private val danmaku = RecordingDanmaku()
    private val overlayMessages = mutableListOf<String>()
    private lateinit var vm: LiveRoomViewModel

    @Before
    fun setup() {
        val site = mockk<LiveSite> {
            every { id } returns "fake"
            coEvery { getRoomDetail(any()) } returns LiveRoomDetail("1", "room", "", "anchor", status = true)
            coEvery { getSuperChatMessage(any(), any()) } returns emptyList()
            coEvery { getPlayQualites(any()) } returns emptyList()
            every { createDanmakuSession(any(), any()) } returns
                LiveDanmakuSession(danmaku, DanmakuArgs.Douyu("1"))
        }
        val follow = mockk<FollowRepository>(relaxed = true) {
            coEvery { isFollowing(any(), any()) } returns false
            every { observeFollowing(any(), any()) } returns flowOf(false)
        }
        val settings = mockk<SettingsRepository> {
            every { danmuShieldEnable } returns shieldEnabled
            every { danmuKeywordShieldEnable } returns keywordEnabled
            every { danmuUserShieldEnable } returns userEnabled
            every { qualityLevel } returns flowOf(1)
            every { qualityLevelCellular } returns flowOf(1)
        }
        val shields = mockk<ShieldRepository> {
            every { getAllShields() } returns flowOf(listOf(
                ShieldEntity(value = "keyword:blocked-word"),
                ShieldEntity(value = "user:fake:blocked-user")
            ))
        }
        vm = LiveRoomViewModel(
            SavedStateHandle(), setOf(site), follow, mockk(relaxed = true),
            shields, settings, ApplicationProvider.getApplicationContext()
        )
        store.put("room", vm)
        collectorScope.launch {
            vm.newDanmakuMessages.collect { overlayMessages += it.message }
        }
        vm.openRoute("1", "fake")
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun close() {
        collectorScope.cancel()
        store.clear()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun shieldedKeywordsAndUsersReachNeitherTheChatPanelNorThePublicOverlayFlow() {
        sendChat("reader", "blocked-word in chat")
        sendChat("blocked-user", "user rule applies")
        sendChat("reader", "visible chat")

        assertVisibleMessages("visible chat")
    }

    @Test
    fun keywordAndUserSwitchesIndependentlyControlBothMessageConsumers() {
        keywordEnabled.value = false
        shadowOf(Looper.getMainLooper()).idle()
        sendChat("reader", "blocked-word allowed")
        sendChat("blocked-user", "user still hidden")
        assertVisibleMessages("blocked-word allowed")

        userEnabled.value = false
        shadowOf(Looper.getMainLooper()).idle()
        sendChat("blocked-user", "user now visible")
        assertVisibleMessages("blocked-word allowed", "user now visible")
    }

    @Test
    fun disablingTheMasterShieldAllowsBothKindsOfMessagesInBothConsumers() {
        shieldEnabled.value = false
        shadowOf(Looper.getMainLooper()).idle()
        sendChat("reader", "blocked-word visible")
        sendChat("blocked-user", "user visible")

        assertVisibleMessages("blocked-word visible", "user visible")
    }

    private fun sendChat(user: String, message: String) {
        checkNotNull(danmaku.onMessage)(LiveMessage(LiveMessageType.CHAT, user, message))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
    }

    private fun assertVisibleMessages(vararg messages: String) {
        assertEquals(messages.toList(), vm.danmakuMessages.value.map { it.message.message })
        assertEquals(messages.toList(), overlayMessages)
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
}
