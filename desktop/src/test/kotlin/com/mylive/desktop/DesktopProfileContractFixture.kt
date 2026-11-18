package com.mylive.desktop

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DesktopProfileContractTest {
    @Test fun fullWindowsExportMatchesTheFixtureConsumedByAndroid() {
        val source = javaClass.getResourceAsStream("/android-compatible-full-profile.json")!!.bufferedReader().use { it.readText() }
        val actual = JSONObject(RoomProfileCodec.export(DesktopProfileContractFixture.state())).apply { remove("exportTime") }
        val expected = JSONObject(source).apply { remove("exportTime") }
        assertTrue(expected.similar(actual))
    }
}

object DesktopProfileContractFixture {
    fun state() = DesktopLibraryState(loaded = true,
        follows = listOf(SavedRoom("douyu", "9999", "Contract Anchor", "Contract room", savedAt = 100,
            pinned = true, tags = listOf("Games", "Favorites"), note = "Contract note")),
        history = listOf(SavedRoom("douyu", "9999", "Contract Anchor", "Contract room", savedAt = 200)),
        appearance = DesktopAppearancePreferences(theme = 1, platformOrder = listOf("douyu", "bilibili", "huya", "douyin")),
        player = DesktopPlayerPreferences(volume = 25f, hardwareDecode = false, danmakuEnabled = false),
        danmaku = DesktopDanmakuPreferences(fontSize = 24f, keywords = listOf("blocked-keyword"), users = listOf(DesktopBlockedUser("douyu", "blocked-user"))))
    @JvmStatic fun main(args: Array<String>) { Files.writeString(Path.of(args[0]), RoomProfileCodec.export(state())) }
}
