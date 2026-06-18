package com.mylive.app.core.site.douyu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DouyuPlaybackPolicyTest {

    @Test
    fun douyuSignArgsExpireBeforePlaybackRefreshReusesThem() {
        val cached = DouyuSignArgsCacheEntry(
            value = "rid=123&time=1000&sign=old",
            createdAtMillis = 1_000L
        )

        assertFalse(
            shouldRefreshDouyuSignArgs(
                cached = cached,
                nowMillis = 1_000L + DOUYU_SIGN_ARGS_TTL_MS - 1
            )
        )
        assertTrue(
            shouldRefreshDouyuSignArgs(
                cached = cached,
                nowMillis = 1_000L + DOUYU_SIGN_ARGS_TTL_MS
            )
        )
        assertTrue(shouldRefreshDouyuSignArgs(cached = null, nowMillis = 1_000L))
        assertTrue(shouldRefreshDouyuSignArgs(cached = cached, nowMillis = 999L))
    }

    @Test
    fun douyuPlayUrlRefreshCanFetchFreshSignArgsWithoutReloadingApp() {
        val source = File("src/main/java/com/mylive/app/core/site/douyu/DouyuSite.kt").readText()

        assertTrue(source.contains("private suspend fun getFreshSignArgs("))
        assertTrue(source.contains("shouldRefreshDouyuSignArgs("))
        assertTrue(source.contains("val signArgs = getFreshSignArgs(detail.roomId)"))
    }
}
