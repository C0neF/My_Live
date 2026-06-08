package com.mylive.app.core.common

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary writer that writes primitive types into a dynamically growing byte list.
 * Uses java.nio.ByteBuffer for efficient binary operations.
 * Supports both big-endian and little-endian byte order
 * (BiliBili uses big-endian, Douyu uses little-endian).
 *
 * @param initialCapacity The initial capacity of the internal buffer
 * @param order The byte order (default: big-endian)
 */
class BinaryWriter(
    initialCapacity: Int = 64,
    private val order: ByteOrder = ByteOrder.BIG_ENDIAN
) {
    private val buffer = ArrayList<Byte>(initialCapacity)
    var position: Int = 0
        private set

    val length: Int get() = buffer.size

    /**
     * Get the accumulated bytes as a ByteArray.
     */
    fun toByteArray(): ByteArray = buffer.toByteArray()

    /**
     * Append raw bytes to the buffer.
     *
     * @param data The bytes to append
     */
    fun writeBytes(data: ByteArray) {
        for (b in data) {
            buffer.add(b)
        }
        position += data.size
    }

    /**
     * Write a signed integer of the specified byte length.
     * Supported lengths: 1 (uint8), 2 (int16), 4 (int32), 8 (int64).
     *
     * @param value The integer value to write
     * @param len Number of bytes to write
     */
    fun writeInt(value: Long, len: Int) {
        val bb = ByteBuffer.allocate(len)
        bb.order(order)
        when (len) {
            1 -> bb.put(value.toByte())
            2 -> bb.putShort(value.toShort())
            4 -> bb.putInt(value.toInt())
            8 -> bb.putLong(value)
            else -> throw IllegalArgumentException("Unsupported int length: $len")
        }
        writeBytes(bb.array())
    }

    /**
     * Write a floating-point value of the specified byte length.
     * Supported lengths: 4 (float32), 8 (float64).
     *
     * @param value The floating-point value to write
     * @param len Number of bytes to write (4 or 8)
     */
    fun writeDouble(value: Double, len: Int) {
        val bb = ByteBuffer.allocate(len)
        bb.order(order)
        when (len) {
            4 -> bb.putFloat(value.toFloat())
            8 -> bb.putDouble(value)
            else -> throw IllegalArgumentException("Unsupported float length: $len")
        }
        writeBytes(bb.array())
    }

    /**
     * Clear the buffer and reset position.
     */
    fun clear() {
        buffer.clear()
        position = 0
    }
}
