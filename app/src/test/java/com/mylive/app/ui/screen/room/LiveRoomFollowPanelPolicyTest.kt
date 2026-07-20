package com.mylive.app.ui.screen.room

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveRoomFollowPanelPolicyTest {

    @Test
    fun liveRoomFollowPanelDoesNotRefreshAllFollowsOnOpen() {
        val panelSource = followListPanelSource()

        assertFalse(panelSource.contains("LaunchedEffect(Unit)"))
        assertFalse(panelSource.contains("LaunchedEffect(Unit) { viewModel.updateFollowStatus() }"))
        // Status refresh is user-driven via the chip-row refresh button only.
        assertFalse(panelSource.contains("PullToRefreshBox("))
        assertTrue(panelSource.contains("viewModel.updateFollowStatus()"))
    }

    @Test
    fun liveRoomFollowPanelPlacesRefreshBesideFilterChips() {
        val panelSource = followListPanelSource()

        assertTrue(panelSource.contains("listOf(\"全部\", \"直播中\", \"未开播\")"))
        assertTrue(panelSource.contains("viewModel.updatingStatus.collectAsStateWithLifecycle()"))
        assertTrue(panelSource.contains("onClick = { viewModel.updateFollowStatus() }"))
        assertTrue(panelSource.contains("contentDescription = \"刷新关注状态\""))
        assertTrue(panelSource.contains("Icons.Default.Refresh"))
        // Refresh lives on the same row as the filter chips, not pull-to-refresh.
        val chipRow = panelSource.substringAfter("val options = listOf(\"全部\", \"直播中\", \"未开播\")")
            .substringBefore("if (filteredFollows.isEmpty())")
        assertTrue(chipRow.contains("updateFollowStatus()"))
        assertTrue(chipRow.contains("Icons.Default.Refresh"))
    }

    @Test
    fun liveRoomFollowPanelUsesStableLazyItemReuseBoundaries() {
        val panelSource = followListPanelSource()

        assertTrue(panelSource.contains("items("))
        assertTrue(panelSource.contains("key = { it.id }"))
        assertTrue(panelSource.contains("contentType = { \"room_follow\" }"))
    }

    private fun followListPanelSource(): String {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        return source.substringAfter("fun FollowListPanel(")
            .substringBefore("// ── Room Settings Panel")
    }
}
