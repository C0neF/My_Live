package com.mylive.app.ui.screen.room

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAutoScrollPolicyTest {

    @Test
    fun followsWhenListWasAtBottomBeforeBurstAppend() {
        assertTrue(
            shouldAutoScrollChat(
                autoScrollDisabled = false,
                hasMessages = true
            )
        )
    }

    @Test
    fun followsWhenListWasNearBottomBeforeAppend() {
        assertTrue(
            shouldAutoScrollChat(
                autoScrollDisabled = false,
                hasMessages = true
            )
        )
    }

    @Test
    fun doesNotFollowWhenUserScrolledAwayFromBottom() {
        assertFalse(
            shouldAutoScrollChat(
                autoScrollDisabled = true,
                hasMessages = true
            )
        )
    }

    @Test
    fun followsInitialMessagesBeforeAnyItemsAreVisible() {
        assertTrue(
            shouldAutoScrollChat(
                autoScrollDisabled = false,
                hasMessages = true
            )
        )
    }

    @Test
    fun disablesAutoScrollWhenUserScrollsAwayFromBottom() {
        assertTrue(
            reduceChatAutoScrollDisabled(
                currentDisabled = false,
                isNearBottom = false,
                userScrolledAwayFromBottom = true
            )
        )
    }

    @Test
    fun reEnablesAutoScrollWhenUserReturnsNearBottom() {
        assertFalse(
            reduceChatAutoScrollDisabled(
                currentDisabled = true,
                isNearBottom = true,
                userScrolledAwayFromBottom = false
            )
        )
    }

    @Test
    fun keepsAutoScrollEnabledWhenAppendTemporarilyIncreasesExtentAfter() {
        assertFalse(
            reduceChatAutoScrollDisabled(
                currentDisabled = false,
                isNearBottom = false,
                userScrolledAwayFromBottom = false
            )
        )
    }

    @Test
    fun showsLatestButtonWhenNewMessagesArriveAwayFromBottom() {
        assertTrue(
            shouldShowLatestChatButton(
                previousLastMessageId = 99,
                currentLastMessageId = 100,
                autoScrollDisabled = true
            )
        )
    }

    @Test
    fun hidesLatestButtonWhenNewMessagesAutoFollowBottom() {
        assertFalse(
            shouldShowLatestChatButton(
                previousLastMessageId = 99,
                currentLastMessageId = 100,
                autoScrollDisabled = false
            )
        )
    }

    @Test
    fun hidesLatestButtonWhenThereIsNoNewMessage() {
        assertFalse(
            shouldShowLatestChatButton(
                previousLastMessageId = 100,
                currentLastMessageId = 100,
                autoScrollDisabled = true
            )
        )
    }

    @Test
    fun preservesDisplayedHistoryWhileAutoScrollIsDisabled() {
        assertTrue(
            mergeChatDisplayMessages(
                currentDisplay = listOf(1L, 2L, 3L, 4L),
                sourceMessages = listOf(3L, 4L, 5L),
                autoScrollDisabled = true,
                maxEnabledMessages = 3,
                keyOf = { it }
            ) == listOf(1L, 2L, 3L, 4L, 5L)
        )
    }

    @Test
    fun returnsToSourceWindowWhenAutoScrollIsEnabled() {
        assertTrue(
            mergeChatDisplayMessages(
                currentDisplay = listOf(1L, 2L, 3L, 4L),
                sourceMessages = listOf(3L, 4L, 5L),
                autoScrollDisabled = false,
                maxEnabledMessages = 2,
                keyOf = { it }
            ) == listOf(4L, 5L)
        )
    }
}
