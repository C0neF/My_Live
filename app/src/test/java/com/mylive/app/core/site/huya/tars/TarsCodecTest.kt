package com.mylive.app.core.site.huya.tars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic round-trip tests for the hand-written TARS binary codec.
 *
 * These were previously untested (the only Huya test hit the live network with no assertions).
 * The codec backs Huya CDN-token requests, so a regression here silently breaks Huya playback.
 */
class TarsCodecTest {

    @Test
    fun roundTripsIntegersAcrossEncodingBoundaries() {
        // Values chosen to exercise every integer encoding: ZERO_TAG, BYTE, SHORT, INT, LONG.
        val values = listOf(
            0L,                  // ZERO_TAG
            127L, -128L,         // BYTE
            128L, -129L, 32767L, // SHORT
            32768L, -32769L, 2147483647L, // INT
            2147483648L, Long.MAX_VALUE, Long.MIN_VALUE // LONG
        )
        val out = TarsOutputStream()
        values.forEachIndexed { tag, v -> out.writeLong(v, tag) }

        val input = TarsInputStream(out.toByteArray())
        values.forEachIndexed { tag, v ->
            assertEquals("tag $tag", v, input.readInt(tag, true))
        }
    }

    @Test
    fun roundTripsStringsIncludingTheString4Path() {
        val long = "虎".repeat(200) // > 255 UTF-8 bytes => STRING4 encoding
        val out = TarsOutputStream().apply {
            writeString("", 0)
            writeString("hello 虎牙 live", 1)
            writeString(long, 2)
        }
        val input = TarsInputStream(out.toByteArray())
        assertEquals("", input.readString(0, true))
        assertEquals("hello 虎牙 live", input.readString(1, true))
        assertEquals(long, input.readString(2, true))
    }

    @Test
    fun roundTripsBoolFloatAndDouble() {
        val out = TarsOutputStream().apply {
            writeBool(true, 0)
            writeBool(false, 1)
            writeFloat(3.5, 2)
            writeDouble(2.718281828459045, 3)
        }
        val input = TarsInputStream(out.toByteArray())
        assertTrue(input.readBool(0, true))
        assertFalse(input.readBool(1, true))
        assertEquals(3.5, input.readFloat(2, true), 1e-6)
        assertEquals(2.718281828459045, input.readFloat(3, true), 1e-12)
    }

    @Test
    fun skipsInterveningTagsWhenReadingOutOfSequence() {
        // Reading tag 0 then tag 2 must transparently skip the tag-1 field (skipToTag).
        val out = TarsOutputStream().apply {
            writeString("first", 0)
            writeLong(999L, 1)
            writeString("third", 2)
        }
        val input = TarsInputStream(out.toByteArray())
        assertEquals("first", input.readString(0, true))
        assertEquals("third", input.readString(2, true))
    }

    @Test
    fun missingOptionalFieldReturnsDefaultWithoutThrowing() {
        val input = TarsInputStream(TarsOutputStream().apply { writeString("only", 5) }.toByteArray())
        assertEquals(0L, input.readInt(1, false))
        assertEquals("", input.readString(2, false))
    }
}
