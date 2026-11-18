package com.mylive.app.core.model

data class LivePlayUrl(
    val urls: List<String>,
    val headers: Map<String, String>? = null,
    val actualQuality: LivePlayQuality? = null,
    /** Resolved source metadata; only a decoder hint when the container omits its frame rate. */
    val videoFrameRateHint: Float? = null
)
