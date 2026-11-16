package com.mylive.desktop

import com.mylive.app.core.model.LiveRoomDetail
import com.mylive.app.core.site.FollowPresence
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopFollowControllerTest {
    @get:Rule val folder = TemporaryFolder()
    private fun room(id: String) = LiveRoomDetail(id, "Room", "", "Anchor")

    @Test fun statusRefreshCannotResurrectAnUnfollowedRoom() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val library = DesktopLibrary(folder.root.toPath().resolve("library.json"), dispatcher)
        library.toggleFollow(RoomAddress("douyu", "1"), room("1"))
        val site = object : StubLiveSite() { override suspend fun getFollowPresence(roomId: String): FollowPresence {
            gate.await(); return FollowPresence(true)
        } }
        val controller = DesktopFollowController(library, listOf(site), dispatcher, dispatcher)
        controller.refresh(); runCurrent()
        library.removeFollow("douyu:1"); gate.complete(Unit); runCurrent()
        assertTrue(library.state.value.follows.isEmpty())
        assertFalse(controller.state.value.refreshing)
        controller.close()
    }

    @Test fun refreshBoundsConcurrentRequestsAndKeepsMetadataThroughCanonicalUpdates() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = DesktopLibrary(folder.root.toPath().resolve("library.json"), dispatcher) { 500 }
        repeat(8) { library.toggleFollow(RoomAddress("douyu", "$it"), room("$it")) }
        library.editFollow("douyu:1", true, listOf("Games", "Daily"), "note")
        val gate = CompletableDeferred<Unit>()
        var running = 0; var peak = 0
        val site = object : StubLiveSite() { override suspend fun getFollowPresence(roomId: String): FollowPresence {
            running++; peak = maxOf(peak, running)
            gate.await(); running--
            return FollowPresence(true, canonicalRoomId = "canonical$roomId")
        } }
        val controller = DesktopFollowController(library, listOf(site), dispatcher, dispatcher)
        controller.refresh(); runCurrent(); assertEquals(4, running)
        gate.complete(Unit); runCurrent()
        assertEquals(4, peak)
        assertEquals(8, controller.state.value.completed)
        val updated = library.state.value.follows.single { it.roomId == "canonical1" }
        assertTrue(updated.pinned); assertEquals(listOf("Games", "Daily"), updated.tags)
        assertEquals("note", updated.note); assertEquals(1, updated.liveStatus)
        assertEquals(500L, updated.lastCheckedAt)
        controller.close()
    }

    @Test fun failedStatusRequestKeepsThePreviousSuccessfulStatus() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = DesktopLibrary(folder.root.toPath().resolve("library.json"), dispatcher)
        library.toggleFollow(RoomAddress("douyu", "1"), room("1"))
        library.applyPresences(mapOf("douyu:1" to FollowPresence(true)))
        val checked = library.state.value.follows.single().lastCheckedAt
        val site = object : StubLiveSite() { override suspend fun getFollowPresence(roomId: String): FollowPresence = error("offline") }
        val controller = DesktopFollowController(library, listOf(site), dispatcher, dispatcher)
        controller.refresh(); runCurrent()
        assertEquals(1, library.state.value.follows.single().liveStatus)
        assertEquals(checked, library.state.value.follows.single().lastCheckedAt)
        assertNotNull(controller.state.value.error)
        controller.close()
    }

    @Test fun automaticRefreshStopsWhenDisabledAndWhenControllerCloses() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = DesktopLibrary(folder.root.toPath().resolve("library.json"), dispatcher)
        library.toggleFollow(RoomAddress("douyu", "1"), room("1"))
        var requests = 0
        val site = object : StubLiveSite() { override suspend fun getFollowPresence(roomId: String): FollowPresence { requests++; return FollowPresence(true) } }
        val controller = DesktopFollowController(library, listOf(site), dispatcher, dispatcher)
        controller.startAutoRefresh(); runCurrent(); assertEquals(1, requests)
        advanceTimeBy(60_000); runCurrent(); assertEquals(2, requests)
        library.updateFollowOptions { it.copy(autoRefresh = false) }; runCurrent()
        advanceTimeBy(120_000); runCurrent(); assertEquals(2, requests)
        controller.close(); advanceTimeBy(120_000); runCurrent(); assertEquals(2, requests)
    }

    @Test fun visitsAndRestartRetainPinnedGroupsAndNotes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val file = folder.root.toPath().resolve("library.json")
        val library = DesktopLibrary(file, dispatcher)
        library.toggleFollow(RoomAddress("douyu", "1"), room("1"))
        library.editFollow("douyu:1", true, listOf("Games", "Daily"), "private note")
        library.recordVisit(RoomAddress("douyu", "1"), room("1").copy(status = true))
        val restarted = DesktopLibrary(file, dispatcher); restarted.load()
        val followed = restarted.state.value.follows.single()
        assertTrue(followed.pinned); assertEquals(listOf("Games", "Daily"), followed.tags)
        assertEquals("private note", followed.note)
        assertEquals(1, followed.liveStatus)
    }
}
