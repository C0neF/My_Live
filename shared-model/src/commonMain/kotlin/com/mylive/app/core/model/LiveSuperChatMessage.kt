package com.mylive.app.core.model

data class LiveSuperChatMessage(
    val id: String? = null,
    val userName: String,
    val face: String = "",
    val message: String,
    val backgroundColor: String = "",
    val backgroundBottomColor: String = "",
    val price: Int = 0,
    val startTime: Long = 0L,
    val endTime: Long = 0L
)
