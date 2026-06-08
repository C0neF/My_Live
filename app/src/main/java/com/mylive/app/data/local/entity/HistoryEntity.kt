package com.mylive.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "histories")
data class HistoryEntity(
    @PrimaryKey
    val id: String, // 格式: siteId_roomId
    val roomId: String,
    val siteId: String,
    val userName: String,
    val face: String,
    val updateTime: Long
)
