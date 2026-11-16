package com.mylive.app.core.site.huya.tars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic round-trip and malformed-input bounds tests for the TARS binary codec.
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

    @Test
    fun rejectsOversizedSimpleListWithoutZeroPaddingAllocation() {
        // Tag 0 SIMPLE_LIST (13 = 0x0D), element head BYTE (0x00), size tag 0 INT (0x02) = 50_000_000
        val crafted = byteArrayOf(
            0x0D,
            0x00,
            0x02,
            0x02, 0xFA.toByte(), 0xF0.toByte(), 0x80.toByte()
        )
        assertThrows(TarsDecodeException::class.java) {
            TarsInputStream(crafted).readBytes(0, true)
        }
    }

    @Test
    fun rejectsOversizedListAndMapHeadersExceedingRemainingBytes() {
        // Tag 0 LIST (9 = 0x09), size tag 0 INT (0x02) = 100_000 with no elements present
        val craftedList = byteArrayOf(0x09, 0x02, 0x00, 0x01, 0x86.toByte(), 0xA0.toByte())
        assertThrows(TarsDecodeException::class.java) {
            TarsInputStream(craftedList).readList<String>("", 0, true)
        }

        // Tag 0 MAP (8 = 0x08), size tag 0 INT (0x02) = 100_000 with no entries present
        val craftedMap = byteArrayOf(0x08, 0x02, 0x00, 0x01, 0x86.toByte(), 0xA0.toByte())
        assertThrows(TarsDecodeException::class.java) {
            TarsInputStream(craftedMap).readMap("", "", 0, true)
        }
    }

    @Test
    fun rejectsExcessiveStructNestingAndInvalidTypeCodes() {
        // 80 nested STRUCT_BEGIN headers at tag 0 (0x0A) followed by tag 1 INT — must fail nesting guard
        val deeplyNested = ByteArray(80) { 0x0A }
        assertThrows(TarsDecodeException::class.java) {
            TarsInputStream(deeplyNested).readInt(1, true)
        }

        // Invalid struct type nibble 15 (0x0F)
        assertThrows(TarsDecodeException::class.java) {
            TarsInputStream(byteArrayOf(0x0F)).readInt(0, true)
        }
    }
}
