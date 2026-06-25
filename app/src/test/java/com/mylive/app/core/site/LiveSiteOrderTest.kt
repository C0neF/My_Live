package com.mylive.app.core.site

import com.mylive.app.core.model.LiveAnchorItem
import com.mylive.app.core.model.LiveCategory
import com.mylive.app.core.model.LiveCategoryResult
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.core.model.LivePlayUrl
import com.mylive.app.core.model.LiveRoomDetail
import com.mylive.app.core.model.LiveSearchAnchorResult
import com.mylive.app.core.model.LiveSearchRoomResult
import com.mylive.app.core.model.LiveSubCategory
import com.mylive.app.core.model.LiveSuperChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveSiteOrderTest {

    @Test
    fun sortedByDefaultOrderUsesStableProductOrder() {
        val sites = listOf(
            TestSite("douyin", "抖音直播"),
            TestSite("huya", "虎牙直播"),
            TestSite("unknown", "未知直播"),
            TestSite("bilibili", "哔哩哔哩直播"),
            TestSite("douyu", "斗鱼直播")
        )

        assertEquals(
            listOf("bilibili", "douyu", "huya", "douyin", "unknown"),
            sites.sortedByDefaultOrder().map { it.id }
        )
    }

    @Test
    fun reorderedSitesPreserveThePreviouslySelectedSiteIdentity() {
        assertEquals(
            2,
            preserveSelectedSiteIndex(
                previousSiteIds = listOf("bilibili", "douyu", "huya", "douyin"),
                reorderedSiteIds = listOf("douyin", "huya", "douyu", "bilibili"),
                selectedIndex = 1
            )
        )
    }

    @Test
    fun reorderedSitesClampSelectionWhenThePreviousSiteDisappears() {
        assertEquals(
            1,
            preserveSelectedSiteIndex(
                previousSiteIds = listOf("bilibili", "douyu", "huya"),
                reorderedSiteIds = listOf("bilibili", "huya"),
                selectedIndex = 2
            )
        )
        assertEquals(
            0,
            preserveSelectedSiteIndex(
                previousSiteIds = listOf("bilibili"),
                reorderedSiteIds = emptyList(),
                selectedIndex = 0
            )
        )
    }

    private data class TestSite(
        override val id: String,
        override val name: String
    ) : LiveSite {
        override fun getDanmaku(): LiveDanmaku = error("Not needed")
        override suspend fun getCategories(): List<LiveCategory> = emptyList()
        override suspend fun searchRooms(keyword: String, page: Int): LiveSearchRoomResult = LiveSearchRoomResult()
        override suspend fun searchAnchors(keyword: String, page: Int): LiveSearchAnchorResult = LiveSearchAnchorResult()
        override suspend fun getCategoryRooms(category: LiveSubCategory, page: Int): LiveCategoryResult = LiveCategoryResult()
        override suspend fun getRecommendRooms(page: Int): LiveCategoryResult = LiveCategoryResult()
        override suspend fun getRoomDetail(roomId: String): LiveRoomDetail =
            LiveRoomDetail(roomId = roomId, title = "", cover = "", userName = "")
        override suspend fun getPlayQualites(detail: LiveRoomDetail): List<LivePlayQuality> = emptyList()
        override suspend fun getPlayUrls(detail: LiveRoomDetail, quality: LivePlayQuality): LivePlayUrl = LivePlayUrl(emptyList())
        override suspend fun getLiveStatus(roomId: String): Boolean = false
        override suspend fun getSuperChatMessage(roomId: String, detail: LiveRoomDetail?): List<LiveSuperChatMessage> = emptyList()
    }
}
