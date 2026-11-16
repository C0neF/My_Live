package com.mylive.desktop

import com.mylive.app.core.model.LiveRoomDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopLibraryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun path() = temporary.root.toPath().resolve("profile/library.json")
    private fun detail(id: String, name: String = "Anchor $id") = LiveRoomDetail(
        id, "Title $id", "https://example.com/cover.jpg", name,
        url = "https://example.com/private?token=not-saved", status = true
    )

    @Test fun followAndHistorySurviveRestartWithoutStoringStreamCredentials() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = DesktopLibrary(path(), dispatcher) { 123 }
        val address = RoomAddress("douyu", "9999")
        library.recordVisit(address, detail("9999"))
        library.toggleFollow(address, detail("9999"))
        val restarted = DesktopLibrary(path(), dispatcher)
        restarted.load()
        assertEquals(library.state.value.follows, restarted.state.value.follows)
        assertEquals(library.state.value.history, restarted.state.value.history)
        assertFalse(Files.readString(path()).contains("not-saved"))
        assertTrue(restarted.toggleFollow(address, detail("9999")))
        assertTrue(restarted.state.value.follows.isEmpty())
        assertEquals(1, restarted.state.value.history.size)
    }

    @Test fun revisitMovesOneRecordToFrontAndUpdatesRoomMetadata() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var time = 0L
        val library = DesktopLibrary(path(), dispatcher) { ++time }
        library.recordVisit(RoomAddress("douyu", "1"), detail("1"))
        library.recordVisit(RoomAddress("douyu", "2"), detail("2"))
        library.recordVisit(RoomAddress("douyu", "1"), detail("1", "New name"))
        assertEquals(listOf("1", "2"), library.state.value.history.map { it.roomId })
        assertEquals("New name", library.state.value.history.first().userName)
        assertEquals(3L, library.state.value.history.first().savedAt)
    }

    @Test fun aliasAndCanonicalRoomMergeAndKeepAPlayableAddress() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = DesktopLibrary(path(), dispatcher)
        val alias = RoomAddress("douyin", "alias")
        library.toggleFollow(alias, detail("alias"))
        library.recordVisit(alias, detail("canonical"))
        library.recordVisit(RoomAddress("douyin", "canonical"), detail("canonical"))
        assertEquals(1, library.state.value.history.size)
        val follow = library.state.value.follows.single()
        assertEquals(RoomAddress("douyin", "canonical"), follow.address)
        assertTrue(follow.matches(alias))
        library.toggleFollow(alias, detail("canonical"))
        assertTrue(library.state.value.follows.isEmpty())
    }

    @Test fun sameRoomNumberOnDifferentPlatformsIsIndependent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = DesktopLibrary(path(), dispatcher)
        for (site in listOf("douyu", "huya")) {
            library.toggleFollow(RoomAddress(site, "1"), detail("1"))
            library.recordVisit(RoomAddress(site, "1"), detail("1"))
        }
        assertEquals(2, library.state.value.follows.size)
        assertEquals(2, library.state.value.history.size)
    }

    @Test fun historyIsBoundedAndClearNeverRemovesFollows() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = DesktopLibrary(path(), dispatcher)
        library.toggleFollow(RoomAddress("douyu", "1"), detail("1"))
        repeat(DesktopLibrary.MAX_HISTORY + 3) { library.recordVisit(RoomAddress("douyu", "$it"), detail("$it")) }
        assertEquals(DesktopLibrary.MAX_HISTORY, library.state.value.history.size)
        assertEquals("202", library.state.value.history.first().roomId)
        library.removeHistory("douyu:202")
        assertEquals(199, library.state.value.history.size)
        library.clearHistory()
        val restarted = DesktopLibrary(path(), dispatcher)
        restarted.load()
        assertTrue(restarted.state.value.history.isEmpty())
        assertEquals(1, restarted.state.value.follows.size)
    }

    @Test fun corruptFileIsPreservedAndEditsCannotOverwriteIt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Files.createDirectories(path().parent)
        Files.writeString(path(), "{broken")
        val library = DesktopLibrary(path(), dispatcher)
        library.load()
        assertFalse(library.state.value.writable)
        assertFalse(library.toggleFollow(RoomAddress("douyu", "1"), detail("1")))
        assertEquals("{broken", Files.readString(path()))
        assertNotNull(library.state.value.error)
        Files.writeString(path(), "{\"version\":1}")
        library.load()
        assertTrue(library.state.value.writable)
        assertNull(library.state.value.error)
        assertTrue(library.toggleFollow(RoomAddress("douyu", "1"), detail("1")))
    }

    @Test fun futureFileVersionIsPreserved() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Files.createDirectories(path().parent)
        val future = "{\"version\":2,\"newData\":true}"
        Files.writeString(path(), future)
        val library = DesktopLibrary(path(), dispatcher)
        assertFalse(library.clearHistory())
        assertEquals(future, Files.readString(path()))
        assertFalse(library.state.value.writable)
    }

    @Test fun failedWriteKeepsBothPreviousFileAndVisibleState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = DesktopLibrary(path(), dispatcher)
        library.toggleFollow(RoomAddress("douyu", "1"), detail("1"))
        val original = Files.readString(path())
        Files.delete(path().resolveSibling("library.json.lock"))
        Files.createDirectory(path().resolveSibling("library.json.lock"))
        assertFalse(library.toggleFollow(RoomAddress("douyu", "2"), detail("2")))
        assertEquals(original, Files.readString(path()))
        assertEquals(listOf("1"), library.state.value.follows.map { it.roomId })
        assertNotNull(library.state.value.error)
    }

    @Test fun concurrentSavesDoNotLoseRooms() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = DesktopLibrary(path(), dispatcher)
        val jobs = List(24) { id -> launch { library.toggleFollow(RoomAddress("douyu", "$id"), detail("$id")) } }
        jobs.forEach { it.join() }
        val restarted = DesktopLibrary(path(), dispatcher)
        restarted.load()
        assertEquals(24, restarted.state.value.follows.size)
    }

    @Test fun secondInstanceMergesDiskChangesInsteadOfOverwritingItsStaleSnapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = DesktopLibrary(path(), dispatcher)
        val second = DesktopLibrary(path(), dispatcher)
        first.load()
        second.load()
        first.toggleFollow(RoomAddress("douyu", "1"), detail("1"))
        second.toggleFollow(RoomAddress("huya", "2"), detail("2"))
        first.removeFollow("douyu:1")
        second.load()
        assertEquals(listOf("huya:2"), second.state.value.follows.map { it.key })
    }

    @Test fun busyFileReportsSaveFailureWithoutCancellingTheCaller() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = DesktopLibrary(path(), dispatcher)
        Files.createDirectories(path().parent)
        FileChannel.open(path().resolveSibling("library.json.lock"), CREATE, WRITE).use { channel ->
            channel.lock().use {
                assertFalse(library.toggleFollow(RoomAddress("douyu", "1"), detail("1")))
            }
        }
        assertNotNull(library.state.value.error)
        assertTrue(library.toggleFollow(RoomAddress("douyu", "1"), detail("1")))
    }
}
