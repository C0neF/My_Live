package com.mylive.app.core.site

import org.json.JSONObject

internal fun JSONObject.optStringValue(key: String, defaultValue: String = ""): String {
    val value = opt(key)
    return if (value == null || value == JSONObject.NULL) defaultValue else value.toString()
}

internal fun JSONObject.optNullableStringValue(key: String): String? {
    val value = opt(key)
    return if (value == null || value == JSONObject.NULL) null else value.toString()
}
