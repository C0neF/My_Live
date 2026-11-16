package com.mylive.desktop

import com.mylive.app.core.model.*
import com.mylive.app.core.site.LiveDanmakuSession
import com.mylive.app.core.site.LiveSite
import com.mylive.app.core.site.LiveDanmaku
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopRoomControllerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun userQualityChoiceIsPersistedAndAppliedWhenOpeningAnotherRoom() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = DesktopLibrary(temporary.root.toPath().resolve("library.json"), dispatcher)
        library.updateDanmaku { it.copy(keywords = listOf("preserved")) }
        val player = FakePlayback()
        val controller = DesktopRoomController(listOf(FakeSite("douyu")), player, dispatcher, dispatcher, library)
        controller.open(RoomAddress("douyu", "first")); runCurrent()
        controller.selectQuality(LOW); runCurrent()
        assertEquals("low", library.state.value.player.qualityBySite["douyu"])
        controller.open(RoomAddress("douyu", "second")); runCurrent()
        assertEquals(listOf("first/high", "first/low", "second/low"), player.opened)
        assertEquals(listOf("preserved"), library.state.value.danmaku.keywords)
        controller.shutdown()
    }

    @Test fun failingQualityRequestStopsChatAndDoesNotLeaveChildrenOrAnUnhandledChannelError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val chat = mockk<LiveDanmakuSession>(relaxed = true)
        val site = object : StubLiveSite() {
            override fun createDanmakuSession(requestedRoomId: String, detail: LiveRoomDetail) = chat
            override suspend fun getPlayQualites(detail: LiveRoomDetail): List<LivePlayQuality> { delay(10); error("network") }
        }
        val controller = DesktopRoomController(listOf(site), FakePlayback(), dispatcher, dispatcher)
        controller.open(RoomAddress("douyu", "1")); runCurrent(); advanceTimeBy(10); runCurrent()
        assertNotNull(controller.state.value.error)
        assertFalse(controller.state.value.opening)
        coVerify(exactly = 1) { chat.stop() }
        controller.shutdown()
    }

    @Test fun superChatsAreDeduplicatedPrunedAndClearedWhenLeaving() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val now = System.currentTimeMillis() / 1000
        val paid = LiveSuperChatMessage("id", "payer", message = "paid", endTime = now + 60)
        val site = object : StubLiveSite() {
            override suspend fun getPlayQualites(detail: LiveRoomDetail) = listOf(HIGH)
            override suspend fun getPlayUrls(detail: LiveRoomDetail, quality: LivePlayQuality) = LivePlayUrl(listOf("fixture"))
            override suspend fun getSuperChatMessage(roomId: String, detail: LiveRoomDetail?) =
                listOf(paid, paid, paid.copy(id = "expired", endTime = now - 1))
        }
        val controller = DesktopRoomController(listOf(site), FakePlayback(), dispatcher, dispatcher)
        controller.open(RoomAddress("douyu", "1")); runCurrent()
        assertEquals(listOf(paid), controller.superChats.value)
        controller.leave(); runCurrent(); assertTrue(controller.superChats.value.isEmpty())
        controller.shutdown()
    }

    @Test fun staleRoomResponseNeverWritesHistoryAndNewRoomSurvivesRestart() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val oldResponse = CompletableDeferred<Unit>()
        val library = DesktopLibrary(temporary.root.toPath().resolve("library.json"), dispatcher)
        val site = FakeSite("douyu").apply {
            detail = { id ->
                if (id == "old") withContext(NonCancellable) { oldResponse.await() }
                room(id)
            }
        }
        val controller = DesktopRoomController(listOf(site), FakePlayback(), dispatcher, dispatcher, library)
        controller.open(RoomAddress("douyu", "old"))
        runCurrent()
        controller.open(RoomAddress("douyu", "new"))
        runCurrent()
        oldResponse.complete(Unit)
        runCurrent()
        controller.shutdown()
        val restarted = DesktopLibrary(temporary.root.toPath().resolve("library.json"), dispatcher)
        restarted.load()
        assertEquals(listOf("new"), restarted.state.value.history.map { it.roomId })
    }

    @Test fun localVideoDoesNotCreateLiveRoomHistory() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = DesktopLibrary(temporary.root.toPath().resolve("library.json"), dispatcher)
        val controller = DesktopRoomController(emptyList(), FakePlayback(), dispatcher, dispatcher, library)
        controller.openVideo("file:/sample.mp4")
        runCurrent()
        assertTrue(library.state.value.history.isEmpty())
        controller.shutdown()
    }
    @Test fun busyRoomKeepsOnlyRecentChatWithoutPublishingAnUnboundedList() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val chat = RecordingDanmaku()
        val site = FakeSite().apply { session = LiveDanmakuSession(chat, DanmakuArgs.Douyu("room")) }
        val controller = DesktopRoomController(listOf(site), FakePlayback(), dispatcher, dispatcher)
        controller.open(RoomAddress("fake", "room"))
        runCurrent()
        repeat(1000) { chat.onMessage?.invoke(LiveMessage(LiveMessageType.CHAT, "user", "message $it")) }
        advanceTimeBy(200)
        runCurrent()
        assertEquals(200, controller.messages.value.size)
        assertEquals("message 999", controller.messages.value.last().message.message)
        controller.shutdown()
    }

    @Test fun callbacksQueuedByOldRoomNeverEnterNewRoomChat() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val chat = RecordingDanmaku()
        val site = FakeSite().apply { session = LiveDanmakuSession(chat, DanmakuArgs.Douyu("old")) }
        val controller = DesktopRoomController(listOf(site), FakePlayback(), dispatcher, dispatcher)
        controller.open(RoomAddress("fake", "old"))
        runCurrent()
        val callback = chat.onMessage!!
        controller.open(RoomAddress("fake", "new"))
        runCurrent()
        callback(LiveMessage(LiveMessageType.CHAT, "old", "must not appear"))
        advanceTimeBy(100)
        runCurrent()
        assertTrue(controller.messages.value.isEmpty())
        controller.shutdown()
    }

    @Test fun lateResponseFromPreviousRoomCannotReplaceCurrentVideo() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val oldResponse = CompletableDeferred<Unit>()
        val site = FakeSite().apply {
            detail = { id ->
                if (id == "old") withContext(NonCancellable) { oldResponse.await() }
                room(id)
            }
        }
        val player = FakePlayback()
        val controller = DesktopRoomController(listOf(site), player, dispatcher, dispatcher)
        controller.open(RoomAddress("fake", "old"))
        runCurrent()
        controller.open(RoomAddress("fake", "new"))
        runCurrent()
        oldResponse.complete(Unit)
        runCurrent()
        assertEquals(listOf("new/high"), player.opened)
        assertEquals("new", controller.state.value.detail?.roomId)
        controller.shutdown()
    }

    @Test fun changingQualityCancelsStaleResolutionAndRetainsActualQuality() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val highResponse = CompletableDeferred<Unit>()
        val site = FakeSite().apply {
            urls = { detail, quality ->
                if (quality.quality == "high") withContext(NonCancellable) { highResponse.await() }
                LivePlayUrl(listOf("${detail.roomId}/${quality.quality}"), actualQuality = quality)
            }
        }
        val player = FakePlayback()
        val controller = DesktopRoomController(listOf(site), player, dispatcher, dispatcher)
        controller.open(RoomAddress("fake", "room"))
        runCurrent()
        controller.selectQuality(LOW)
        runCurrent()
        highResponse.complete(Unit)
        runCurrent()
        assertEquals(listOf("room/low"), player.opened)
        assertEquals(LOW, controller.state.value.quality)
        controller.shutdown()
    }

    @Test fun leavingRoomStopsPlayerAndClosesDanmaku() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val chat = mockk<LiveDanmakuSession>(relaxed = true)
        val site = FakeSite().apply { session = chat }
        val player = FakePlayback()
        val controller = DesktopRoomController(listOf(site), player, dispatcher, dispatcher)
        controller.open(RoomAddress("fake", "room"))
        runCurrent()
        controller.leave()
        runCurrent()
        coVerify(exactly = 1) { chat.stop() }
        assertNull(controller.state.value.address)
        assertTrue(controller.messages.value.isEmpty())
        assertTrue(player.stops >= 2)
        controller.shutdown()
        assertTrue(player.closed)
    }

    @Test fun oldLineCannotInterruptPendingQualityChange() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val lowResponse = CompletableDeferred<Unit>()
        val site = FakeSite().apply {
            urls = { _, quality ->
                if (quality == LOW) lowResponse.await()
                LivePlayUrl(listOf("${quality.quality}/first", "${quality.quality}/second"))
            }
        }
        val player = FakePlayback()
        val controller = DesktopRoomController(listOf(site), player, dispatcher, dispatcher)
        controller.open(RoomAddress("fake", "room"))
        runCurrent()
        controller.selectQuality(LOW)
        runCurrent()
        controller.nextLine()
        runCurrent()
        lowResponse.complete(Unit)
        runCurrent()
        assertEquals(listOf("high/first", "low/first"), player.opened)
        assertEquals(LOW, controller.state.value.quality)
        controller.shutdown()
    }

    @Test fun emptyQualityResponseClearsOldLinesAndCannotCrashLineSwitch() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val site = FakeSite().apply {
            urls = { _, quality ->
                LivePlayUrl(if (quality == LOW) emptyList() else listOf("first", "second"))
            }
        }
        val controller = DesktopRoomController(listOf(site), FakePlayback(), dispatcher, dispatcher)
        controller.open(RoomAddress("fake", "room"))
        runCurrent()
        controller.selectQuality(LOW)
        runCurrent()
        controller.nextLine()
        runCurrent()
        assertEquals(0, controller.state.value.lineCount)
        assertNotNull(controller.state.value.error)
        controller.shutdown()
    }

    @Test fun offlineRoomNeverRequestsPlayback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val player = FakePlayback()
        val site = FakeSite().apply { detail = { room(it).copy(status = false) } }
        val controller = DesktopRoomController(listOf(site), player, dispatcher, dispatcher)
        controller.open(RoomAddress("fake", "offline"))
        runCurrent()
        assertTrue(player.opened.isEmpty())
        assertEquals("主播暂未开播", controller.state.value.error)
        assertFalse(controller.state.value.opening)
        controller.shutdown()
    }

    private class FakePlayback : DesktopPlayback {
        val opened = mutableListOf<String>()
        var stops = 0
        var closed = false
        override suspend fun open(url: String, headers: Map<String, String>) { opened += url }
        override fun stop() { stops++ }
        override fun close() { closed = true }
    }

    private class RecordingDanmaku : LiveDanmaku {
        override var onMessage: ((LiveMessage) -> Unit)? = null
        override var onClose: ((String) -> Unit)? = null
        override var onReady: (() -> Unit)? = null
        override var heartbeatTime = 45_000
        override fun heartbeat() {}
        override suspend fun start(args: DanmakuArgs) { onReady?.invoke() }
        override suspend fun stop() {}
    }

    private class FakeSite(override val id: String = "fake") : LiveSite {
        override val name = "Fake"
        var session: LiveDanmakuSession? = null
        var detail: suspend (String) -> LiveRoomDetail = { room(it) }
        var urls: suspend (LiveRoomDetail, LivePlayQuality) -> LivePlayUrl = { room, quality ->
            LivePlayUrl(listOf("${room.roomId}/${quality.quality}"))
        }
        override fun createDanmakuSession(requestedRoomId: String, detail: LiveRoomDetail) = session ?: error("No chat")
        override suspend fun getRoomDetail(roomId: String) = detail(roomId)
        override suspend fun getPlayQualites(detail: LiveRoomDetail) = listOf(HIGH, LOW)
        override suspend fun getPlayUrls(detail: LiveRoomDetail, quality: LivePlayQuality) = urls(detail, quality)
        override suspend fun getCategories() = emptyList<LiveCategory>()
        override suspend fun searchRooms(keyword: String, page: Int) = LiveSearchRoomResult()
        override suspend fun searchAnchors(keyword: String, page: Int) = LiveSearchAnchorResult()
        override suspend fun getCategoryRooms(category: LiveSubCategory, page: Int) = LiveCategoryResult()
        override suspend fun getRecommendRooms(page: Int) = LiveCategoryResult()
        override suspend fun getLiveStatus(roomId: String) = true
        override suspend fun getSuperChatMessage(roomId: String, detail: LiveRoomDetail?) = emptyList<LiveSuperChatMessage>()
    }

    companion object {
        private val HIGH = LivePlayQuality("high", PlayQualityData.Douyin(emptyList()))
        private val LOW = LivePlayQuality("low", PlayQualityData.Douyin(emptyList()))
        private fun room(id: String) = LiveRoomDetail(id, "Room $id", "", "Anchor", status = true)
    }
}
