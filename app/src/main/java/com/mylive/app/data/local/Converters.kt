package com.mylive.app.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return runCatching {
            gson.fromJson<List<String>?>(value, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun toStringList(list: List<String>?): String {
        return gson.toJson(list ?: emptyList<String>())
    }
}
