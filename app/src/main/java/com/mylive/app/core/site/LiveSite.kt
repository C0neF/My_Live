package com.mylive.app.core.site

import com.mylive.app.core.model.*

interface LiveSite {
    val id: String
    val name: String

    /**
     * Creates a ready-to-start connection for the room that was just resolved.
     * Implementations own aliases, cached handshake data, and fallback policy.
     */
    fun createDanmakuSession(
        requestedRoomId: String,
        detail: LiveRoomDetail
    ): LiveDanmakuSession = error("Danmaku is not supported by $name")

    suspend fun getCategories(): List<LiveCategory>

    suspend fun searchRooms(keyword: String, page: Int = 1): LiveSearchRoomResult

    suspend fun searchAnchors(keyword: String, page: Int = 1): LiveSearchAnchorResult

    suspend fun getCategoryRooms(category: LiveSubCategory, page: Int = 1): LiveCategoryResult

    suspend fun getRecommendRooms(page: Int = 1): LiveCategoryResult

    suspend fun getRoomDetail(roomId: String): LiveRoomDetail

    suspend fun getPlayQualites(detail: LiveRoomDetail): List<LivePlayQuality>

    suspend fun getPlayUrls(detail: LiveRoomDetail, quality: LivePlayQuality): LivePlayUrl

    suspend fun getLiveStatus(roomId: String): Boolean

    suspend fun getSuperChatMessage(roomId: String, detail: LiveRoomDetail? = null): List<LiveSuperChatMessage>
}

/**
 * The stored account credentials a site can consume.
 *
 * An enum rather than a site-id string so that adding a credential is a compile error everywhere
 * it has to be handled, instead of a silently missing map entry.
 */
enum class LiveSiteAccount {
    BILIBILI,
    DOUYIN
}

/**
 * Optional capability for sites whose API and danmaku handshake use a signed-in
 * account cookie.
 */
interface AccountAwareLiveSite : LiveSite {
    /** Which stored credential this site consumes. */
    val accountKey: LiveSiteAccount

    fun updateAccountCookie(cookie: String)
}

private val defaultSiteOrder = listOf("bilibili", "douyu", "huya", "douyin")

fun Iterable<LiveSite>.sortedByDefaultOrder(): List<LiveSite> {
    return sortedWith(
        compareBy<LiveSite> { site ->
            val index = defaultSiteOrder.indexOf(site.id)
            if (index == -1) Int.MAX_VALUE else index
        }.thenBy { it.name }
            .thenBy { it.id }
    )
}

fun Iterable<LiveSite>.sortedByUserOrder(orderStr: String): List<LiveSite> {
    val order = orderStr.split(",").filter { it.isNotBlank() }
    if (order.isEmpty()) return sortedByDefaultOrder()
    return sortedWith(
        compareBy<LiveSite> { site ->
            val index = order.indexOf(site.id)
            if (index == -1) Int.MAX_VALUE else index
        }.thenBy { it.name }
            .thenBy { it.id }
    )
}

fun preserveSelectedSiteIndex(
    previousSiteIds: List<String>,
    reorderedSiteIds: List<String>,
    selectedIndex: Int
): Int {
    if (reorderedSiteIds.isEmpty()) return 0
    val selectedSiteId = previousSiteIds.getOrNull(selectedIndex)
    val preservedIndex = reorderedSiteIds.indexOf(selectedSiteId)
    return if (preservedIndex >= 0) {
        preservedIndex
    } else {
        selectedIndex.coerceIn(0, reorderedSiteIds.lastIndex)
    }
}
