package com.mylive.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.mylive.app.data.local.Converters

@Entity(tableName = "follow_user_tags")
@TypeConverters(Converters::class)
data class FollowUserTagEntity(
    @PrimaryKey
    val id: String, // UUID
    val tag: String,
    val userIds: List<String>
)
