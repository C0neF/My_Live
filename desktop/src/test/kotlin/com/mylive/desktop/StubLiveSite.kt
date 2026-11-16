package com.mylive.desktop

import com.mylive.app.core.model.*
import com.mylive.app.core.site.LiveSite

internal open class StubLiveSite(override val id: String = "douyu") : LiveSite {
    override val name = id
    override suspend fun getCategories() = emptyList<LiveCategory>()
    override suspend fun searchRooms(keyword: String, page: Int) = LiveSearchRoomResult()
    override suspend fun searchAnchors(keyword: String, page: Int) = LiveSearchAnchorResult()
    override suspend fun getCategoryRooms(category: LiveSubCategory, page: Int) = LiveCategoryResult()
    override suspend fun getRecommendRooms(page: Int) = LiveCategoryResult()
    override suspend fun getRoomDetail(roomId: String) = LiveRoomDetail(roomId, "Room", "", "Anchor", status = true)
    override suspend fun getPlayQualites(detail: LiveRoomDetail) = emptyList<LivePlayQuality>()
    override suspend fun getPlayUrls(detail: LiveRoomDetail, quality: LivePlayQuality) = LivePlayUrl(emptyList())
    override suspend fun getLiveStatus(roomId: String) = true
    override suspend fun getSuperChatMessage(roomId: String, detail: LiveRoomDetail?) = emptyList<LiveSuperChatMessage>()
}
