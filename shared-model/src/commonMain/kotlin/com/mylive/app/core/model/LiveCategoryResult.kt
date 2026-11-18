package com.mylive.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LiveCategoryResult(
    val hasMore: Boolean = false,
    val items: List<LiveRoomItem> = emptyList()
)
