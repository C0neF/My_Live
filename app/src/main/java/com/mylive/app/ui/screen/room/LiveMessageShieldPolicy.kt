package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageType

internal data class LiveMessageShieldConfig(
    val shieldValues: List<String> = emptyList(),
    val shieldEnabled: Boolean = true,
    val keywordShieldEnabled: Boolean = true,
    val userShieldEnabled: Boolean = true
)

internal fun shouldShieldLiveMessage(
    message: LiveMessage,
    siteId: String,
    config: LiveMessageShieldConfig
): Boolean {
    if (!config.shieldEnabled || message.type != LiveMessageType.CHAT) return false

    if (config.userShieldEnabled && isUserShieldedByConfig(message.userName, siteId, config.shieldValues)) {
        return true
    }

    if (config.keywordShieldEnabled && isKeywordShieldedByConfig(message.message, config.shieldValues)) {
        return true
    }

    return false
}

private fun isKeywordShieldedByConfig(message: String, shieldValues: List<String>): Boolean {
    for (rawValue in shieldValues) {
        val keyword = rawValue.keywordShieldValue() ?: continue
        if (keyword.isEmpty()) continue
        if (keyword.isSlashRegex()) {
            val regex = try {
                Regex(keyword.substring(1, keyword.length - 1))
            } catch (_: IllegalArgumentException) {
                null
            }
            if (regex != null && regex.containsMatchIn(message)) return true
        } else if (message.contains(keyword)) {
            return true
        }
    }
    return false
}

private fun isUserShieldedByConfig(
    userName: String,
    siteId: String,
    shieldValues: List<String>
): Boolean {
    val normalizedUserName = userName.trim()
    if (normalizedUserName.isEmpty()) return false

    for (rawValue in shieldValues) {
        val rule = rawValue.userShieldRule() ?: continue
        if (rule.userName != normalizedUserName) continue
        if (rule.siteId == GlobalUserShieldSiteId || rule.siteId == siteId) return true
    }
    return false
}

private data class UserShieldRule(
    val siteId: String,
    val userName: String
)

private const val KeywordShieldPrefix = "keyword:"
private const val UserShieldPrefix = "user:"
private const val GlobalUserShieldSiteId = "__all__"

private fun String.keywordShieldValue(): String? {
    val trimmed = trim()
    return when {
        trimmed.startsWith(KeywordShieldPrefix) -> trimmed.removePrefix(KeywordShieldPrefix).trim()
        trimmed.startsWith(UserShieldPrefix) -> null
        else -> trimmed
    }
}

private fun String.userShieldRule(): UserShieldRule? {
    val payload = trim().removePrefix(UserShieldPrefix).takeIf { it != trim() } ?: return null
    val separator = payload.indexOf(':')
    if (separator <= 0 || separator >= payload.lastIndex) return null
    val siteId = payload.substring(0, separator).trim()
    val userName = payload.substring(separator + 1).trim()
    if (siteId.isEmpty() || userName.isEmpty()) return null
    return UserShieldRule(siteId = siteId, userName = userName)
}

private fun String.isSlashRegex(): Boolean =
    length > 2 && startsWith('/') && endsWith('/')
