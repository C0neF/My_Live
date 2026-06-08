package com.mylive.app.core.model

data class LiveContributionRankItem(
    val rank: Int,
    val userName: String,
    val avatar: String = "",
    val scoreText: String = "",
    val scoreDetail: String? = null,
    val userLevelText: String? = null,
    val userLevelIcon: String? = null,
    val userLevel: Int? = null,
    val fansName: String? = null,
    val fansIcon: String? = null,
    val fansLevel: Int? = null
)
