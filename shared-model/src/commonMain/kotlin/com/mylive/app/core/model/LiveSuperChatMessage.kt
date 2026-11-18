package com.mylive.app.core.model

/**
 * Paid SuperChat message displayed in live rooms.
 *
 * [startTime] and [endTime] are epoch timestamps in **milliseconds**.
 */
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
