package com.mylive.app.ui.theme

import androidx.compose.ui.graphics.Color

internal fun livePlatformAccentColor(platformId: String): Color? {
    return when (platformId.lowercase()) {
        "douyu" -> Color(0xFFFF5D23)
        "huya" -> Color(0xFFFFD736)
        "bilibili" -> Color(0xFFF07775)
        else -> null
    }
}

internal fun livePlatformOnAccentColor(
    platformId: String,
    defaultOnAccentColor: Color
): Color {
    return when (platformId.lowercase()) {
        "douyu", "huya", "bilibili" -> Color.Black
        else -> defaultOnAccentColor
    }
}
