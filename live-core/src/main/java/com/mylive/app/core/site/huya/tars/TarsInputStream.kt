package com.mylive.app.core.site.huya.tars

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * TARS binary deserializer.
 *
 * Reads tagged fields from a byte stream, supporting all TARS primitive types
 * (byte, short, int, long, float, double, string, bytes), as well as
 * compound types (struct, list, map).
 *
 * Ported from Dart tars_dart TarsInputStream.
 */
class TarsInputStream(data: ByteArray?, pos: Int = 0) {

    companion object {
        private const val MAX_NESTING_DEPTH = 64
    }

    private var buffer: ByteArray = data ?: ByteArray(0)
    private var position: Int = pos
    private var nestingDepth: Int = 0
    private val length: Int get() = buffer.size

    fun wrap(data: ByteArray, pos: Int = 0) {
        buffer = data
        position = pos
        nestingDepth = 0
    }

    // ── Head reading ───────────────────────────────────────────────────────

    data class HeadData(var type: Int = 0, var tag: Int = 0)

    private fun structTypeOf(code: Int): TarsStructType {
        return TarsStructType.entries.getOrElse(code) {
            throw TarsDecodeException("invalid struct type: $code")
        }
    }

    private inline fun <T> withNestingGuard(block: () -> T): T {
        if (nestingDepth >= MAX_NESTING_DEPTH) {
            throw TarsDecodeException("maximum nesting depth exceeded: $nestingDepth")
        }
        nestingDepth++
        return try {
            block()
        } finally {
            nestingDepth--
        }
    }

    private fun requireContainerSize(size: Int, minBytesPerElement: Int = 1) {
        val remaining = length - position
        if (
            size < 0 ||
            size > TarsStruct.TARS_MAX_CONTAINER_ELEMENTS ||
            size.toLong() * minBytesPerElement > remaining.toLong()
        ) {
            throw TarsDecodeException("size invalid: $size, remaining: $remaining")
        }
    }

    private fun requireByteLength(len: Int, maxAllowed: Int) {
        val remaining = length - position
        if (len < 0 || len > maxAllowed || len > remaining) {
            throw TarsDecodeException("byte length invalid: $len, remaining: $remaining")
        }
    }

    private fun readBinaryReaderHead(hd: HeadData): Int {
        if (position < 0 || position >= length) {
            throw TarsDecodeException("read file to end")
        }
        val b = buffer[position].toInt() and 0xFF
        position++
        hd.type = b and 0x0F
        hd.tag = (b and (0x0F shl 4)) shr 4
        if (hd.tag == 15) {
            if (position >= length) {
                throw TarsDecodeException("read file to end")
            }
            hd.tag = buffer[position].toInt() and 0xFF
            position++
            return 2
        }
        return 1
    }

    private fun readHead(hd: HeadData): Int {
        return readBinaryReaderHead(hd)
    }

    private fun peakHead(hd: HeadData): Int {
        val curPos = position
        val len = readHead(hd)
        position = curPos
        return len
    }

    private fun skip(len: Int) {
        if (len < 0 || len > length - position) {
            throw TarsDecodeException("skip out of bounds: len=$len, remaining=${length - position}")
        }
        position += len
    }

    // ── Primitive readers ──────────────────────────────────────────────────

    private fun readByteInt(len: Int): Long {
        if (len < 0 || len > length - position) {
            throw TarsDecodeException("readByteInt out of bounds: len=$len, remaining=${length - position}")
        }
        val bb = ByteBuffer.wrap(buffer, position, len)
        bb.order(ByteOrder.BIG_ENDIAN)
        val result: Long = when (len) {
            // A BYTE-typed value is signed int8: writeLong() only emits BYTE for -128..127
            // (128..255 are promoted to SHORT), so this slot must sign-extend, not mask to 0xFF.
            1 -> bb.get().toLong()
            2 -> bb.short.toLong()
            4 -> bb.int.toLong()
            8 -> bb.long
            else -> throw TarsDecodeException("Unsupported int length: $len")
        }
        position += len
        return result
    }

    private fun readByteFloat(len: Int): Double {
        if (len < 0 || len > length - position) {
            throw TarsDecodeException("readByteFloat out of bounds: len=$len, remaining=${length - position}")
        }
        val bb = ByteBuffer.wrap(buffer, position, len)
        bb.order(ByteOrder.BIG_ENDIAN)
        val result: Double = when (len) {
            4 -> bb.float.toDouble()
            8 -> bb.double
            else -> throw TarsDecodeException("Unsupported float length: $len")
        }
        position += len
        return result
    }

