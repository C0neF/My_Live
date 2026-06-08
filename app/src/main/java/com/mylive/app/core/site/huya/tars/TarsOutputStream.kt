package com.mylive.app.core.site.huya.tars

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * TARS binary serializer.
 *
 * Writes tagged fields into a byte stream with proper type encoding.
 * Automatically selects the smallest encoding for integers (byte/short/int/long).
 *
 * Ported from Dart tars_dart TarsOutputStream.
 */
class TarsOutputStream(initialData: ByteArray? = null) {

    private val buffer = ArrayList<Byte>(initialData?.size ?: 64)

    init {
        initialData?.forEach { buffer.add(it) }
    }

    // ── Binary writer helpers ──────────────────────────────────────────────

    private fun writeBytesRaw(data: ByteArray) {
        for (b in data) {
            buffer.add(b)
        }
    }

    private fun writeIntRaw(value: Long, len: Int) {
        val bb = ByteBuffer.allocate(len)
        bb.order(ByteOrder.BIG_ENDIAN)
        when (len) {
            1 -> bb.put(value.toByte())
            2 -> bb.putShort(value.toShort())
            4 -> bb.putInt(value.toInt())
            8 -> bb.putLong(value)
            else -> throw TarsEncodeException("Unsupported int length: $len")
        }
        writeBytesRaw(bb.array())
    }

    private fun writeDoubleRaw(value: Double, len: Int) {
        val bb = ByteBuffer.allocate(len)
        bb.order(ByteOrder.BIG_ENDIAN)
        when (len) {
            4 -> bb.putFloat(value.toFloat())
            8 -> bb.putDouble(value)
            else -> throw TarsEncodeException("Unsupported float length: $len")
        }
        writeBytesRaw(bb.array())
    }

    // ── Head writing ───────────────────────────────────────────────────────

    fun writeHead(type: Int, tag: Int) {
        if (tag < 15) {
            val b = (tag shl 4) or type
            writeIntRaw(b.toLong(), 1)
        } else if (tag < 256) {
            val b = (15 shl 4) or type
            writeIntRaw(b.toLong(), 1)
            writeIntRaw(tag.toLong(), 1)
        } else {
            throw TarsEncodeException("tag is too large: $tag")
        }
    }

    // ── High-level write methods ───────────────────────────────────────────

    /**
     * Generic write dispatch. Mirrors the Dart `write(dynamic data, int tag)` method.
     */
    fun write(data: Any?, tag: Int) {
        when (data) {
            is Byte -> writeByte(data.toInt(), tag)
            is Short -> writeInt(data.toInt(), tag)
            is Int -> writeInt(data, tag)
            is Long -> writeLong(data, tag)
            is Float -> writeFloat(data.toDouble(), tag)
            is Double -> writeDouble(data, tag)
            is Boolean -> writeBool(data, tag)
            is ByteArray -> writeUint8List(data, tag)
            is String -> writeString(data, tag)
            is TarsStruct -> writeTarsStruct(data, tag)
            is List<*> -> writeList(data, tag)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                writeMap(data as Map<Any, Any?>, tag)
            }
            null -> { /* skip null */ }
            else -> throw TarsEncodeException("type:${data::class} not supported.")
        }
    }

    fun writeBool(b: Boolean, tag: Int) {
        writeByte(if (b) 1 else 0, tag)
    }

    fun writeByte(b: Int, tag: Int) {
        if (b == 0) {
            writeHead(TarsStructType.ZERO_TAG.ordinal, tag)
        } else {
            writeHead(TarsStructType.BYTE.ordinal, tag)
            writeIntRaw(b.toLong(), 1)
        }
    }

    fun writeInt(n: Int, tag: Int) {
        writeLong(n.toLong(), tag)
    }

    fun writeLong(n: Long, tag: Int) {
        if (n in -128..127) {
            writeByte(n.toInt(), tag)
            return
        }
        if (n in -32768..32767) {
            writeHead(TarsStructType.SHORT.ordinal, tag)
            writeIntRaw(n, 2)
            return
        }
        if (n in -2147483648..2147483647) {
            writeHead(TarsStructType.INT.ordinal, tag)
            writeIntRaw(n, 4)
            return
        }
        writeHead(TarsStructType.LONG.ordinal, tag)
        writeIntRaw(n, 8)
    }

    fun writeFloat(n: Double, tag: Int) {
        writeHead(TarsStructType.FLOAT.ordinal, tag)
        writeDoubleRaw(n, 4)
    }

    fun writeDouble(n: Double, tag: Int) {
        writeHead(TarsStructType.DOUBLE.ordinal, tag)
        writeDoubleRaw(n, 8)
    }

    fun writeString(s: String, tag: Int) {
        val bytes = s.toByteArray(StandardCharsets.UTF_8)
        if (bytes.isEmpty()) {
            writeHead(TarsStructType.STRING1.ordinal, tag)
            writeIntRaw(0, 1)
            return
        }
        if (bytes.size > 255) {
            writeHead(TarsStructType.STRING4.ordinal, tag)
            writeIntRaw(bytes.size.toLong(), 4)
            writeBytesRaw(bytes)
        } else {
            writeHead(TarsStructType.STRING1.ordinal, tag)
            writeIntRaw(bytes.size.toLong(), 1)
            writeBytesRaw(bytes)
        }
    }

    /**
     * Write a byte array as a SIMPLE_LIST.
     */
    fun writeUint8List(ls: ByteArray, tag: Int) {
        writeHead(TarsStructType.SIMPLE_LIST.ordinal, tag)
        writeHead(TarsStructType.BYTE.ordinal, 0)
        writeInt(ls.size, 0)
        writeBytesRaw(ls)
    }

    fun writeMap(map: Map<Any, Any?>, tag: Int) {
        writeHead(TarsStructType.MAP.ordinal, tag)
        writeInt(map.size, 0)
        for ((key, value) in map) {
            write(key, 0)
            write(value, 1)
        }
    }

    fun writeList(ls: List<*>, tag: Int) {
        writeHead(TarsStructType.LIST.ordinal, tag)
        writeInt(ls.size, 0)
        for (item in ls) {
            write(item, 0)
        }
    }

    fun writeTarsStruct(o: TarsStruct, tag: Int) {
        writeHead(TarsStructType.STRUCT_BEGIN.ordinal, tag)
        o.writeTo(this)
        writeHead(TarsStructType.STRUCT_END.ordinal, 0)
    }

    fun toByteArray(): ByteArray {
        return buffer.toByteArray()
    }
}

class TarsEncodeException(message: String) : Exception(message)
