package com.mylive.app.core.site

import com.mylive.app.core.model.LiveAnchorItem
import com.mylive.app.core.model.LiveRoomItem

/** Keeps pagination stable for every screen that uses roomId as its lazy-list key. */
fun mergeRoomCatalogPage(
    existing: List<LiveRoomItem>,
    incoming: List<LiveRoomItem>,
    replace: Boolean
): List<LiveRoomItem> {
    return (if (replace) incoming else existing + incoming).distinctBy { it.roomId }
}

fun mergeAnchorCatalogPage(
    existing: List<LiveAnchorItem>,
    incoming: List<LiveAnchorItem>,
    replace: Boolean
): List<LiveAnchorItem> {
    return (if (replace) incoming else existing + incoming).distinctBy { it.roomId }
}

data class CatalogRequestToken internal constructor(
    val generation: Long
)

/** Rejects late platform responses even when the HTTP call ignored coroutine cancellation. */
class LatestCatalogRequestGate {
    private var generation = 0L

    fun begin(): CatalogRequestToken {
        generation += 1
        return CatalogRequestToken(generation = generation)
    }

    fun invalidate() {
        generation += 1
    }

    fun isCurrent(token: CatalogRequestToken): Boolean = token.generation == generation
}
