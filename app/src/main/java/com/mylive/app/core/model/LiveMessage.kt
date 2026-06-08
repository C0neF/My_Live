package com.mylive.app.core.model

enum class LiveMessageType {
    CHAT, GIFT, ONLINE, SUPER_CHAT
}

data class LiveMessageColor(
    val r: Int,
    val g: Int,
    val b: Int
) {
    companion object {
        val WHITE = LiveMessageColor(255, 255, 255)

        fun numberToColor(intColor: Int): LiveMessageColor {
            return LiveMessageColor(
                r = (intColor shr 16) and 0xFF,
                g = (intColor shr 8) and 0xFF,
                b = intColor and 0xFF
            )
        }
    }
}

sealed interface LiveMessageSpan {
    data class Text(val text: String) : LiveMessageSpan
    data class Image(val imageUrl: String) : LiveMessageSpan
}

data class LiveMessage(
    val type: LiveMessageType,
    val userName: String,
    val message: String,
    val color: LiveMessageColor = LiveMessageColor.WHITE,
    val imageUrls: List<String>? = null,
    val spans: List<LiveMessageSpan>? = null,
    // For ONLINE type: viewer count. For SUPER_CHAT: LiveSuperChatMessage.
    // Use typed accessors instead of dynamic.
    val onlineCount: Int? = null,
    val superChatMessage: LiveSuperChatMessage? = null
)
