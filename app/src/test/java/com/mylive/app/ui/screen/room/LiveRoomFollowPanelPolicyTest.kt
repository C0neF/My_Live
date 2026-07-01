package com.mylive.app.ui.screen.room

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveRoomFollowPanelPolicyTest {

    @Test
    fun liveRoomFollowPanelDoesNotRefreshAllFollowsOnOpen() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val panelSource = source.substringAfter("fun FollowListPanel(")
            .substringBefore("// ── Room Settings Panel")

        assertFalse(panelSource.contains("LaunchedEffect(Unit)"))
        assertFalse(panelSource.contains("viewModel.updateFollowStatus()"))
    }

    @Test
    fun liveRoomFollowPanelUsesStableLazyItemReuseBoundaries() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val panelSource = source.substringAfter("fun FollowListPanel(")
            .substringBefore("// ── Room Settings Panel")

        assertTrue(panelSource.contains("items("))
        assertTrue(panelSource.contains("key = { it.id }"))
        assertTrue(panelSource.contains("contentType = { \"room_follow\" }"))
    }
}
