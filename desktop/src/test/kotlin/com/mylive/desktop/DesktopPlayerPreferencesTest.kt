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
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopPlayerPreferencesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun zeroVolumeAndDisabledPlayerTogglesSurviveRestartAndRoomWrites() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val path = temporary.root.toPath().resolve("library.json")
        val library = DesktopLibrary(path, dispatcher)
        val expected = DesktopPlayerPreferences(0f, false, false, false)
        assertTrue(library.updatePlayer { expected })
        library.recordVisit(RoomAddress("douyu", "1"), LiveRoomDetail("1", "Room", "", "Anchor"))
        library.importRooms(RoomProfileCodec.parse("[]"))
        val restarted = DesktopLibrary(path, dispatcher)
        restarted.load()
        assertEquals(expected, restarted.state.value.player)
    }

    @Test fun filesFromThePreviousDesktopVersionGetDefaultsWithoutLosingRooms() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val path = temporary.root.toPath().resolve("library.json")
        Files.writeString(path, """{"follows":[{"siteId":"douyu","roomId":"1","userName":"Old","title":"Room","savedAt":1}]}""")
        val library = DesktopLibrary(path, dispatcher)
        library.load()
        assertEquals(DesktopPlayerPreferences(), library.state.value.player)
        assertTrue(library.updatePlayer { it.copy(danmakuEnabled = false) })
        assertEquals("Old", library.state.value.follows.single().userName)
    }

    @Test fun concurrentPreferenceChangesPreserveBothFieldsAndOtherInstancesChanges() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val path = temporary.root.toPath().resolve("library.json")
        val first = DesktopLibrary(path, dispatcher)
        val second = DesktopLibrary(path, dispatcher)
        val volume = launch { first.updatePlayer { it.copy(volume = 22f) } }
        val danmaku = launch { first.updatePlayer { it.copy(danmakuEnabled = false) } }
        volume.join(); danmaku.join()
        second.updatePlayer { it.copy(chatVisible = false) }
        first.load()
        assertEquals(DesktopPlayerPreferences(volume = 22f, danmakuEnabled = false, chatVisible = false), first.state.value.player)
    }

    @Test fun invalidVolumeNeverReplacesThePreviousFileOrVisiblePreferences() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val path = temporary.root.toPath().resolve("library.json")
        val library = DesktopLibrary(path, dispatcher)
        library.updatePlayer { it.copy(volume = 25f) }
        val original = Files.readString(path)
        for (invalid in listOf(-1f, 101f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFalse(library.updatePlayer { it.copy(volume = invalid) })
            assertEquals(original, Files.readString(path))
            assertEquals(25f, library.state.value.player.volume)
        }
    }

    @Test fun malformedPreferencesMakeTheOriginalFileReadOnly() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val path = temporary.root.toPath().resolve("library.json")
        val original = """{"player":{"volume":101}}"""
        Files.writeString(path, original)
        val library = DesktopLibrary(path, dispatcher)
        library.load()
        assertFalse(library.state.value.writable)
        assertFalse(library.updatePlayer { DesktopPlayerPreferences() })
        assertEquals(original, Files.readString(path))
    }
}
