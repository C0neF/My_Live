package com.mylive.app.ui.screen.home

import androidx.lifecycle.ViewModelStore
import com.mylive.app.core.model.LiveCategory
import com.mylive.app.core.model.LiveCategoryResult
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.core.model.LivePlayUrl
import com.mylive.app.core.model.LiveRoomDetail
import com.mylive.app.core.model.LiveRoomItem
import com.mylive.app.core.model.LiveSearchAnchorResult
import com.mylive.app.core.model.LiveSearchRoomResult
import com.mylive.app.core.model.LiveSubCategory
import com.mylive.app.core.model.LiveSuperChatMessage
import com.mylive.app.core.site.LiveSite
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.ui.screen.category.CategoryViewModel
import com.mylive.app.ui.screen.search.SearchViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomePlatformOrderStartupTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        HomeStateCache.clear()
    }

    @After
    fun tearDown() {
        HomeStateCache.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun coldStartUsesFirstPersistedSortedPlatformBeforeChasingReorders() = runTest(testDispatcher) {
        val requestedRecommendSites = mutableListOf<String>()
        val requestedCategorySites = mutableListOf<String>()
        val requestedSearchSites = mutableListOf<String>()
        val sites = listOf("bilibili", "douyu", "huya", "douyin").map { id ->
            RecordingSite(
                id = id,
                onRecommend = { requestedRecommendSites += id },
                onCategories = { requestedCategorySites += id },
                onSearch = { requestedSearchSites += id }
            )
        }.toSet()

        val siteSortFlow = MutableSharedFlow<String>(replay = 0)
        val settings = mockk<SettingsRepository> {
            every { siteSort } returns siteSortFlow
        }

        val store = ViewModelStore()
        try {
            val homeVm = HomeViewModel(sites, settings)
            val categoryVm = CategoryViewModel(sites, settings)
            val searchVm = SearchViewModel(sites, settings)
            store.put("home", homeVm)
            store.put("category", categoryVm)
            store.put("search", searchVm)

            // Before DataStore emits the persisted sort order, nothing should fetch bilibili.
            advanceUntilIdle()
            assertEquals(emptyList<String>(), requestedRecommendSites)
            assertEquals(emptyList<String>(), requestedCategorySites)
            assertEquals(emptyList<String>(), requestedSearchSites)

            // Simulate DataStore emitting the user's persisted sort order ("douyu" is first).
            siteSortFlow.emit("douyu,huya,douyin,bilibili")
            advanceUntilIdle()

            assertEquals("douyu", homeVm.uiState.value.siteId)
            assertEquals(listOf("douyu"), requestedRecommendSites)
            assertFalse(requestedRecommendSites.contains("bilibili"))

            assertEquals(0, categoryVm.selectedSiteIndex.value)
            assertEquals("douyu", categoryVm.getSelectedSite()?.id)
            assertEquals(listOf("douyu"), requestedCategorySites)

            assertEquals(0, searchVm.uiState.value.selectedSiteIndex)
            searchVm.search("test")
            advanceUntilIdle()
            assertEquals(listOf("douyu"), requestedSearchSites)

            // Once initialized on "douyu", a subsequent live reorder moves selection with "douyu".
            siteSortFlow.emit("huya,douyu,douyin,bilibili")
            advanceUntilIdle()

            assertEquals("douyu", homeVm.uiState.value.siteId)
            assertEquals(1, categoryVm.selectedSiteIndex.value)
            assertEquals("douyu", categoryVm.getSelectedSite()?.id)
            assertEquals(1, searchVm.uiState.value.selectedSiteIndex)
        } finally {
            store.clear()
        }
    }

    private class RecordingSite(
        override val id: String,
        private val onRecommend: () -> Unit,
        private val onCategories: () -> Unit,
        private val onSearch: () -> Unit
    ) : LiveSite {
        override val name: String = id

        override suspend fun getCategories(): List<LiveCategory> {
            onCategories()
            return listOf(LiveCategory("c1", "Cat", listOf(LiveSubCategory("s1", "Sub"))))
        }

        override suspend fun searchRooms(keyword: String, page: Int): LiveSearchRoomResult {
            onSearch()
            return LiveSearchRoomResult(hasMore = false, items = emptyList())
        }

        override suspend fun searchAnchors(keyword: String, page: Int): LiveSearchAnchorResult {
            return LiveSearchAnchorResult(hasMore = false, items = emptyList())
        }

        override suspend fun getCategoryRooms(category: LiveSubCategory, page: Int): LiveCategoryResult {
            return LiveCategoryResult(hasMore = false, items = emptyList())
        }

        override suspend fun getRecommendRooms(page: Int): LiveCategoryResult {
            onRecommend()
            return LiveCategoryResult(
                hasMore = false,
                items = listOf(LiveRoomItem(roomId = "$id-1", title = "Room", cover = "", userName = "User"))
            )
        }

        override suspend fun getRoomDetail(roomId: String): LiveRoomDetail = error("not used")
        override suspend fun getPlayQualites(detail: LiveRoomDetail): List<LivePlayQuality> = error("not used")
        override suspend fun getPlayUrls(detail: LiveRoomDetail, quality: LivePlayQuality): LivePlayUrl = error("not used")
        override suspend fun getLiveStatus(roomId: String): Boolean = false
        override suspend fun getSuperChatMessage(roomId: String, detail: LiveRoomDetail?): List<LiveSuperChatMessage> = emptyList()
    }
}
