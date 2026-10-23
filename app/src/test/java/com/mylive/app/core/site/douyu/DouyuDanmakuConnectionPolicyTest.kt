package com.mylive.app.core.site.douyu

import org.junit.Assert.assertEquals
import org.junit.Test

class DouyuDanmakuConnectionPolicyTest {

    @Test
    fun exposesAllSixStableDanmakuPortsWithoutDuplicates() {
        val urls = douyuDanmakuServerUrls()

        assertEquals(
            (8501..8506).map { "wss://danmuproxy.douyu.com:$it" },
            urls
        )
        assertEquals(urls.size, urls.toSet().size)
        assertEquals(3_000L, DOUYU_DANMAKU_CONNECT_TIMEOUT_MILLIS)
    }
}
