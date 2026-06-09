package com.mylive.app.ui.screen.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LiveRoomPortraitActionPolicyTest {

    @Test
    fun bilibiliAddsOnlySuperChatToQuickAccessWithBadge() {
        val actions = resolvePortraitLiveRoomActions(
            siteId = "bilibili",
            superChatCount = 3
        )

        assertEquals(1, actions.size)
        assertEquals(PortraitLiveRoomPanel.SUPER_CHAT, actions[0].panel)
        assertEquals("SC", actions[0].label)
        assertEquals(3, actions[0].badgeCount)
    }

    @Test
    fun huyaLabelsSuperChatAsHeadline() {
        val actions = resolvePortraitLiveRoomActions(
            siteId = "huya",
            superChatCount = 1
        )

        assertEquals(PortraitLiveRoomPanel.SUPER_CHAT, actions[0].panel)
        assertEquals("头条", actions[0].label)
        assertEquals(1, actions[0].badgeCount)
    }

    @Test
    fun unsupportedPlatformsUseDefaultQuickAccessTabsOnly() {
        val actions = resolvePortraitLiveRoomActions(
            siteId = "douyin",
            superChatCount = 4
        )

        assertFalse(actions.any { it.panel == PortraitLiveRoomPanel.SUPER_CHAT })
        assertEquals(emptyList<PortraitLiveRoomAction>(), actions)
    }

    @Test
    fun zeroSuperChatCountKeepsSupportedPanelWithoutBadge() {
        val actions = resolvePortraitLiveRoomActions(
            siteId = "bilibili",
            superChatCount = 0
        )

        assertEquals(PortraitLiveRoomPanel.SUPER_CHAT, actions[0].panel)
        assertEquals(null, actions[0].badgeCount)
    }
}
