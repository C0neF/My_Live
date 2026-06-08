package com.mylive.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LiveSearchRoomResult(
    val hasMore: Boolean = false,
    val items: List<LiveRoomItem> = emptyList()
)

@Serializable
data class LiveSearchAnchorResult(
    val hasMore: Boolean = false,
    val items: List<LiveAnchorItem> = emptyList()
)
