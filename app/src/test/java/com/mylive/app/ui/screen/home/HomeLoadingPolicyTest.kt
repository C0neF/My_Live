package com.mylive.app.ui.screen.home

import com.mylive.app.core.model.LiveRoomItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
