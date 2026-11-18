package com.mylive.desktop

import com.mylive.app.core.common.MAX_IMPORTED_JSON_BYTES
import com.mylive.app.core.model.LiveRoomDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class RoomProfileCodecTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun fixture() = javaClass.getResourceAsStream("/android-compatible-room-profile.json")!!.bufferedReader().use { it.readText() }
    private fun saved(id: String, time: Long, aliases: List<String> = emptyList()) =
        SavedRoom("douyu", id, "Local name", "Local title", "cover", savedAt = time, aliases = aliases)

    @Test fun androidV3RoomExportRoundTripsAndMatchesTheAndroidConsumerFixture() {
        val plan = RoomProfileCodec.parse(fixture())
        assertEquals(2, plan.follows.size)
        assertEquals(1, plan.history.size)
        val state = DesktopLibraryState(loaded = true, follows = plan.follows, history = plan.history,
            player = DesktopPlayerPreferences(volume = 0f, danmakuEnabled = false))
        val output = JSONObject(RoomProfileCodec.export(state)).apply { remove("exportTime") }
        val expected = JSONObject(fixture()).apply { remove("exportTime") }
        assertTrue(expected.getJSONArray("followUsers").similar(output.getJSONArray("followUsers")))
        assertTrue(expected.getJSONArray("histories").similar(output.getJSONArray("histories")))
        assertEquals(0f, RoomProfileCodec.parse(output.toString()).preferences!!.player.volume)
    }

    @Test fun androidCanonicalDouyinIdAndLegacyTimestampsArePreserved() {
        val plan = RoomProfileCodec.parse("""{"type":"my_live_profile","schemaVersion":3,
            "followUsers":[{"siteId":"douyin","roomId":"alias","canonicalRoomId":"canonical","addTime":"2026-10-25T00:00:00Z"}],
            "histories":[{"siteId":"douyu","roomId":9999,"updateTime":"123"}]}""")
        assertEquals("canonical", plan.follows.single().roomId)
        assertEquals(listOf("alias"), plan.follows.single().aliases)
        assertEquals(123L, plan.history.single().savedAt)
        assertTrue(plan.follows.single().savedAt > 0)
    }

    @Test fun legacyFollowArrayAndBoxedProfileAreAccepted() {
        val row = """{"site":"douyu","room":"9999","name":"Legacy","avatar":"avatar"}"""
        val array = RoomProfileCodec.parse("[$row]", now = 12)
        assertEquals("Legacy", array.follows.single().userName)
        assertEquals(12L, array.follows.single().savedAt)
        val boxed = RoomProfileCodec.parse("""{"boxes":{"followUsers":[$row],"histories":[$row]}}""", now = 13)
        assertEquals("avatar", boxed.follows.single().userAvatar)
        assertEquals(13L, boxed.history.single().savedAt)
        assertEquals(1, RoomProfileCodec.parse("""{"follows":[$row],"tags":[]}""").follows.size)
    }

    @Test fun emptyCanonicalAliasFallsBackToOriginalRoom() {
        val plan = RoomProfileCodec.parse("""[{"siteId":"douyin","roomId":"alias","canonicalRoomId":""}]""")
        assertEquals("alias", plan.follows.single().roomId)
    }

    @Test fun unsupportedVersionsTypesAndInvalidCollectionsAreRejected() {
        val invalid = listOf(
            """{"type":"other","schemaVersion":3,"followUsers":[]}""",
            """{"type":"my_live_profile","schemaVersion":4,"followUsers":[]}""",
            """{"type":"my_live_profile","schemaVersion":2.5,"followUsers":[]}""",
            """{"type":"my_live_profile","followUsers":[]}""",
            """{"followUsers":{}}""", """{"boxes":[]}"""
        )
        invalid.forEach { value -> assertThrows(Exception::class.java) { RoomProfileCodec.parse(value) } }
    }

    @Test fun invalidRoomAndTimestampFieldsAreRejected() {
        val invalid = listOf(
            """{"siteId":"unknown","roomId":"1"}""",
            """{"siteId":"douyu","roomId":1.5}""",
            """{"siteId":"douyu","roomId":"../file"}""",
            """{"siteId":"douyu","roomId":"1","userName":12}""",
            """{"siteId":"douyu","roomId":"1","addTime":true}""",
            """{"siteId":"douyu","roomId":"1","addTime":1.5}"""
        )
        invalid.forEach { value -> assertThrows(Exception::class.java) { RoomProfileCodec.parse("[$value]") } }
    }

    @Test fun trailingJsonAndUtf8OversizeInputAreRejected() {
        assertThrows(Exception::class.java) { RoomProfileCodec.parse("[] {}") }
        val text = "界".repeat(MAX_IMPORTED_JSON_BYTES / 3 + 1)
        assertTrue(text.length < MAX_IMPORTED_JSON_BYTES)
        assertThrows(IllegalArgumentException::class.java) { RoomProfileCodec.parse(text) }
        val file = temporary.root.toPath().resolve("large.json")
        Files.writeString(file, text)
        assertThrows(IllegalArgumentException::class.java) { RoomProfileCodec.read(file) }
    }

    @Test fun ignoredCredentialsAndDeviceSettingsNeverEnterTheRoomPlan() {
        val source = """{"followUsers":[{"siteId":"douyu","roomId":"1"}],
            "settings":{"DouyuCookie":"private","SyncServerUrl":"http://private"},"followUserTags":[],"danmuShield":[]}"""
        val plan = RoomProfileCodec.parse(source)
        val output = RoomProfileCodec.export(DesktopLibraryState(loaded = true, follows = plan.follows))
        assertFalse(output.contains("private"))
        assertFalse(output.contains("SyncServerUrl"))
    }

    @Test fun allRowsAreParsedBeforeAnyLocalDataCanChange() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val path = temporary.root.toPath().resolve("library.json")
        val library = DesktopLibrary(path, dispatcher)
        library.toggleFollow(RoomAddress("douyu", "1"), LiveRoomDetail("1", "Local", "", "Local"))
        val original = Files.readString(path)
        try {
            library.importRooms(RoomProfileCodec.parse("""{"followUsers":[{"siteId":"douyu","roomId":"2"}],"histories":[{}]}"""))
            fail("Invalid history must reject the whole import")
        } catch (_: IllegalArgumentException) {}
        assertEquals(original, Files.readString(path))
    }

    @Test fun mergingKeepsNewerHistoryLocalMetadataAndCanonicalIdentity() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = DesktopLibrary(temporary.root.toPath().resolve("library.json"), dispatcher) { 500 }
        library.recordVisit(RoomAddress("douyu", "alias"), LiveRoomDetail("canonical", "Rich title", "cover", "Local"))
        library.toggleFollow(RoomAddress("douyu", "alias"), LiveRoomDetail("canonical", "Rich title", "cover", "Local"))
        val plan = RoomProfileImport(listOf(saved("alias", 10)), listOf(saved("alias", 600).copy(title = "", cover = "")))
        assertTrue(library.importRooms(plan))
        assertEquals(1, library.state.value.follows.size)
        val history = library.state.value.history.single()
        assertEquals("canonical", history.roomId)
        assertEquals(600L, history.savedAt)
        assertEquals("Rich title", history.title)
        assertEquals("cover", history.cover)
        assertTrue(history.matches(RoomAddress("douyu", "alias")))
    }

    @Test fun importedHistoryIsSortedDeduplicatedAndCappedWhileEmptyImportRetainsExistingRooms() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = DesktopLibrary(temporary.root.toPath().resolve("library.json"), dispatcher)
        assertTrue(library.importRooms(RoomProfileImport(listOf(saved("keep", 1)), List(250) { saved("$it", it.toLong()) })))
        assertTrue(library.importRooms(RoomProfileImport(emptyList(), listOf(saved("249", 999)))))
        assertTrue(library.importRooms(RoomProfileImport(emptyList(), emptyList())))
        assertEquals(DesktopLibrary.MAX_HISTORY, library.state.value.history.size)
        assertEquals("249", library.state.value.history.first().roomId)
        assertEquals(999L, library.state.value.history.first().savedAt)
        assertEquals("keep", library.state.value.follows.single().roomId)
    }

    @Test fun aliasChainsMergeTransitivelyAndKeepPlatformsIndependent() {
        val rooms = mergeSavedRooms(listOf(saved("a", 10), saved("b", 20, listOf("a")),
            saved("c", 30, listOf("b")), saved("a", 40).copy(siteId = "huya")))
        assertEquals(2, rooms.size)
        assertEquals("c", rooms.single { it.siteId == "douyu" }.roomId)
        assertTrue(rooms.single { it.siteId == "douyu" }.aliases.containsAll(listOf("a", "b")))
    }

    @Test fun revisitingImportedShortAndCanonicalFollowsCollapsesTheDuplicateRecords() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = DesktopLibrary(temporary.root.toPath().resolve("library.json"), dispatcher) { 300 }
        assertTrue(library.importRooms(RoomProfileImport(listOf(saved("short", 100), saved("canonical", 200)), emptyList())))
        assertTrue(library.recordVisit(RoomAddress("douyu", "short"), LiveRoomDetail("canonical", "Room", "", "Anchor")))
        assertEquals(1, library.state.value.follows.size)
        assertEquals("canonical", library.state.value.follows.single().roomId)
        assertTrue(library.state.value.follows.single().matches(RoomAddress("douyu", "short")))
        assertEquals(1, library.state.value.history.size)
    }

    @Test fun exportNeverOverwritesAnExistingFile() {
        val file = temporary.root.toPath().resolve("backup.json")
        val state = DesktopLibraryState(loaded = true, follows = listOf(saved("1", 1)))
        RoomProfileCodec.writeNew(file, state)
        val original = Files.readString(file)
        assertThrows(FileAlreadyExistsException::class.java) { RoomProfileCodec.writeNew(file, state.copy(follows = emptyList())) }
        assertEquals(original, Files.readString(file))
    }
}
