package com.mylive.app.core.site.huya.tars

/**
 * TARS struct type enum, matching the wire-format type codes.
 */
enum class TarsStructType {
    BYTE,        // 0
    SHORT,       // 1
    INT,         // 2
    LONG,        // 3
    FLOAT,       // 4
    DOUBLE,      // 5
    STRING1,     // 6
    STRING4,     // 7
    MAP,         // 8
    LIST,        // 9
    STRUCT_BEGIN, // 10
    STRUCT_END,   // 11
    ZERO_TAG,     // 12
    SIMPLE_LIST;  // 13
}

/**
 * Base class for all TARS serializable structs.
 *
 * Subclasses must implement [readFrom] and [writeTo] for binary (de)serialization,
 * and [deepCopy] for creating independent copies.
 *
 * Ported from Dart tars_dart TarsStruct.
 */
abstract class TarsStruct {

    companion object {
        const val TARS_MAX_STRING_LENGTH = 100 * 1024 * 1024
    }

    abstract fun writeTo(os: TarsOutputStream)

    abstract fun readFrom(`is`: TarsInputStream)

    abstract fun deepCopy(): TarsStruct

    fun toByteArray(): ByteArray {
        val os = TarsOutputStream()
        writeTo(os)
        return os.toByteArray()
    }
}
