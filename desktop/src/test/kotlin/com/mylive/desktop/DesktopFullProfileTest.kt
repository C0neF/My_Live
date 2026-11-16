package com.mylive.desktop

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopFullProfileTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun scaleModesPreserveAndroidMeaningsInBothDirections() {
        for ((androidMode, expected) in listOf(0 to DesktopVideoScale.Fit, 1 to DesktopVideoScale.Stretch, 2 to DesktopVideoScale.Crop)) {
            val imported = RoomProfileCodec.parse("""{"followUsers":[],"settings":{"ScaleMode":$androidMode}}""")
            assertEquals(expected, imported.preferences!!.player.scale)
            val exported = JSONObject(RoomProfileCodec.export(DesktopLibraryState(loaded = true, player = DesktopPlayerPreferences(scale = expected))))
            assertEquals(androidMode, exported.getJSONObject("settings").getInt("ScaleMode"))
        }
        for (androidMode in listOf(3, 4)) {
            val imported = RoomProfileCodec.parse("""{"followUsers":[],"settings":{"ScaleMode":$androidMode}}""")
            assertEquals(DesktopVideoScale.Fit, imported.preferences!!.player.scale)
        }
    }
    @Test fun fullWindowsProfileSurvivesRestartAndExportsAndroidCompatibleMemberships() = runTest {
        val room = SavedRoom("douyu", "9999", "主播", "直播", savedAt = 1, pinned = true,
            tags = listOf("游戏", "常看"), note = "备注", aliases = listOf("alias"))
        val prefs = DesktopProfilePreferences(
            player = DesktopPlayerPreferences(volume = 23f, muted = true, qualityBySite = mapOf("douyu" to "2K120")),
            appearance = DesktopAppearancePreferences(theme = 1, platformOrder = listOf("douyu", "huya", "douyin", "bilibili"), textScalePercent = 110),
            danmaku = DesktopDanmakuPreferences(keywords = listOf("屏蔽词"), users = listOf(DesktopBlockedUser("douyu", "用户"))))
        val state = DesktopLibraryState(loaded = true, follows = listOf(room), player = prefs.player,
            appearance = prefs.appearance, danmaku = prefs.danmaku)
        val encoded = RoomProfileCodec.export(state)
        val root = JSONObject(encoded)
        assertEquals("douyu_9999", root.getJSONArray("followUserTags").getJSONObject(0).getJSONArray("userIds").getString(0))
        val library = DesktopLibrary(temporary.root.toPath().resolve("profile.json"))
        assertTrue(library.importRooms(RoomProfileCodec.parse(encoded)))
        library.load()
        assertEquals(room, library.state.value.follows.single())
        assertEquals(prefs, RoomProfileCodec.parse(RoomProfileCodec.export(library.state.value)).preferences)
    }
    @Test fun androidGroupsNotesAndShieldsMigrateWithoutCredentialsOrSyncAddresses() {
        val plan = RoomProfileCodec.parse("""{"followUsers":[{"siteId":"douyu","roomId":"1","userName":"A"}],
          "followUserTags":[{"tag":"常看","userIds":["douyu_1"]}],
          "settings":{"ThemeMode":1,"UserRemarks":"{\"douyu::A\":\"备注\"}","DouyuCookie":"secret","SyncServerUrl":"http://secret"},
          "danmuShield":[{"value":"keyword:广告"},{"value":"user:douyu:坏用户"}]}""")
        assertEquals(listOf("常看"), plan.follows.single().tags)
        assertEquals("备注", plan.follows.single().note)
        assertEquals(listOf("广告"), plan.preferences!!.danmaku.keywords)
        assertEquals(1, plan.preferences!!.appearance.theme)
        assertFalse(plan.toString().contains("secret"))
    }
    @Test fun invalidSettingsRejectWholeImportAndReorderingCannotDropPlatforms() = runTest {
        val path = temporary.root.toPath().resolve("profile.json")
        val library = DesktopLibrary(path)
        library.load()
        val source = JSONObject(RoomProfileCodec.export(library.state.value))
        source.getJSONObject("desktop").getJSONObject("player").put("volume", 1000)
        assertThrows(IllegalArgumentException::class.java) { RoomProfileCodec.parse(source.toString()) }
        val order = DesktopAppearancePreferences().platformOrder
        assertEquals(listOf("douyu", "bilibili", "huya", "douyin"), moveDesktopPlatform(order, 1, -1))
        assertEquals(order, moveDesktopPlatform(order, 0, -1))
        assertFalse(library.updateAppearance { it.copy(platformOrder = listOf("douyu")) })
        assertEquals(order, library.state.value.appearance.platformOrder)
    }
    @Test fun androidCustomRowIdsStillReceiveTheirGroupMembership() {
        val plan = RoomProfileCodec.parse("""{"followUsers":[{"id":"custom-row","siteId":"douyu","roomId":"1"}],
          "followUserTags":[{"tag":"Group","userIds":["custom-row"]}]}""")
        assertEquals(listOf("Group"), plan.follows.single().tags)
    }
    @Test fun malformedOptionalSectionsCannotSilentlyProduceAPartialRestore() {
        for (invalid in listOf("""{"followUsers":[],"settings":[]}""", """{"followUsers":[],"followUserTags":{}}""",
            """{"followUsers":[],"desktop":[]}""")) assertThrows(IllegalArgumentException::class.java) { RoomProfileCodec.parse(invalid) }
    }
}