    private fun readBytesRaw(len: Int): ByteArray {
        if (len < 0 || len > length - position) {
            throw TarsDecodeException("readBytesRaw out of bounds: len=$len, remaining=${length - position}")
        }
        val bytes = buffer.copyOfRange(position, position + len)
        position += len
        return bytes
    }

    // ── Skip logic ─────────────────────────────────────────────────────────

    fun skipToTag(tag: Int): Boolean {
        val hd = HeadData()
        while (position < length) {
            val len = try {
                peakHead(hd)
            } catch (e: TarsDecodeException) {
                return false
            }
            if (tag <= hd.tag || hd.type == TarsStructType.STRUCT_END.ordinal) {
                return tag == hd.tag
            }
            skip(len)
            skipFieldWithType(hd.type)
        }
        return false
    }

    private fun skipToStructEnd() {
        withNestingGuard {
            val hd = HeadData()
            do {
                readHead(hd)
                skipFieldWithType(hd.type)
            } while (hd.type != TarsStructType.STRUCT_END.ordinal)
        }
    }

    private fun skipField() {
        withNestingGuard {
            val hd = HeadData()
            readHead(hd)
            skipFieldWithType(hd.type)
        }
    }

    private fun skipFieldWithType(type: Int) {
        val t = structTypeOf(type)
        when (t) {
            TarsStructType.BYTE -> skip(1)
            TarsStructType.SHORT -> skip(2)
            TarsStructType.INT -> skip(4)
            TarsStructType.LONG -> skip(8)
            TarsStructType.FLOAT -> skip(4)
            TarsStructType.DOUBLE -> skip(8)
            TarsStructType.STRING1 -> {
                if (position >= length) throw TarsDecodeException("read file to end")
                val len = buffer[position].toInt() and 0xFF
                position++
                skip(len)
            }
            TarsStructType.STRING4 -> {
                val len = readByteInt(4).toInt()
                requireByteLength(len, TarsStruct.TARS_MAX_STRING_LENGTH)
                skip(len)
            }
            TarsStructType.MAP -> {
                val size = readInt(0, true).toInt()
                requireContainerSize(size, minBytesPerElement = 2)
                for (i in 0 until size * 2) {
                    skipField()
                }
            }
            TarsStructType.LIST -> {
                val size = readInt(0, true).toInt()
                requireContainerSize(size, minBytesPerElement = 1)
                for (i in 0 until size) {
                    skipField()
                }
            }
            TarsStructType.SIMPLE_LIST -> {
                val hd = HeadData()
                readHead(hd)
                if (hd.type != TarsStructType.BYTE.ordinal) {
                    throw TarsDecodeException("skipField with invalid type, type value: $type,${hd.type}")
                }
                val size = readInt(0, true).toInt()
                requireByteLength(size, TarsStruct.TARS_MAX_BYTE_ARRAY_LENGTH)
                skip(size)
            }
            TarsStructType.STRUCT_BEGIN -> skipToStructEnd()
            TarsStructType.STRUCT_END, TarsStructType.ZERO_TAG -> { /* no-op */ }
        }
    }

    // ── High-level read methods ────────────────────────────────────────────

    fun readInt(tag: Int, isRequire: Boolean): Long {
        var n = 0L
        if (skipToTag(tag)) {
            val hd = HeadData()
            readHead(hd)
            val t = structTypeOf(hd.type)
            n = when (t) {
                TarsStructType.ZERO_TAG -> 0L
                TarsStructType.BYTE -> readByteInt(1)
                TarsStructType.SHORT -> readByteInt(2)
                TarsStructType.INT -> readByteInt(4)
                TarsStructType.LONG -> readByteInt(8)
                else -> throw TarsDecodeException("type mismatch.")
            }
        } else if (isRequire) {
            throw TarsDecodeException("require field not exist.")
        }
        return n
    }

    fun readBool(tag: Int, isRequire: Boolean): Boolean {
        return readInt(tag, isRequire) != 0L
    }

    fun readString(tag: Int, isRequire: Boolean): String {
        var n = ""
        if (skipToTag(tag)) {
            val hd = HeadData()
            readHead(hd)
            val t = structTypeOf(hd.type)
            n = when (t) {
                TarsStructType.STRING1 -> readString1()
                TarsStructType.STRING4 -> readString4()
                else -> throw TarsDecodeException("type mismatch.")
            }
        } else if (isRequire) {
            throw TarsDecodeException("require field not exist.")
        }
        return n
    }

