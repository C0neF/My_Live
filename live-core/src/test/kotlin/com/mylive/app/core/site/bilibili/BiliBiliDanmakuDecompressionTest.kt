package com.mylive.app.core.site.bilibili

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
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
}
