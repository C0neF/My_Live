package com.mylive.desktop

import com.mylive.app.core.model.*
import kotlinx.serialization.Serializable

@Serializable enum class DesktopVideoScale { Fit, Crop, Stretch }
@Serializable data class DesktopBlockedUser(val siteId: String, val userName: String)
@Serializable data class DesktopDanmakuPreferences(
    val fontSize: Float = 18f, val travelSeconds: Float = 8f, val opacity: Float = 1f,
    val area: Float = .8f, val lines: Int = 8, val colored: Boolean = true,
    val scroll: Boolean = true, val top: Boolean = true, val bottom: Boolean = true,
    val shieldEnabled: Boolean = true, val keywords: List<String> = emptyList(),
    val users: List<DesktopBlockedUser> = emptyList(), val deduplicate: Boolean = false,
    val dedupeSeconds: Int = 10, val dedupeByUser: Boolean = false, val renderEmoji: Boolean = true
) {
    fun validate() {
        require(fontSize.isFinite() && fontSize in 12f..48f && travelSeconds.isFinite() && travelSeconds in 3f..30f)
        require(opacity.isFinite() && opacity in .1f..1f && area.isFinite() && area in .2f..1f)
        require(lines in 1..20 && dedupeSeconds in 1..120)
        require(keywords.all { it.isNotBlank() && it.length <= 200 })
        require(users.all { it.siteId in setOf("__all__", "bilibili", "douyu", "huya", "douyin") && it.userName.isNotBlank() && it.userName.length <= 100 })
    }
    fun allows(message: LiveMessage, siteId: String): Boolean = !shieldEnabled ||
        (keywords.none { message.message.contains(it, ignoreCase = true) } &&
            users.none { (it.siteId == "__all__" || it.siteId == siteId) && it.userName == message.userName })
    fun positionAllowed(message: LiveMessage): Boolean = when (message.danmakuPosition) {
        LiveMessageDanmakuPosition.SCROLL -> scroll
        LiveMessageDanmakuPosition.TOP -> top
        LiveMessageDanmakuPosition.BOTTOM -> bottom
    }
}

internal class DesktopMessageDeduplicator {
    private val seen = LinkedHashMap<String, Long>()
    private val hash = java.security.MessageDigest.getInstance("SHA-256")
    private var previousClock = Long.MIN_VALUE
    fun accepts(message: LiveMessage, settings: DesktopDanmakuPreferences, now: Long): Boolean {
        if (!settings.deduplicate) { seen.clear(); return true }
        if (now < previousClock) seen.clear()
        previousClock = now
        val text = (if (settings.dedupeByUser) message.userName else "") + '\u0000' + message.message
        val key = java.util.Base64.getEncoder().encodeToString(hash.digest(text.toByteArray(Charsets.UTF_8)))
        val previous = seen[key]
        if (previous != null && now - previous < settings.dedupeSeconds * 1000L) return false
        seen[key] = now
        while (seen.size > 2048) seen.remove(seen.keys.first())
        return true
    }
}

internal fun preferredDesktopQuality(qualities: List<LivePlayQuality>, siteId: String, settings: DesktopPlayerPreferences): LivePlayQuality {
    require(qualities.isNotEmpty())
    if (settings.rememberQuality) settings.qualityBySite[siteId]?.let { name -> qualities.firstOrNull { it.quality == name }?.let { return it } }
    return when (settings.qualityLevel) { 1 -> qualities[qualities.lastIndex.coerceAtMost(1)]; 2 -> qualities.last(); else -> qualities.first() }
}
