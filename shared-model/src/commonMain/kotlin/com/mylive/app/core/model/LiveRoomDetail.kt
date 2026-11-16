package com.mylive.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LiveRoomDetail(
    val roomId: String,
    val title: String,
    val cover: String,
    val userName: String,
    val userAvatar: String = "",
    val url: String = "",
    val online: Int = 0,
    val status: Boolean = false,
    val isRecord: Boolean = false,
    val introduction: String? = null,
    val notice: String? = null,
    val showTime: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val categoryParentId: String? = null,
    val categoryParentName: String? = null,
    val categoryPic: String? = null
)
// Note: `data` and `danmakuData` from Dart version are dynamic types.
// In Kotlin, these are NOT part of the serializable model.
// They will be passed separately through the site-specific code.
