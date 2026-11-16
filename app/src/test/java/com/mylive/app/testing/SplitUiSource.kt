package com.mylive.app.testing

import java.io.File

/**
 * LiveRoomScreen and PlayerView were split into neighbouring files. Policy tests that used to read
 * the single source still see the composables in their original order.
 */
fun liveRoomUiSource(): String = joinSources(
    "src/main/java/com/mylive/app/ui/screen/room",
    listOf(
        "LiveRoomScreen.kt",
        "LiveRoomPortraitLayout.kt",
        "LiveRoomLandscapeLayout.kt",
        "LiveRoomPortraitChrome.kt",
        "LiveRoomQualitySheet.kt",
        "LiveRoomChatPanel.kt",
        "LiveRoomSuperChatPanel.kt",
        "LiveRoomFollowPanel.kt",
        "LiveRoomSettingsPanel.kt"
    )
)

fun playerViewSource(): String = joinSources(
    "src/main/java/com/mylive/app/ui/screen/room/player",
    listOf(
        "PlayerView.kt",
        "PlayerViewChrome.kt",
        "PlayerViewSheets.kt"
    )
)

private fun joinSources(directory: String, names: List<String>): String {
    val roots = listOf(File(directory), File("My_Live/app/$directory"))
    val root = roots.firstOrNull { it.isDirectory } ?: error("Cannot find $directory")
    return names.joinToString("\n") { name -> root.resolve(name).readText() }
}
