package com.mylive.app.core.site.douyu

internal const val DOUYU_DANMAKU_CONNECT_TIMEOUT_MILLIS = 3_000L

internal fun douyuDanmakuServerUrls(): List<String> =
    (8501..8506).map { port -> "wss://danmuproxy.douyu.com:$port" }
