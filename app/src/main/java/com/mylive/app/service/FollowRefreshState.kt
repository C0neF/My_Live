package com.mylive.app.service

data class FollowRefreshEntry(
    val refreshing: Boolean = false,
    val lastAttemptAtMillis: Long? = null,
    val lastSuccessAtMillis: Long? = null,
    val failed: Boolean = false
)

data class FollowRefreshProgress(
    val total: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val running: Boolean = false,
    val cancelled: Boolean = false,
    val error: String? = null
)
