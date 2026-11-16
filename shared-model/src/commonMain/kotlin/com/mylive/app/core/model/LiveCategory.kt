package com.mylive.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LiveCategory(
    val name: String,
    val id: String,
    val children: List<LiveSubCategory> = emptyList()
)

@Serializable
data class LiveSubCategory(
    val name: String,
    val id: String,
    val parentId: String = "",
    val pic: String? = null
)
