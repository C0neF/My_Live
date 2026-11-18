package com.mylive.app.core.site.bilibili

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.DeflaterOutputStream

class BiliBiliDanmakuDecompressionTest {

    private fun zlibCompress(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out).use { it.write(input) }
        return out.toByteArray()
    }

    @Test
    fun decompressesZlibBodyWithinLimit() {
        val original = """{"cmd":"DANMU_MSG","info":[[0,1,25,16777215],"你好",[1,"user"]]}"""
            .toByteArray(Charsets.UTF_8)
        val compressed = zlibCompress(original)

        val actual = decompressBiliBiliDanmakuBody(
            protocolVersion = 2,
            body = compressed,
            maxBytes = 1024
        )

        assertArrayEquals(original, actual)
    }

    @Test
    fun rejectsZlibBombExceedingLimit() {
        val oversizedZeros = ByteArray(64 * 1024)
        val compressed = zlibCompress(oversizedZeros)

        assertThrows(IllegalArgumentException::class.java) {
            decompressBiliBiliDanmakuBody(
                protocolVersion = 2,
                body = compressed,
                maxBytes = 4096
            )
        }
    }

    @Test
    fun rejectsUncompressedBodyExceedingLimit() {
        val raw = ByteArray(1025)
        assertThrows(IllegalArgumentException::class.java) {
            decompressBiliBiliDanmakuBody(
                protocolVersion = 0,
                body = raw,
                maxBytes = 1024
            )
        }
    }

    @Test
    fun extractsConcatenatedSubPacketsEvenWhenPacketLengthContainsBraceByte() {
        val json1 = """{"cmd":"DANMU_MSG","info":[[0,1,25,16777215],"first",[1,"u1"]]}"""
        // Pad second JSON so total inner packet length == 123 (0x0000007B, low byte '{').
        val base2 = """{"cmd":"DANMU_MSG","info":[[0,1,25,16777215],"second",[1,"u2"]]}"""
        val paddingNeeded = 123 - 16 - base2.toByteArray(Charsets.UTF_8).size
        val json2 = base2.dropLast(1) + ""","pad":"${"x".repeat(paddingNeeded - 9)}"}"""
        val sub1 = buildInnerPacket(json1)
        val sub2 = buildInnerPacket(json2)
        assertEquals(123, sub2.size)

        val decompressed = decompressBiliBiliDanmakuBody(
            protocolVersion = 2,
            body = zlibCompress(sub1 + sub2),
            maxBytes = 4096
        )
        val extracted = extractBiliBiliNotificationJsons(2, decompressed)

        assertEquals(listOf(json1, json2), extracted)
    }

    private fun buildInnerPacket(json: String): ByteArray {
        val body = json.toByteArray(Charsets.UTF_8)
        val packetLen = 16 + body.size
        return ByteBuffer.allocate(packetLen).order(ByteOrder.BIG_ENDIAN)
            .putInt(packetLen)
            .putShort(16)
            .putShort(0)
            .putInt(5)
            .putInt(1)
            .put(body)
            .array()
    }
}
