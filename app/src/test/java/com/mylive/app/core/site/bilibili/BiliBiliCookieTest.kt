package com.mylive.app.core.site.bilibili

import org.junit.Assert.assertEquals
import org.junit.Test

class BiliBiliCookieTest {

    @Test
    fun parsesDedeUserId() {
        val uid = parseBilibiliUserIdFromCookie(
            "SESSDATA=abc; DedeUserID=12345678; bili_jct=token"
        )

        assertEquals(12345678, uid)
    }

    @Test
    fun returnsZeroWhenDedeUserIdIsMissing() {
        val uid = parseBilibiliUserIdFromCookie("SESSDATA=abc; bili_jct=token")

        assertEquals(0, uid)
    }
}
