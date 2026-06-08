package com.mylive.app.ui.screen.home

import com.mylive.app.core.model.LiveCategory
import com.mylive.app.core.model.LiveCategoryResult
import com.mylive.app.core.model.LiveContributionRankItem
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.core.model.LivePlayUrl
import com.mylive.app.core.model.LiveRoomDetail
import com.mylive.app.core.model.LiveSearchAnchorResult
import com.mylive.app.core.model.LiveSearchRoomResult
import com.mylive.app.core.model.LiveSuperChatMessage
import com.mylive.app.core.site.LiveDanmaku
import com.mylive.app.core.site.LiveSite
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeNavigationPolicyTest {

    @Test
    fun homeRoomRouteIncludesSelectedSiteId() {
        val route = homeLiveRoomRoute(
            siteTabs = listOf(FakeLiveSite("bilibili"), FakeLiveSite("douyu")),
            selectedTab = 1,
            roomId = "123"
        )

        assertEquals("123", route.roomId)
        assertEquals("douyu", route.siteId)
    }

    @Test
    fun homeRoomRouteFallsBackToEmptySiteIdWhenSelectedTabIsMissing() {
        val route = homeLiveRoomRoute(
            siteTabs = listOf(FakeLiveSite("bilibili")),
            selectedTab = 5,
            roomId = "123"
        )

        assertEquals("123", route.roomId)
        assertEquals("", route.siteId)
    }

    private class FakeLiveSite(override val id: String) : LiveSite {
        override val name: String = id
        override fun getDanmaku(): LiveDanmaku = error("not used")
        override suspend fun getCategories(): List<LiveCategory> = error("not used")
        override suspend fun searchRooms(keyword: String, page: Int): LiveSearchRoomResult = error("not used")
        override suspend fun searchAnchors(keyword: String, page: Int): LiveSearchAnchorResult = error("not used")
        override suspend fun getCategoryRooms(category: com.mylive.app.core.model.LiveSubCategory, page: Int): LiveCategoryResult = error("not used")
        override suspend fun getRecommendRooms(page: Int): LiveCategoryResult = error("not used")
        override suspend fun getRoomDetail(roomId: String): LiveRoomDetail = error("not used")
        override suspend fun getPlayQualites(detail: LiveRoomDetail): List<LivePlayQuality> = error("not used")
        override suspend fun getPlayUrls(detail: LiveRoomDetail, quality: LivePlayQuality): LivePlayUrl = error("not used")
        override suspend fun getLiveStatus(roomId: String): Boolean = error("not used")
        override suspend fun getSuperChatMessage(roomId: String, detail: LiveRoomDetail?): List<LiveSuperChatMessage> = error("not used")
        override suspend fun getContributionRank(roomId: String, detail: LiveRoomDetail?): List<LiveContributionRankItem> = error("not used")
    }
}