    private fun readString1(): String {
        if (position >= length) throw TarsDecodeException("read file to end")
        val len = buffer[position].toInt() and 0xFF
        position++
        val bytes = readBytesRaw(len)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun readString4(): String {
        val len = readByteInt(4).toInt()
        requireByteLength(len, TarsStruct.TARS_MAX_STRING_LENGTH)
        val bytes = readBytesRaw(len)
        return String(bytes, StandardCharsets.UTF_8)
    }

    fun readFloat(tag: Int, isRequire: Boolean): Double {
        var n = 0.0
        if (skipToTag(tag)) {
            val hd = HeadData()
            readHead(hd)
            val t = structTypeOf(hd.type)
            n = when (t) {
                TarsStructType.ZERO_TAG -> 0.0
                TarsStructType.FLOAT -> readByteFloat(4)
                TarsStructType.DOUBLE -> readByteFloat(8)
                else -> throw TarsDecodeException("type mismatch.")
            }
        } else if (isRequire) {
            throw TarsDecodeException("require field not exist.")
        }
        return n
    }

    fun readBytes(tag: Int, isRequire: Boolean): ByteArray {
        var lr = ByteArray(0)
        if (skipToTag(tag)) {
            val hd = HeadData()
            readHead(hd)
            val t = structTypeOf(hd.type)
            when (t) {
                TarsStructType.SIMPLE_LIST -> {
                    val hh = HeadData()
                    readHead(hh)
                    if (hh.type != TarsStructType.BYTE.ordinal) {
                        throw TarsDecodeException("type mismatch, tag: $tag,type:${hd.type},${hh.type}")
                    }
                    val size = readInt(0, true).toInt()
                    requireByteLength(size, TarsStruct.TARS_MAX_BYTE_ARRAY_LENGTH)
                    lr = readBytesRaw(size)
                }
                TarsStructType.LIST -> {
                    val size = readInt(0, true).toInt()
                    requireByteLength(size, TarsStruct.TARS_MAX_BYTE_ARRAY_LENGTH)
                    lr = ByteArray(size)
                    for (i in 0 until size) {
                        lr[i] = readInt(0, true).toByte()
                    }
                }
                else -> throw TarsDecodeException("type mismatch.")
            }
        } else if (isRequire) {
            throw TarsDecodeException("require field not exist.")
        }
        return lr
    }

    fun <K, V> readMap(proxyKey: K, proxyValue: V, tag: Int, isRequire: Boolean): Map<K, V> {
        val map = mutableMapOf<K, V>()
        if (skipToTag(tag)) {
            val hd = HeadData()
            readHead(hd)
            val t = structTypeOf(hd.type)
            if (t == TarsStructType.MAP) {
                withNestingGuard {
                    val size = readInt(0, true).toInt()
                    requireContainerSize(size, minBytesPerElement = 2)
                    for (i in 0 until size) {
                        @Suppress("UNCHECKED_CAST")
                        val mk = readAny(proxyKey as Any, 0, true) as K
                        @Suppress("UNCHECKED_CAST")
                        val mv = readAny(proxyValue as Any, 1, true) as V
                        map[mk] = mv
                    }
                }
            } else {
                throw TarsDecodeException("type mismatch.")
            }
        } else if (isRequire) {
            throw TarsDecodeException("require field not exist.")
        }
        return map
    }

    fun <K, V> readMapOf(proxyKey: Any, proxyValue: Any, tag: Int, isRequire: Boolean): Map<K, V> {
        val map = mutableMapOf<K, V>()
        if (skipToTag(tag)) {
            val hd = HeadData()
            readHead(hd)
            val t = structTypeOf(hd.type)
            if (t == TarsStructType.MAP) {
                withNestingGuard {
                    val size = readInt(0, true).toInt()
                    requireContainerSize(size, minBytesPerElement = 2)
                    for (i in 0 until size) {
                        @Suppress("UNCHECKED_CAST")
                        val mk = readAny(proxyKey, 0, true) as K
                        @Suppress("UNCHECKED_CAST")
                        val mv = readAny(proxyValue, 1, true) as V
                        map[mk] = mv
                    }
                }
            } else {
                throw TarsDecodeException("type mismatch.")
            }
        } else if (isRequire) {
            throw TarsDecodeException("require field not exist.")
        }
        return map
    }

    fun <K, K2, V2> readMapMap(
        proxyKey: Any, proxyKey2: Any, proxyValue2: Any,
        tag: Int, isRequire: Boolean
    ): Map<K, Map<K2, V2>> {
        val map = mutableMapOf<K, Map<K2, V2>>()
        if (skipToTag(tag)) {
            val hd = HeadData()
            readHead(hd)
            val t = structTypeOf(hd.type)
            if (t == TarsStructType.MAP) {
                withNestingGuard {
                    val size = readInt(0, true).toInt()
                    requireContainerSize(size, minBytesPerElement = 2)
                    for (i in 0 until size) {
                        @Suppress("UNCHECKED_CAST")
                        val mk = readAny(proxyKey, 0, true) as K
                        @Suppress("UNCHECKED_CAST")
                        val mv = readMapOf<K2, V2>(proxyKey2, proxyValue2, 1, true)
                        map[mk] = mv
                    }
                }
            } else {
                throw TarsDecodeException("type mismatch.")
            }
        } else if (isRequire) {
            throw TarsDecodeException("require field not exist.")
        }
        return map
    }

    fun <T> readList(proxyElement: Any, tag: Int, isRequire: Boolean): List<T> {
        val ls = mutableListOf<T>()
        if (skipToTag(tag)) {
            val hd = HeadData()
            readHead(hd)
            val t = structTypeOf(hd.type)
            when (t) {
                TarsStructType.LIST -> {
                    withNestingGuard {
                        val size = readInt(0, true).toInt()
                        requireContainerSize(size, minBytesPerElement = 1)
                        for (i in 0 until size) {
                            @Suppress("UNCHECKED_CAST")
                            ls.add(readAny(proxyElement, 0, true) as T)
                        }
                    }
                }
                else -> throw TarsDecodeException("type mismatch.")
            }
        } else if (isRequire) {
            throw TarsDecodeException("require field not exist.")
        }
        return ls
    }

    fun readTarsStruct(proxy: TarsStruct, tag: Int, isRequire: Boolean): TarsStruct {
        if (skipToTag(tag)) {
            val hd = HeadData()
            readHead(hd)
            val t = structTypeOf(hd.type)
            if (t == TarsStructType.STRUCT_BEGIN) {
                return withNestingGuard {
                    val copyTs = proxy.deepCopy()
                    copyTs.readFrom(this)
                    skipToStructEnd()
                    copyTs
                }
            } else {
                throw TarsDecodeException("type mismatch.")
            }
        } else if (isRequire) {
            throw TarsDecodeException("require field not exist.")
        }
        return proxy
    }

    /**
     * Generic read dispatch based on proxy object type.
     * This mirrors the Dart `read<T>(dynamic data, int tag, bool isRequire)` method.
     */
    fun readAny(data: Any, tag: Int, isRequire: Boolean): Any {
        return when (data) {
            is Byte -> readInt(tag, isRequire).toByte()
            is Short -> readInt(tag, isRequire).toShort()
            is Int -> readInt(tag, isRequire).toInt()
            is Long -> readInt(tag, isRequire)
            is Float -> readFloat(tag, isRequire).toFloat()
            is Double -> readFloat(tag, isRequire)
            is Boolean -> readBool(tag, isRequire)
            is ByteArray -> readBytes(tag, isRequire)
            is String -> readString(tag, isRequire)
            is TarsStruct -> readTarsStruct(data, tag, isRequire)
            else -> {
                // Check if it's a "type" reference (e.g., Int::class.javaObjectType)
                when (data) {
                    Int::class, Int::class.javaPrimitiveType, Int::class.javaObjectType ->
                        readInt(tag, isRequire).toInt()
                    Long::class, Long::class.javaPrimitiveType, Long::class.javaObjectType ->
                        readInt(tag, isRequire)
                    String::class, String::class.java ->
                        readString(tag, isRequire)
                    Boolean::class, Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType ->
                        readBool(tag, isRequire)
                    Double::class, Double::class.javaPrimitiveType, Double::class.javaObjectType ->
                        readFloat(tag, isRequire)
                    ByteArray::class -> readBytes(tag, isRequire)
                    else -> throw TarsDecodeException("type:${data::class} not supported.")
                }
            }
        }
    }
}

class TarsDecodeException(message: String) : Exception(message)
