package com.mylive.app.core.site.douyin

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure-Kotlin parts of Douyin signing (the JS-engine-backed parts need the native
 * QuickJS runtime and are covered by instrumented tests). [DouyinSign.getMsStub] and
 * [DouyinSign.generateMsToken] are deterministic / format-constrained and unit-testable here.
 */
class DouyinSignTest {

    private val sign = DouyinSign(mockk(relaxed = true))

    @Test
    fun msStubIsAStableLowercaseMd5Hex() {
        val a = sign.getMsStub("123456", "7382872326016435738")
        assertEquals(32, a.length)
        assertTrue("expected 32-char lowercase hex but was '$a'", a.matches(Regex("[0-9a-f]{32}")))
        // Deterministic for identical inputs.
        assertEquals(a, sign.getMsStub("123456", "7382872326016435738"))
    }

    @Test
    fun msStubVariesWithRoomAndUser() {
        val base = sign.getMsStub("111111", "user-1")
        assertNotEquals(base, sign.getMsStub("222222", "user-1"))
        assertNotEquals(base, sign.getMsStub("111111", "user-2"))
    }

    @Test
    fun generateMsTokenHasRequestedLengthAndIsAlphanumeric() {
        val token = sign.generateMsToken(107)
        assertEquals(107, token.length)
        assertTrue(token.all { it.isLetterOrDigit() })
    }
}
