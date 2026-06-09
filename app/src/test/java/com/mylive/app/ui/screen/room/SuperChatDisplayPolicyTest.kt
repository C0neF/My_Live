package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.LiveSuperChatMessage
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class SuperChatDisplayPolicyTest {

    @Test
    fun mergesActiveSuperChatsByFingerprintAndUpdatesExisting() {
        val now = 1_000L
        val stale = sc(id = "same", message = "old", endTime = 5_000L)
        val updated = sc(id = "same", message = "new", endTime = 6_000L)
        val added = sc(id = "new", message = "added", endTime = 4_000L)
        val expired = sc(id = "expired", message = "gone", endTime = now)

        val result = mergeActiveSuperChats(
            current = listOf(stale),
            incoming = listOf(updated, added, expired),
            nowMillis = now
        )

        assertEquals(listOf(added, updated), result)
    }

    @Test
    fun usesStableFallbackFingerprintWhenIdIsMissing() {
        val message = sc(
            id = null,
            userName = "user",
            message = "content",
            price = 30,
            startTime = 2_000L
        )

        assertEquals(
            "user|content|30|2000",
            superChatFingerprint(message)
        )
    }

    @Test
    fun cardCountdownUsesFullRemainingSeconds() {
        val message = sc(endTime = 120_000L)

        assertEquals(
            119,
            remainingSuperChatSeconds(message, nowMillis = 1_000L)
        )
    }

    @Test
    fun parsesServiceHexColorsForSuperChatSurfaces() {
        assertEquals(
            Color(0xFF112233),
            parseSuperChatColor("#112233", fallback = Color.Black)
        )
    }

    @Test
    fun fallsBackWhenServiceHexColorIsInvalid() {
        assertEquals(
            Color(0xFFFFD600),
            parseSuperChatColor("", fallback = Color(0xFFFFD600))
        )
    }

    private fun sc(
        id: String? = "id",
        userName: String = "user",
        message: String = "message",
        price: Int = 10,
        startTime: Long = 1L,
        endTime: Long = 10_000L
    ): LiveSuperChatMessage {
        return LiveSuperChatMessage(
            id = id,
            userName = userName,
            message = message,
            price = price,
            startTime = startTime,
            endTime = endTime
        )
    }
}
