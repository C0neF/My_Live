package com.mylive.app.core.site.huya.tars

/**
 * TUP attribute container.
 *
 * Stores named parameters as serialized byte arrays, supporting both
 * TUP2 (old, nested map-of-maps) and TUP3 (new, flat map) formats.
 *
 * Ported from Dart tars_dart UniAttribute.
 */
open class UniAttribute : TarsStruct() {

    companion object {
        const val PACKET_TYPE_TUP = 2
        const val PACKET_TYPE_TUP3 = 3
    }

    /** TUP3 data: name -> serialized bytes */
    var newData: MutableMap<String, ByteArray> = mutableMapOf()

    /** TUP2 data: name -> (className -> serialized bytes) */
    var oldData: MutableMap<String, Map<String, ByteArray>> = mutableMapOf()

    /** Cache for decoded objects to avoid re-parsing */
    val cachedData: MutableMap<String, Any> = mutableMapOf()

    var version: Int = PACKET_TYPE_TUP3
    var encodeName: String = "UTF-8"

    private val inputStream = TarsInputStream(null)

    fun clearCacheData() {
        cachedData.clear()
    }

    fun isEmpty(): Boolean {
        return if (version == PACKET_TYPE_TUP3) newData.isEmpty() else oldData.isEmpty()
    }

    val length: Int
        get() = if (version == PACKET_TYPE_TUP3) newData.size else oldData.size

    fun containsKey(key: String): Boolean {
        return if (version == PACKET_TYPE_TUP3) newData.containsKey(key)
        else oldData.containsKey(key)
    }

    /**
     * Put a value by name. The value is serialized immediately.
     */
    fun <T> put(name: String, t: T) {
        require(name.isNotEmpty()) { "put key can not be null" }
        requireNotNull(t) { "put value can not be null" }

        val os = TarsOutputStream()
        os.write(t as Any, 0)
        val sBuffer = os.toByteArray()

        if (version == PACKET_TYPE_TUP3) {
            cachedData.remove(name)
            newData[name] = sBuffer
        } else {
            // TUP2 format - we don't fully support encoding in TUP2 for this use case
            cachedData.remove(name)
            val pair = mapOf(t!!::class.java.simpleName to sBuffer)
            oldData[name] = pair
        }
    }

    /**
     * Get a value by name, using [proxy] as a type template for deserialization.
     * Returns [proxy] unchanged if the key is not found.
     */
    fun <T> getByClass(name: String, proxy: T): T {
        if (version == PACKET_TYPE_TUP3) {
            if (!newData.containsKey(name)) {
                @Suppress("UNCHECKED_CAST")
                return proxy as T
            }
            if (cachedData.containsKey(name)) {
                @Suppress("UNCHECKED_CAST")
                return cachedData[name] as T
            }
            try {
                val data = newData[name]!!
                val o = decodeData(data, proxy as Any)
                saveDataCache(name, o)
                @Suppress("UNCHECKED_CAST")
                return o as T
            } catch (ex: Exception) {
                throw RuntimeException("ObjectCreateException: ${ex.message}")
            }
        } else {
            return get2(name, proxy)
        }
    }

    private fun <T> get2(name: String, proxy: T): T {
        if (version == PACKET_TYPE_TUP3) {
            throw IllegalStateException("data is not in tup2 format")
        }
        if (cachedData.containsKey(name)) {
            @Suppress("UNCHECKED_CAST")
            return cachedData[name] as T
        }
        if (!oldData.containsKey(name)) {
            @Suppress("UNCHECKED_CAST")
            return null as T
        }
        val data = oldData[name]!!
        val sBuffer = data.values.first()
        val o = decodeData(sBuffer, proxy as Any)
        saveDataCache(name, o)
        @Suppress("UNCHECKED_CAST")
        return o as T
    }

    /**
     * Get a value, compatible with both TUP2 and TUP3 formats.
     */
    fun <T> get(name: String, defaultObj: T): T {
        return try {
            val result: Any? = if (version == PACKET_TYPE_TUP3) {
                getByClass(name, defaultObj)
            } else {
                get2(name, defaultObj)
            }
            if (result == null) defaultObj
            else {
                @Suppress("UNCHECKED_CAST")
                result as T
            }
        } catch (ex: Exception) {
            defaultObj
        }
    }

    private fun decodeData(data: ByteArray, proxy: Any): Any {
        inputStream.wrap(data)
        return inputStream.readAny(proxy, 0, true)
    }

    private fun saveDataCache(name: String, o: Any) {
        cachedData[name] = o
    }

    open fun encode(): ByteArray {
        val os = TarsOutputStream()
        if (version == PACKET_TYPE_TUP3) {
            os.write(newData, 0)
        } else {
            os.write(oldData, 0)
        }
        return os.toByteArray()
    }

    open fun decode(buffer: ByteArray, index: Int = 0) {
        // Try TUP2 first
        try {
            inputStream.wrap(buffer, pos = index)
            version = PACKET_TYPE_TUP
            oldData = inputStream.readMapMap<String, String, ByteArray>(
                "", "", byteArrayOf(0), 0, false
            ).toMutableMap()
        } catch (ex: Exception) {
            // Fall back to TUP3
            version = PACKET_TYPE_TUP3
            inputStream.wrap(buffer, pos = index)
            newData = inputStream.readMapOf<String, ByteArray>(
                "", byteArrayOf(0), 0, false
            ).toMutableMap()
        }
    }

    override fun writeTo(os: TarsOutputStream) {
        if (version == PACKET_TYPE_TUP3) {
            os.write(newData, 0)
        } else {
            os.write(oldData, 0)
        }
    }

    override fun readFrom(`is`: TarsInputStream) {
        if (version == PACKET_TYPE_TUP3) {
            newData = `is`.readMapOf<String, ByteArray>(
                "", byteArrayOf(0), 0, false
            ).toMutableMap()
        } else {
            oldData = `is`.readMapMap<String, String, ByteArray>(
                "", "", byteArrayOf(0), 0, false
            ).toMutableMap()
        }
    }

    override fun deepCopy(): TarsStruct {
        throw UnsupportedOperationException()
    }

    /**
     * Encode helper that mirrors the Dart encode() method for ByteArray return.
     */
    fun encodeBytes(): ByteArray {
        val os = TarsOutputStream()
        if (version == PACKET_TYPE_TUP3) {
            os.write(newData, 0)
        } else {
            os.write(oldData, 0)
        }
        return os.toByteArray()
    }
}
