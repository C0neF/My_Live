package com.mylive.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun fromStringListReturnsEmptyListForMalformedJson() {
        assertEquals(emptyList<String>(), converters.fromStringList("not-json"))
    }

    @Test
    fun fromStringListReturnsEmptyListForJsonNull() {
        assertEquals(emptyList<String>(), converters.fromStringList("null"))
    }
}
