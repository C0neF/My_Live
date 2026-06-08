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

    private var buffer: ByteArray = data ?: ByteArray(0)
    private var position: Int = pos
    private val length: Int get() = buffer.size

    fun wrap(data: ByteArray, pos: Int = 0) {
        buffer = data
        position = pos
    }

    // ── Head reading ───────────────────────────────────────────────────────

    data class HeadData(var type: Int = 0, var tag: Int = 0)

    private fun readBinaryReaderHead(hd: HeadData): Int {
        if (position >= length) {
            throw TarsDecodeException("read file to end")
        }
        val b = buffer[position].toInt() and 0xFF
        position++
        hd.type = b and 0x0F
        hd.tag = (b and (0x0F shl 4)) shr 4
        if (hd.tag == 15) {
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
        position += len
    }

    // ── Primitive readers ──────────────────────────────────────────────────

    private fun readByteInt(len: Int): Long {
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
        val bytes = buffer.copyOfRange(position, position + len)
        position += len
        return bytes
    }

    // ── Skip logic ─────────────────────────────────────────────────────────

    fun skipToTag(tag: Int): Boolean {
        try {
            val hd = HeadData()
            while (true) {
                val len = peakHead(hd)
                if (tag <= hd.tag || hd.type == TarsStructType.STRUCT_END.ordinal) {
                    return tag == hd.tag
                }
                skip(len)
                skipFieldWithType(hd.type)
            }
        } catch (e: TarsDecodeException) {
            // Reached end
        }
        return false
    }

    private fun skipToStructEnd() {
        val hd = HeadData()
        do {
            readHead(hd)
            skipFieldWithType(hd.type)
        } while (hd.type != TarsStructType.STRUCT_END.ordinal)
    }

    private fun skipField() {
        val hd = HeadData()
        readHead(hd)
        skipFieldWithType(hd.type)
    }

    private fun skipFieldWithType(type: Int) {
        val t = TarsStructType.entries[type]
        when (t) {
            TarsStructType.BYTE -> skip(1)
            TarsStructType.SHORT -> skip(2)
            TarsStructType.INT -> skip(4)
            TarsStructType.LONG -> skip(8)
            TarsStructType.FLOAT -> skip(4)
            TarsStructType.DOUBLE -> skip(8)
            TarsStructType.STRING1 -> {
                var len = buffer[position].toInt() and 0xFF
                position++
                skip(len)
            }
            TarsStructType.STRING4 -> {
                val len = readByteInt(4).toInt()
                skip(len)
            }
            TarsStructType.MAP -> {
                val size = readInt(0, true).toInt()
                for (i in 0 until size * 2) {
                    skipField()
                }
            }
            TarsStructType.LIST -> {
                val size = readInt(0, true).toInt()
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
            val t = TarsStructType.entries[hd.type]
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
            val t = TarsStructType.entries[hd.type]
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
        val len = buffer[position].toInt() and 0xFF
        position++
        val bytes = readBytesRaw(len)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun readString4(): String {
        val len = readByteInt(4).toInt()
        if (len > TarsStruct.TARS_MAX_STRING_LENGTH || len < 0) {
            throw TarsDecodeException("string too long: $len")
        }
        val bytes = readBytesRaw(len)
        return String(bytes, StandardCharsets.UTF_8)
    }

    fun readFloat(tag: Int, isRequire: Boolean): Double {
        var n = 0.0
        if (skipToTag(tag)) {
            val hd = HeadData()
            readHead(hd)
            val t = TarsStructType.entries[hd.type]
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
            val t = TarsStructType.entries[hd.type]
            when (t) {
                TarsStructType.SIMPLE_LIST -> {
                    val hh = HeadData()
                    readHead(hh)
                    if (hh.type != TarsStructType.BYTE.ordinal) {
                        throw TarsDecodeException("type mismatch, tag: $tag,type:${hd.type},${hh.type}")
                    }
                    val size = readInt(0, true).toInt()
                    if (size < 0) {
                        throw TarsDecodeException("invalid size, tag: $tag, type: ${hd.type}, ${hh.type}  size:$size")
                    }
                    lr = readBytesRaw(size)
                }
                TarsStructType.LIST -> {
                    val size = readInt(0, true).toInt()
                    if (size < 0) throw TarsDecodeException("size invalid: $size")
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
            val t = TarsStructType.entries[hd.type]
            if (t == TarsStructType.MAP) {
                val size = readInt(0, true).toInt()
                if (size < 0) {
                    throw TarsDecodeException("size invalid:$size")
                }
                for (i in 0 until size) {
                    @Suppress("UNCHECKED_CAST")
                    val mk = readAny(proxyKey as Any, 0, true) as K
                    @Suppress("UNCHECKED_CAST")
                    val mv = readAny(proxyValue as Any, 1, true) as V
                    map[mk] = mv
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
            val t = TarsStructType.entries[hd.type]
            if (t == TarsStructType.MAP) {
                val size = readInt(0, true).toInt()
                if (size < 0) {
                    throw TarsDecodeException("size invalid:$size")
                }
                for (i in 0 until size) {
                    @Suppress("UNCHECKED_CAST")
                    val mk = readAny(proxyKey, 0, true) as K
                    @Suppress("UNCHECKED_CAST")
                    val mv = readAny(proxyValue, 1, true) as V
                    map[mk] = mv
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
            val t = TarsStructType.entries[hd.type]
            if (t == TarsStructType.MAP) {
                val size = readInt(0, true).toInt()
                if (size < 0) {
                    throw TarsDecodeException("size invalid:$size")
                }
                for (i in 0 until size) {
                    @Suppress("UNCHECKED_CAST")
                    val mk = readAny(proxyKey, 0, true) as K
                    @Suppress("UNCHECKED_CAST")
                    val mv = readMapOf<K2, V2>(proxyKey2, proxyValue2, 1, true)
                    map[mk] = mv
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
            val t = TarsStructType.entries[hd.type]
            when (t) {
                TarsStructType.LIST -> {
                    val size = readInt(0, true).toInt()
                    if (size < 0) throw TarsDecodeException("size invalid: $size")
                    for (i in 0 until size) {
                        @Suppress("UNCHECKED_CAST")
                        ls.add(readAny(proxyElement, 0, true) as T)
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
            val t = TarsStructType.entries[hd.type]
            if (t == TarsStructType.STRUCT_BEGIN) {
                val copyTs = proxy.deepCopy()
                copyTs.readFrom(this)
                skipToStructEnd()
                return copyTs
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
