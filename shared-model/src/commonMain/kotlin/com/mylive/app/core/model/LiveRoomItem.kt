package com.mylive.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LiveRoomItem(
    val roomId: String,
    val title: String,
    val cover: String,
    val userName: String,
    val faceUrl: String = "",
    val online: Int = 0
)
