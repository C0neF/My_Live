package com.mylive.app.core.site

import com.mylive.app.core.model.LiveAnchorItem
import com.mylive.app.core.model.LiveRoomItem
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomCatalogPolicyTest {

    @Test
    fun onlyTheLatestCatalogRequestMayPublish() {
        val gate = LatestCatalogRequestGate()
        val bilibili = gate.begin()
        val douyu = gate.begin()

        assertEquals(false, gate.isCurrent(bilibili))
        assertEquals(true, gate.isCurrent(douyu))

        gate.invalidate()
        assertEquals(false, gate.isCurrent(douyu))
    }

    @Test
    fun firstAndLaterPagesUseTheSameStableRoomDedupePolicy() {
        val old = room("1", "old")
        val refreshed = room("1", "refreshed")
        val next = room("2", "next")

        assertEquals(
            listOf(refreshed),
            mergeRoomCatalogPage(existing = listOf(old), incoming = listOf(refreshed, refreshed), replace = true)
        )
        assertEquals(
            listOf(old, next),
            mergeRoomCatalogPage(existing = listOf(old), incoming = listOf(old, next, next), replace = false)
        )
    }

    @Test
    fun anchorsAreAlsoDedupedBeforeUsingRoomIdAsAListKey() {
        val first = LiveAnchorItem(roomId = "1", userName = "first")
        val duplicate = LiveAnchorItem(roomId = "1", userName = "duplicate")

        assertEquals(
            listOf(first),
            mergeAnchorCatalogPage(emptyList(), listOf(first, duplicate), replace = true)
        )
    }

    private fun room(id: String, title: String) = LiveRoomItem(
        roomId = id,
        title = title,
        cover = "",
        userName = "anchor"
    )
}
