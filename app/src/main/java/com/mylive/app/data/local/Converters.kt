package com.mylive.app.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {

    private val stringList = ListSerializer(String.serializer())

    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return runCatching {
            Json.decodeFromString(stringList, value)
        }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun toStringList(list: List<String>?): String {
        return Json.encodeToString(stringList, list ?: emptyList())
    }
}
