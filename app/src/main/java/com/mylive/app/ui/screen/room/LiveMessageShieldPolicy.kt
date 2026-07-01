package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageType

internal data class LiveMessageShieldConfig(
    val shieldValues: List<String> = emptyList(),
    val shieldEnabled: Boolean = true,
    val keywordShieldEnabled: Boolean = true,
    val userShieldEnabled: Boolean = true
) {
    val keywordRules: KeywordShieldRules = KeywordShieldRules.from(shieldValues)
    val userRules: UserShieldRules = UserShieldRules.from(shieldValues)
}

internal data class KeywordShieldRules(
    val plainKeywords: List<String> = emptyList(),
    val regexKeywords: List<Regex> = emptyList()
) {
    fun matches(message: String): Boolean {
        for (keyword in plainKeywords) {
            if (message.contains(keyword)) return true
        }
        for (regex in regexKeywords) {
            if (regex.containsMatchIn(message)) return true
        }
        return false
    }

    companion object {
        fun from(shieldValues: List<String>): KeywordShieldRules {
            val plainKeywords = mutableListOf<String>()
            val regexKeywords = mutableListOf<Regex>()
            for (rawValue in shieldValues) {
                val keyword = rawValue.keywordShieldValue() ?: continue
                if (keyword.isEmpty()) continue
                if (keyword.isSlashRegex()) {
                    val regex = runCatching {
                        Regex(keyword.substring(1, keyword.length - 1))
                    }.getOrNull()
                    if (regex != null) {
                        regexKeywords.add(regex)
                    }
                } else {
                    plainKeywords.add(keyword)
                }
            }
            return KeywordShieldRules(
                plainKeywords = plainKeywords,
                regexKeywords = regexKeywords
            )
        }
    }
}

internal data class UserShieldRules(
    val globalUserNames: Set<String> = emptySet(),
    val siteUserNames: Map<String, Set<String>> = emptyMap()
) {
    fun matches(userName: String, siteId: String): Boolean {
        val normalizedUserName = userName.trim()
        if (normalizedUserName.isEmpty()) return false
        if (normalizedUserName in globalUserNames) return true
        return normalizedUserName in siteUserNames[siteId].orEmpty()
    }

    companion object {
        fun from(shieldValues: List<String>): UserShieldRules {
            val globalUserNames = mutableSetOf<String>()
            val siteUserNames = mutableMapOf<String, MutableSet<String>>()
            for (rawValue in shieldValues) {
                val rule = rawValue.userShieldRule() ?: continue
                if (rule.siteId == GlobalUserShieldSiteId) {
                    globalUserNames.add(rule.userName)
                } else {
                    siteUserNames.getOrPut(rule.siteId) { mutableSetOf() }.add(rule.userName)
                }
            }
            return UserShieldRules(
                globalUserNames = globalUserNames,
                siteUserNames = siteUserNames
            )
        }
    }
}

internal fun shouldShieldLiveMessage(
    message: LiveMessage,
    siteId: String,
    config: LiveMessageShieldConfig
): Boolean {
    if (!config.shieldEnabled || message.type != LiveMessageType.CHAT) return false

    if (config.userShieldEnabled && config.userRules.matches(message.userName, siteId)) {
        return true
    }

    if (config.keywordShieldEnabled && config.keywordRules.matches(message.message)) {
        return true
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
