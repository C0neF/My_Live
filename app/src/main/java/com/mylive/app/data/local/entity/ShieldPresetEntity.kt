package com.mylive.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shield_presets")
data class ShieldPresetEntity(
    @PrimaryKey
    val name: String,
    val value: String // JSON 字符串
)
