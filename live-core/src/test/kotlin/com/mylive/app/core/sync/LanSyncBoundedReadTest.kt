package com.mylive.app.core.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class LanSyncBoundedReadTest {
    @Test
    fun readsUpToTheLimitOnAStreamThatReturnsOneByteAtATime() {
        val stream = OneByteStream("abcdefghij".toByteArray())
        assertArrayEquals("abcde".toByteArray(), stream.readAtMost(5))
        assertEquals(5, stream.remaining())
    }

    @Test
    fun readsTheWholeStreamWhenItIsShorterThanTheLimit() {
        assertArrayEquals("ok".toByteArray(), ByteArrayInputStream("ok".toByteArray()).readAtMost(64))
        assertArrayEquals(ByteArray(0), ByteArrayInputStream(ByteArray(0)).readAtMost(8))
        assertArrayEquals(ByteArray(0), ByteArrayInputStream("leftover".toByteArray()).readAtMost(0))
    }

    @Test
    fun sharedClientDoesNotCallTheApi33Read() {
        val source = File("src/main/java/com/mylive/app/core/sync/LanSyncClient.kt").readText()
        assertFalse(source.contains(".readNBytes("))
        assertTrue(source.contains(".readAtMost("))
    }

    private class OneByteStream(private val data: ByteArray) : InputStream() {
        private var index = 0
        override fun read(): Int = if (index >= data.size) -1 else data[index++].toInt() and 0xff
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (index >= data.size) return -1
            b[off] = data[index++]
            return 1
        }
        fun remaining(): Int = data.size - index
    }
}
