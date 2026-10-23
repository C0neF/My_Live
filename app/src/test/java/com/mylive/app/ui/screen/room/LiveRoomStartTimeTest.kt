package com.mylive.app.ui.screen.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

class LiveRoomStartTimeTest {
    private val chinaTime = ZoneId.of("Asia/Shanghai")

    @Test
    fun douyuSecondsAreRenderedAsALocalDateInsteadOfTheRawTimestamp() {
        assertEquals("2026-09-24 10:43:34", formatLiveRoomStartTime("1790217814", chinaTime))
    }

    @Test
    fun millisecondTimestampsDescribeTheSameInstant() {
        assertEquals("2026-09-24 10:43:34", formatLiveRoomStartTime("1790217814000", chinaTime))
    }

    @Test
    fun displayUsesTheRequestedTimezone() {
        assertEquals("2026-09-24 02:43:34", formatLiveRoomStartTime("1790217814", ZoneOffset.UTC))
    }

    @Test
    fun whitespaceAroundTheTimestampIsIgnored() {
        assertEquals("2026-09-24 10:43:34", formatLiveRoomStartTime(" 1790217814 ", chinaTime))
    }

    @Test
    fun missingAndInvalidValuesDoNotDisplayEpochDatesOrArbitraryText() {
        listOf(null, "", " ", "0", "-1", "null", "unknown", Long.MAX_VALUE.toString())
            .forEach { value -> assertNull(value, formatLiveRoomStartTime(value, chinaTime)) }
    }
}
