package com.mylive.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class BoundedInputTest {

    @Test
    fun readsUtf8TextAtOrBelowTheConfiguredLimit() {
        val content = "直播配置"
        val bytes = content.toByteArray(Charsets.UTF_8)

        assertEquals(
            content,
            ByteArrayInputStream(bytes).readUtf8TextWithinLimit(bytes.size)
        )
    }

    @Test
    fun rejectsInputThatExceedsTheConfiguredLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            ByteArrayInputStream("12345".toByteArray())
                .readUtf8TextWithinLimit(maxBytes = 4)
        }
    }

    @Test
    fun readBytesWithinLimitReadsExactLimitAndRejectsOverflow() {
        val exact = byteArrayOf(1, 2, 3, 4)
        org.junit.Assert.assertArrayEquals(
            exact,
            ByteArrayInputStream(exact).readBytesWithinLimit(maxBytes = 4)
        )
        assertThrows(IllegalArgumentException::class.java) {
            ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5))
                .readBytesWithinLimit(maxBytes = 4)
        }
    }
}
