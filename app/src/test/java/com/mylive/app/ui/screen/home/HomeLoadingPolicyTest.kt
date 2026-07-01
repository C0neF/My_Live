package com.mylive.app.ui.screen.home

import com.mylive.app.core.model.LiveRoomItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeLoadingPolicyTest {

    @Test
    fun coldHomeStartShowsInitialLoadingSkeleton() {
        val state = HomeUiState(isLoading = true, rooms = emptyList())

        assertTrue(shouldShowHomeInitialLoading(state, suppressInitialLoadingEffect = false))
    }

    @Test
    fun liveRoomReturnHidesInitialLoadingSkeleton() {
        val state = HomeUiState(isLoading = true, rooms = emptyList())

        assertFalse(shouldShowHomeInitialLoading(state, suppressInitialLoadingEffect = true))
    }

    @Test
    fun populatedHomeNeverShowsInitialLoadingSkeleton() {
        val state = HomeUiState(
            isLoading = true,
            rooms = listOf(
                LiveRoomItem(
                    roomId = "1",
                    title = "Loaded room",
                    cover = "",
                    userName = "Anchor"
                )
            )
        )

        assertFalse(shouldShowHomeInitialLoading(state, suppressInitialLoadingEffect = false))
    }

    @Test
    fun loadMoreShowsRoomCardSkeletonsOnlyWhenAppending() {
        val room = LiveRoomItem(
            roomId = "1",
            title = "Loaded room",
            cover = "",
            userName = "Anchor"
        )

        assertEquals(
            4,
            homeLoadMoreSkeletonItemCount(HomeUiState(isLoading = true, rooms = listOf(room)))
        )
        assertEquals(
            0,
            homeLoadMoreSkeletonItemCount(HomeUiState(isLoading = true, rooms = emptyList()))
        )
        assertEquals(
            0,
            homeLoadMoreSkeletonItemCount(HomeUiState(isLoading = false, rooms = listOf(room)))
        )
    }

    @Test
    fun loadMoreUsesSkeletonCardsInsteadOfCircularIndicator() {
        val source = File("src/main/java/com/mylive/app/ui/screen/home/HomeScreen.kt").readText()

        assertTrue(source.contains("LiveRoomCardSkeleton("))
        assertFalse(source.contains("CircularProgressIndicator("))
    }

    @Test
    fun backToTopButtonShowsOnlyWhenHomeListIsScrolledWithRooms() {
        assertTrue(homeBackToTopButtonVisible(isAtTop = false, hasRooms = true))
        assertFalse(homeBackToTopButtonVisible(isAtTop = true, hasRooms = true))
        assertFalse(homeBackToTopButtonVisible(isAtTop = false, hasRooms = false))
    }

    @Test
    fun homeBackToTopButtonUsesSharedCircularControl() {
        val source = File("src/main/java/com/mylive/app/ui/screen/home/HomeScreen.kt").readText()

        assertTrue(source.contains("BackToTopButton("))
        assertFalse(source.contains("SmallFloatingActionButton("))
    }

    @Test
    fun homeRoomGridUsesStableContentTypes() {
        val source = File("src/main/java/com/mylive/app/ui/screen/home/HomeScreen.kt").readText()

        assertTrue(source.contains("items(\n                        items = uiState.rooms,"))
        assertTrue(source.contains("key = { it.roomId }"))
        assertTrue(source.contains("contentType = { \"home_room\" }"))
        assertTrue(source.contains("contentType = { \"home_load_more_skeleton\" }"))
    }

    @Test
    fun cachedHomeStateRestoresLoadedRoomsWithoutInitialLoadingSkeleton() {
        HomeStateCache.clear()
        HomeStateCache.put(
            siteId = "bilibili",
            state = HomeUiState(
                rooms = listOf(
                    LiveRoomItem(
                        roomId = "1",
                        title = "Cached room",
                        cover = "",
                        userName = "Anchor"
                    )
                ),
                isLoading = true,
                isRefreshing = true,
                error = "old error"
            )
        )

        val restored = HomeStateCache.get("bilibili") ?: error("Expected cached state")

        assertEquals(1, restored.rooms.size)
        assertFalse(restored.isLoading)
        assertFalse(restored.isRefreshing)
        assertEquals(null, restored.error)
        assertFalse(shouldShowHomeInitialLoading(restored, suppressInitialLoadingEffect = false))
    }
}
