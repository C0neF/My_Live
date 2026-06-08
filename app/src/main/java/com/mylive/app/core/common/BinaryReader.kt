package com.mylive.app.core.common

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary reader that reads primitive types from a byte array.
 * Uses java.nio.ByteBuffer for efficient binary operations.
 * Supports both big-endian and little-endian byte order
 * (BiliBili uses big-endian, Douyu uses little-endian).
 *
 * @param buffer The source byte array
 * @param order The byte order (default: big-endian)
 */
class BinaryReader(
    private val buffer: ByteArray,
    private val order: ByteOrder = ByteOrder.BIG_ENDIAN
) {
    var position: Int = 0
    val length: Int get() = buffer.size

    /**
     * Read a single byte from the current position and advance by 1.
     * @return The next byte as an unsigned value (0-255)
     */
    fun read(): Int {
        checkBounds(1)
        val byte = buffer[position].toInt() and 0xFF
        position += 1
        return byte
    }

    /**
     * Read a signed integer of the specified byte length from the current position.
     * Supported lengths: 1 (uint8), 2 (int16), 4 (int32), 8 (int64).
     *
     * @param len Number of bytes to read
     * @return The integer value
     */
    fun readInt(len: Int): Long {
        checkBounds(len)
        val bb = ByteBuffer.wrap(buffer, position, len)
        bb.order(order)
        val result: Long = when (len) {
            1 -> (bb.get().toInt() and 0xFF).toLong()
            2 -> bb.short.toLong()
            4 -> bb.int.toLong()
            8 -> bb.long
            else -> throw IllegalArgumentException("Unsupported int length: $len")
        }
        position += len
        return result
    }

    /**
     * Read a single byte (uint8). Equivalent to readInt(1).
     */
    fun readByte(): Int = readInt(1).toInt()

    /**
     * Read a 16-bit signed short. Equivalent to readInt(2).
     */
    fun readShort(): Int = readInt(2).toInt()

    /**
     * Read a 32-bit signed integer. Equivalent to readInt(4).
     */
    fun readInt32(): Int = readInt(4).toInt()

    /**
     * Read a 64-bit signed long. Equivalent to readInt(8).
     */
    fun readLong(): Long = readInt(8)

    /**
     * Read a byte array of the specified length from the current position.
     *
     * @param len Number of bytes to read
     * @return A copy of the bytes
     */
    fun readBytes(len: Int): ByteArray {
        checkBounds(len)
        val bytes = buffer.copyOfRange(position, position + len)
        position += len
        return bytes
    }

    /**
     * Read a floating-point value of the specified byte length.
     * Supported lengths: 4 (float), 8 (double).
     *
     * @param len Number of bytes to read (4 or 8)
     * @return The floating-point value
     */
    fun readFloat(len: Int): Double {
        checkBounds(len)
        val bb = ByteBuffer.wrap(buffer, position, len)
        bb.order(order)
        val result: Double = when (len) {
            4 -> bb.float.toDouble()
            8 -> bb.double
            else -> throw IllegalArgumentException("Unsupported float length: $len")
        }
        position += len
        return result
    }

    private fun checkBounds(len: Int) {
        if (position + len > buffer.size) {
            throw CoreError(
                "BinaryReader: buffer underflow at position $position, " +
                    "need $len bytes, have ${buffer.size - position}"
            )
        }
    }
}
