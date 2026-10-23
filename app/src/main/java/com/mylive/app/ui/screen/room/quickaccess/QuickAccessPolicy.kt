package com.mylive.app.ui.screen.room.quickaccess

import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.playbackRoomId
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.data.local.entity.HistoryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

internal val quickAccessDefaultTabs = listOf("follow", "history", "recommendation")

internal fun orderedQuickAccessTabs(sort: String, extraKeys: List<String>): List<String> =
    (sort.split(',').map(String::trim).filter { it in quickAccessDefaultTabs } +
        quickAccessDefaultTabs + extraKeys.filter(String::isNotBlank)).distinct()

internal fun resolveQuickAccessPanelHeightDp(screenHeightDp: Int): Int =
    (screenHeightDp.coerceAtLeast(0) * 0.76f).roundToInt().coerceIn(280, 588)
        .coerceAtMost((screenHeightDp - 80).coerceAtLeast(0))

enum class QuickAccessStatus(val label: String) {
    All("全部"), Live("直播中"), Offline("未开播"), Unknown("待更新");

    fun matches(value: Int): Boolean = when (this) {
        All -> true
        Live -> value == 1
        Offline -> value == 2
        Unknown -> value != 1 && value != 2
    }
}

data class QuickAccessFilters(
    val query: String = "",
    val siteId: String? = null,
    val status: QuickAccessStatus = QuickAccessStatus.All,
    val tagId: String? = null
)

internal fun quickAccessSiteName(siteId: String): String = when (siteId) {
    "bilibili" -> "哔哩哔哩"
    "douyu" -> "斗鱼"
    "huya" -> "虎牙"
    "douyin" -> "抖音"
    else -> siteId
}

internal fun quickAccessRemark(remarks: Map<String, String>, siteId: String, userName: String): String? =
    remarks["${siteId.lowercase()}::${userName.trim()}"]?.takeIf(String::isNotBlank)

private fun matchesQuickAccessQuery(query: String, vararg fields: String?): Boolean {
    val words = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return words.all { word -> fields.any { it?.contains(word, ignoreCase = true) == true } }
}

internal fun filterQuickAccessFollows(
    follows: List<FollowUserEntity>,
    tags: List<FollowUserTagEntity>,
    remarks: Map<String, String>,
    filters: QuickAccessFilters
): List<FollowUserEntity> {
    val selectedTag = tags.firstOrNull { it.id == filters.tagId }
    val selectedMembers = selectedTag?.userIds?.toHashSet()
    return follows.asSequence().filter { user ->
        (filters.siteId == null || user.siteId == filters.siteId) &&
            filters.status.matches(user.liveStatus) &&
            (selectedMembers == null || user.id in selectedMembers) &&
            matchesQuickAccessQuery(filters.query, user.userName, user.roomId, user.playbackRoomId,
                quickAccessSiteName(user.siteId), quickAccessRemark(remarks, user.siteId, user.userName))
    }.distinctBy { it.siteId to it.roomId }
        .sortedWith(compareByDescending<FollowUserEntity> { it.isSpecialFollow }
            .thenBy { when (it.liveStatus) { 1 -> 0; 2 -> 2; else -> 1 } }
            .thenByDescending { it.liveStartTime ?: it.addTime })
        .toList()
}

internal fun filterQuickAccessHistory(
    history: List<HistoryEntity>,
    remarks: Map<String, String>,
    filters: QuickAccessFilters
): List<HistoryEntity> = history.asSequence().filter { item ->
    (filters.siteId == null || item.siteId == filters.siteId) &&
        matchesQuickAccessQuery(filters.query, item.userName, item.roomId,
            quickAccessSiteName(item.siteId), quickAccessRemark(remarks, item.siteId, item.userName))
}.sortedByDescending { it.updateTime }.distinctBy { it.siteId to it.roomId }.toList()

internal fun isCurrentQuickAccessRoom(
    siteId: String,
    roomId: String,
    currentSiteId: String,
    currentRoomId: String,
    resolvedRoomId: String?
): Boolean = siteId == currentSiteId && (roomId == currentRoomId || roomId == resolvedRoomId)

internal fun quickAccessHistoryDay(
    timestamp: Long,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "M月d日" else "yyyy年M月d日"))
    }
}

internal fun formatQuickAccessRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - timestamp).coerceAtLeast(0L)) / 60_000
    return when {
        minutes < 1 -> "刚刚看过"
        minutes < 60 -> "${minutes}分钟前"
        minutes < 1440 -> "${minutes / 60}小时前"
        else -> "${minutes / 1440}天前"
    }
}

internal fun quickAccessPopularity(online: Int): String =
    if (online >= 10_000) "${(online / 1000) / 10.0}万 人气" else "$online 人气"
