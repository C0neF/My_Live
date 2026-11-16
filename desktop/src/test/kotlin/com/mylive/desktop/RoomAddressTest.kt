package com.mylive.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RoomAddressTest {
    @Test fun acceptsRoomIdAndTrimsClipboardWhitespace() {
        assertEquals(RoomAddress("douyu", "9999"), parseRoomAddress(" 9999\n", "douyu"))
        assertEquals(RoomAddress("huya", "lck"), parseRoomAddress("lck", "huya"))
    }

    @Test fun platformLinksOverrideCurrentlySelectedPlatform() {
        val cases = mapOf(
            "https://www.douyu.com/9999?dyshid=x" to RoomAddress("douyu", "9999"),
            "https://m.douyu.com/9999" to RoomAddress("douyu", "9999"),
            "https://www.huya.com/lck/" to RoomAddress("huya", "lck"),
            "https://live.bilibili.com/blanc/6?broadcast_type=0" to RoomAddress("bilibili", "6"),
            "live.douyin.com/123456" to RoomAddress("douyin", "123456")
        )
        cases.forEach { (url, expected) -> assertEquals(url, expected, parseRoomAddress(url, "huya")) }
    }

    @Test fun rejectsUnsupportedHostsAndExecutableSchemes() {
        listOf("https://douyu.com.evil.example/12", "https://evilhuya.com/12", "file:///12",
            "javascript://douyu.com/12", "https://douyu.com/", "", "123 456").forEach { value ->
            assertThrows(value, IllegalArgumentException::class.java) { parseRoomAddress(value, "douyu") }
        }
    }
}
