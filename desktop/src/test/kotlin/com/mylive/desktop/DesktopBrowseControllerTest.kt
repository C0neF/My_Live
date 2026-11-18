package com.mylive.desktop

import com.mylive.app.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopBrowseControllerTest {
    private fun room(id: String) = LiveRoomItem(id, id, "", id)

    @Test fun returningToAPlatformRestoresLoadedPagesAndExplicitRefreshFetchesNewRooms() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<String>()
        val sites = listOf("douyu", "huya").map { id -> object : StubLiveSite(id) {
            override suspend fun getRecommendRooms(page: Int): LiveCategoryResult {
                requests += "$id:$page"
                return LiveCategoryResult(page == 1, listOf(room("$id-$page")))
            }
        } }
        val controller = DesktopBrowseController(sites, dispatcher, dispatcher)
        try {
            controller.recommend("douyu"); runCurrent()
            controller.more(); runCurrent()
            controller.recommend("huya"); runCurrent()
            controller.recommend("douyu"); runCurrent()
            assertEquals(listOf("douyu-1", "douyu-2"), controller.state.value.rooms.map { it.roomId })
            assertEquals(2, controller.state.value.page)
            assertEquals(listOf("douyu:1", "douyu:2", "huya:1"), requests)
            controller.refresh(); runCurrent()
            assertEquals(listOf("douyu-1"), controller.state.value.rooms.map { it.roomId })
            assertEquals("douyu:1", requests.last())
        } finally { controller.close() }
    }

    @Test fun retryAfterARefreshFailureReloadsTheFirstPageAndKeepsVisibleRooms() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pages = mutableListOf<Int>()
        val site = object : StubLiveSite() {
            override suspend fun getRecommendRooms(page: Int): LiveCategoryResult {
                pages += page
                if (pages.size == 2) error("refresh failed")
                return LiveCategoryResult(false, listOf(room("$page")))
            }
        }
        val controller = DesktopBrowseController(listOf(site), dispatcher, dispatcher)
        try {
            controller.recommend(site.id); runCurrent()
            controller.refresh(); runCurrent()
            assertEquals("1", controller.state.value.rooms.single().roomId)
            controller.retry(); runCurrent()
            assertEquals(listOf(1, 1, 1), pages)
            assertNull(controller.state.value.error)
        } finally { controller.close() }
    }

    @Test fun oldPlatformResponseCannotReplaceNewCategories() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val old = object : StubLiveSite("douyu") { override suspend fun getCategories(): List<LiveCategory> {
            withContext(NonCancellable) { gate.await() }; return listOf(LiveCategory("old", "old"))
        } }
        val new = object : StubLiveSite("huya") { override suspend fun getCategories() = listOf(LiveCategory("new", "new")) }
        val controller = DesktopBrowseController(listOf(old, new), dispatcher, dispatcher)
        controller.categories("douyu"); runCurrent()
        controller.categories("huya"); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals("new", controller.state.value.categories.single().id)
        assertEquals("huya", controller.state.value.siteId)
        controller.close()
    }

    @Test fun categoryPagesDeduplicateAndStopWhenThePlatformMakesNoProgress() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var requested = 0
        val category = LiveSubCategory("Game", "game", "parent")
        val site = object : StubLiveSite() { override suspend fun getCategoryRooms(category: LiveSubCategory, page: Int): LiveCategoryResult {
            assertEquals("parent", category.parentId); requested++
            return LiveCategoryResult(true, listOf(room("same")))
        } }
        val controller = DesktopBrowseController(listOf(site), dispatcher, dispatcher)
        controller.category(site.id, category); runCurrent()
        controller.more(); controller.more(); runCurrent()
        assertEquals(2, requested)
        assertEquals(1, controller.state.value.rooms.size)
        assertFalse(controller.state.value.hasMore)
        controller.more(); runCurrent()
        assertEquals(2, requested)
        controller.close()
    }

    @Test fun anchorSearchRetainsRoomNavigationAndPagination() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val site = object : StubLiveSite() { override suspend fun searchAnchors(keyword: String, page: Int): LiveSearchAnchorResult {
            assertEquals("anchor", keyword)
            return LiveSearchAnchorResult(page == 1, listOf(LiveAnchorItem("$page", userName = "name $page", liveStatus = true)))
        } }
        val controller = DesktopBrowseController(listOf(site), dispatcher, dispatcher)
        controller.search(site.id, " anchor ", true); runCurrent(); controller.more(); runCurrent()
        assertEquals(listOf("1", "2"), controller.state.value.anchors.map { it.roomId })
        assertTrue(controller.state.value.rooms.isEmpty())
        assertFalse(controller.state.value.hasMore)
        controller.close()
    }

    @Test fun aLateSearchCannotReplaceTheNewQuery() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val site = object : StubLiveSite() { override suspend fun searchRooms(keyword: String, page: Int): LiveSearchRoomResult {
            if (keyword == "old") withContext(NonCancellable) { gate.await() }
            return LiveSearchRoomResult(items = listOf(room(keyword)))
        } }
        val controller = DesktopBrowseController(listOf(site), dispatcher, dispatcher)
        controller.search(site.id, "old", false); runCurrent()
        controller.search(site.id, "new", false); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals("new", controller.state.value.rooms.single().roomId)
        controller.close()
    }

    @Test fun failedPageRetainsItemsAndRetryRequestsTheSamePage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var failures = 1
        val pages = mutableListOf<Int>()
        val site = object : StubLiveSite() { override suspend fun getRecommendRooms(page: Int): LiveCategoryResult {
            pages += page
            if (page == 2 && failures-- > 0) error("network")
            return LiveCategoryResult(page < 2, listOf(room("$page")))
        } }
        val controller = DesktopBrowseController(listOf(site), dispatcher, dispatcher)
        controller.recommend(site.id); runCurrent(); controller.more(); runCurrent()
        assertEquals(listOf("1"), controller.state.value.rooms.map { it.roomId })
        assertNotNull(controller.state.value.error)
        controller.more(); runCurrent()
        assertEquals(listOf(1, 2, 2), pages)
        assertEquals(listOf("1", "2"), controller.state.value.rooms.map { it.roomId })
        controller.close()
    }
}
