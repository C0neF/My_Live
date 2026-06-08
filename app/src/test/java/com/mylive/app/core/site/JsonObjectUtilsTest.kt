package com.mylive.app.core.site

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JsonObjectUtilsTest {

    @Test
    fun optStringValueTreatsJsonNullAsMissing() {
        val json = JSONObject()
            .put("nullValue", JSONObject.NULL)
            .put("numberValue", 42)

        assertEquals("", json.optStringValue("nullValue"))
        assertEquals("", json.optStringValue("missing"))
        assertEquals("42", json.optStringValue("numberValue"))
    }

    @Test
    fun optNullableStringValueTreatsJsonNullAsNull() {
        val json = JSONObject()
            .put("nullValue", JSONObject.NULL)
            .put("booleanValue", true)

        assertEquals(null, json.optNullableStringValue("nullValue"))
        assertEquals(null, json.optNullableStringValue("missing"))
        assertEquals("true", json.optNullableStringValue("booleanValue"))
    }
}
