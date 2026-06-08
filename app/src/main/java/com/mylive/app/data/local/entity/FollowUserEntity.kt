package com.mylive.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "follow_users")
data class FollowUserEntity(
    @PrimaryKey
    val id: String, // 格式: siteId_roomId
    val roomId: String,
    val siteId: String,
    val userName: String,
    val face: String,
    val addTime: Long,
    val tag: String = "",
    val isSpecialFollow: Boolean = false,
    val liveStatus: Int = 0, // 0=未知, 1=直播中, 2=未开播
    val liveStartTime: Long? = null,
    val showTime: String? = null // 平台报告的开播时间戳（秒），仅 B站/斗鱼 有值
)
