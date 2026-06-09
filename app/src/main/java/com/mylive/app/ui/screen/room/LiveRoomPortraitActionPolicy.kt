package com.mylive.app.ui.screen.room

internal enum class PortraitLiveRoomPanel {
    SUPER_CHAT,
    FOLLOW,
    SETTINGS
}

internal data class PortraitLiveRoomAction(
    val panel: PortraitLiveRoomPanel,
    val label: String,
    val badgeCount: Int? = null
)

internal fun resolvePortraitLiveRoomActions(
    siteId: String,
    superChatCount: Int
): List<PortraitLiveRoomAction> {
    val actions = mutableListOf<PortraitLiveRoomAction>()
    when (siteId) {
        "bilibili" -> actions.add(
            PortraitLiveRoomAction(
                panel = PortraitLiveRoomPanel.SUPER_CHAT,
                label = "SC",
                badgeCount = superChatCount.takeIf { it > 0 }
            )
        )
        "huya" -> actions.add(
            PortraitLiveRoomAction(
                panel = PortraitLiveRoomPanel.SUPER_CHAT,
                label = "头条",
                badgeCount = superChatCount.takeIf { it > 0 }
            )
        )
    }
    return actions
}
