package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.LiveMessageColor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageColorPolicyTest {

    @Test
    fun customDanmakuColorAppliesToMessageTextOnly() {
        val policy = resolveChatMessageColorPolicy(
            LiveMessageColor(r = 255, g = 0, b = 0)
        )

        assertFalse(policy.applyMessageColorToUserName)
        assertTrue(policy.applyMessageColorToText)
    }

    @Test
    fun defaultWhiteDoesNotOverrideChatTextColor() {
        val policy = resolveChatMessageColorPolicy(LiveMessageColor.WHITE)

        assertFalse(policy.applyMessageColorToUserName)
        assertFalse(policy.applyMessageColorToText)
    }
}
