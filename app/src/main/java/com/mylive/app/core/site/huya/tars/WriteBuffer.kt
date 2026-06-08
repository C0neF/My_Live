package com.mylive.app.core.site.huya.tars

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Write-only buffer for incrementally building a byte array.
 *
 * Used by UniPacket to construct the final encoded payload with a 4-byte
 * big-endian size prefix.
 *
 * Ported from Dart tars_dart WriteBuffer.
 */
class WriteBuffer(startCapacity: Int = 8) {

    private var buffer = ByteArray(startCapacity)
    private var currentSize = 0
    private var isDone = false

    private fun resize(requiredLength: Int = 0) {
        val doubleLength = buffer.size * 2
        val newLength = maxOf(requiredLength, doubleLength)
        val newBuffer = ByteArray(newLength)
        buffer.copyInto(newBuffer, 0, 0, currentSize)
        buffer = newBuffer
    }

    fun putInt32(value: Int, bigEndian: Boolean = true) {
        check(!isDone)
        val bb = ByteBuffer.allocate(4)
        bb.order(if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
        bb.putInt(value)
        append(bb.array())
    }

    fun putUint8List(data: ByteArray) {
        check(!isDone)
        append(data)
    }

    private fun append(data: ByteArray) {
        val newSize = currentSize + data.size
        if (newSize >= buffer.size) {
            resize(newSize)
        }
        data.copyInto(buffer, currentSize)
        currentSize += data.size
    }

    /**
     * Finalize and return the written bytes.
     * Must only be called once.
     */
    fun done(): ByteArray {
        check(!isDone) { "done() must not be called more than once" }
        val result = buffer.copyOfRange(0, currentSize)
        buffer = ByteArray(0)
        isDone = true
        return result
    }
}
