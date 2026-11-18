package com.mylive.app.core.site.douyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DouyinCookieInspectorTest {

    @Test
    fun extractsCookieFromRequestHeadersAndPreservesEqualsInsideValues() {
        val input = """
            Accept: application/json
            Cookie: ttwid=abc==; sessionid=top-secret; sid_guard=session%7C1000%7C3600
            Referer: https://www.douyin.com/
        """.trimIndent()

        assertEquals(
            "ttwid=abc==; sessionid=top-secret; sid_guard=session%7C1000%7C3600",
            DouyinCookieInspector.normalizeInput(input)
        )
        val inspection = DouyinCookieInspector.inspect(input)
        assertEquals(DouyinCookieKind.LoginStateShape, inspection.kind)
        assertEquals(4_600_000L, inspection.estimatedExpiryEpochMillis)
        assertFalse(inspection.toString().contains("top-secret"))
    }

    @Test
    fun distinguishesTtwidOnlyUnknownAndEmptyWithoutClaimingValidity() {
        assertEquals(
            "ttwid=bare-token",
            DouyinCookieInspector.normalizeInput("bare-token")
        )
        assertEquals(
            DouyinCookieKind.TtwidOnly,
            DouyinCookieInspector.inspect("ttwid=bare-token").kind
        )
        assertEquals(
            DouyinCookieKind.Unrecognized,
            DouyinCookieInspector.inspect("custom=value").kind
        )
        val empty = DouyinCookieInspector.inspect("")
        assertEquals(DouyinCookieKind.NotConfigured, empty.kind)
        assertNull(empty.estimatedExpiryEpochMillis)
    }
}
