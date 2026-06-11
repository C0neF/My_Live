package com.mylive.app.ui.screen.home

import androidx.compose.ui.graphics.Color
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
    fun platformSelectionRestoresFromLoadedStateSiteIdAfterBottomTabReturn() {
        val siteTabs = listOf(
            FakeLiveSite("bilibili"),
            FakeLiveSite("douyu"),
            FakeLiveSite("huya")
        )

        assertEquals(1, homeSelectedPlatformIndex(siteTabs = siteTabs, siteId = "douyu"))
        assertEquals(0, homeSelectedPlatformIndex(siteTabs = siteTabs, siteId = ""))
        assertEquals(0, homeSelectedPlatformIndex(siteTabs = siteTabs, siteId = "missing"))
        assertEquals(0, homeSelectedPlatformIndex(siteTabs = emptyList(), siteId = "douyu"))
    }

    @Test
    fun homeScreenUsesUiStateSiteIdAsPlatformSelectionSource() {
        val source = File("src/main/java/com/mylive/app/ui/screen/home/HomeScreen.kt").readText()

        assertTrue(source.contains("homeSelectedPlatformIndex(siteTabs = siteTabs, siteId = uiState.siteId)"))
        assertTrue(source.contains("pagerState.scrollToPage(restoredTab)"))
        assertFalse(source.contains("var selectedTab by rememberSaveable { mutableIntStateOf(0) }"))
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

    @Test
    fun platformAccentUsesRequestedBrandColors() {
        assertEquals(Color(0xFFFF5D23), homePlatformAccentColor("douyu"))
        assertEquals(Color(0xFFFFD736), homePlatformAccentColor("huya"))
        assertEquals(null, homePlatformAccentColor("douyin"))
        assertEquals(Color(0xFFF07775), homePlatformAccentColor("bilibili"))
    }

    @Test
    fun platformChipUsesBrandBackgroundOnlyWhenSelected() {
        val selectedFallback = Color.Blue
        val unselectedFallback = Color.Gray

        assertEquals(
            Color(0xFFFF5D23),
            homePlatformChipContainerColor("douyu", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            Color(0xFFFFD736),
            homePlatformChipContainerColor("huya", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            selectedFallback,
            homePlatformChipContainerColor("douyin", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            Color(0xFFF07775),
            homePlatformChipContainerColor("bilibili", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            unselectedFallback,
            homePlatformChipContainerColor("douyu", selectedFallback, unselectedFallback, isSelected = false)
        )
    }

    @Test
    fun platformChipUsesReadableContentColorOnBrandBackgrounds() {
        val selectedFallback = Color.White
        val unselectedFallback = Color.Gray

        assertEquals(
            Color.Black,
            homePlatformChipContentColor("douyu", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            unselectedFallback,
            homePlatformChipContentColor("huya", selectedFallback, unselectedFallback, isSelected = false)
        )
        assertEquals(
            selectedFallback,
            homePlatformChipContentColor("douyin", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            Color.Black,
            homePlatformChipContentColor("bilibili", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            unselectedFallback,
            homePlatformChipContentColor("bilibili", selectedFallback, unselectedFallback, isSelected = false)
        )
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
