package com.mylive.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shields")
data class ShieldEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val value: String // 带前缀格式: keyword:xxx, user:siteId:xxx, user:__all__:xxx
)
