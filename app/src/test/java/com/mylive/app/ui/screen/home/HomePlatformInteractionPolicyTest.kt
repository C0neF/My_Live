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

class HomePlatformInteractionPolicyTest {

    @Test
    fun platformSelectorShowsAllPlatformsAndHighlightsCurrentPlatform() {
        val layout = homePlatformSelectorLayout(
            siteTabs = listOf(
                FakeLiveSite("douyu"),
                FakeLiveSite("bilibili"),
                FakeLiveSite("huya"),
                FakeLiveSite("douyin")
            ),
            selectedIndex = 2
        )

        assertEquals(2, layout.primarySiteIndex)
        assertEquals(listOf(0, 1, 2, 3), layout.secondarySiteIndices)
    }

    @Test
    fun platformSelectorClampsSelectedPlatformIndexAndStillShowsAllPlatforms() {
        val layout = homePlatformSelectorLayout(
            siteTabs = listOf(
                FakeLiveSite("douyu"),
                FakeLiveSite("bilibili")
            ),
            selectedIndex = 10
        )

        assertEquals(1, layout.primarySiteIndex)
        assertEquals(listOf(0, 1), layout.secondarySiteIndices)
    }

    @Test
    fun platformClickKeepsFarPagePreJumpPolicy() {
        assertEquals(null, homePlatformPreJumpPageForTarget(currentPage = 0, targetPage = 1))
        assertEquals(null, homePlatformPreJumpPageForTarget(currentPage = 2, targetPage = 1))
        assertEquals(2, homePlatformPreJumpPageForTarget(currentPage = 0, targetPage = 3))
        assertEquals(1, homePlatformPreJumpPageForTarget(currentPage = 3, targetPage = 0))
    }

    @Test
    fun platformDisplayNameRemovesLiveSuffixOnlyForBilibili() {
        assertEquals("哔哩哔哩", homePlatformDisplayName("哔哩哔哩直播"))
        assertEquals("斗鱼直播", homePlatformDisplayName("斗鱼直播"))
        assertEquals("虎牙直播", homePlatformDisplayName("虎牙直播"))
        assertEquals("抖音直播", homePlatformDisplayName("抖音直播"))
        assertEquals("其它平台", homePlatformDisplayName("其它平台"))
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
