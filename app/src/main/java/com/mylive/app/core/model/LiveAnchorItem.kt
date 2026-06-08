package com.mylive.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LiveAnchorItem(
    val roomId: String,
    val avatar: String = "",
    val userName: String,
    val liveStatus: Boolean = false
)
