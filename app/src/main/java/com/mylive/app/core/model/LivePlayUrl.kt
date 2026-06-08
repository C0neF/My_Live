package com.mylive.app.core.model

data class LivePlayUrl(
    val urls: List<String>,
    val headers: Map<String, String>? = null
)
