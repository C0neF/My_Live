package com.mylive.app.ui.screen.search

import android.app.Application
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import com.mylive.app.core.model.LiveAnchorItem
import com.mylive.app.core.model.LiveRoomItem
import com.mylive.app.core.model.LiveSearchAnchorResult
import com.mylive.app.core.model.LiveSearchRoomResult
import com.mylive.app.core.site.LiveSite
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.theme.MyLiveTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class SearchPaginationFailureTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()

    @After fun clearViewModels() = store.clear()

    @Test fun failedRoomPageWaitsForExplicitRetry() = verifyPagination(searchType = 0)

    @Test fun failedAnchorPageWaitsForExplicitRetry() = verifyPagination(searchType = 1)

    private fun verifyPagination(searchType: Int) {
        val releaseFailure = CompletableDeferred<Unit>()
        val nextPageRequests = AtomicInteger()
        val requestedPages = mutableListOf<Int>()
        val site = mockk<LiveSite> {
            every { id } returns "bilibili"
            every { name } returns "Bilibili"
        }
        suspend fun beforePage(page: Int) {
            requestedPages += page
            if (page > 1 && nextPageRequests.incrementAndGet() == 1) {
                // Keep loading visible for a frame, as a real network request would.
                releaseFailure.await()
                throw IOException("Second page is temporarily unavailable")
            }
        }
        coEvery { site.searchRooms("query", any()) } coAnswers {
            val page = secondArg<Int>()
            beforePage(page)
            LiveSearchRoomResult(
                hasMore = page == 1,
                items = listOf(LiveRoomItem("room-$page", "Room $page", "", "Streamer $page"))
            )
        }
        coEvery { site.searchAnchors("query", any()) } coAnswers {
            val page = secondArg<Int>()
            beforePage(page)
            LiveSearchAnchorResult(
                hasMore = page == 1,
                items = listOf(LiveAnchorItem("anchor-$page", userName = "Anchor $page"))
            )
        }
        val settings = mockk<SettingsRepository> {
            every { siteSort } returns flowOf("bilibili")
        }
        val model = SearchViewModel(setOf(site), settings)
        model.setSearchType(searchType)
        store.put("search-pagination", model)
        val navigator = Navigator().apply { navigate(Route.Search) }
        compose.setContent { MyLiveTheme { SearchScreen(navigator, model) } }
        compose.onNode(hasSetTextAction()).performTextInput("query")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5_000) { nextPageRequests.get() == 1 }
        compose.waitForIdle()
        compose.runOnIdle { releaseFailure.complete(Unit) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertEquals("A failed visible page must not retry without a user action", 1, nextPageRequests.get())
        val firstLabel = if (searchType == 0) "Room 1" else "Anchor 1"
        val secondLabel = if (searchType == 0) "Room 2" else "Anchor 2"
        compose.onNodeWithText(firstLabel).assertExists()
        compose.onNodeWithText("重试").performClick()
        compose.waitUntil(5_000) { nextPageRequests.get() == 2 }
        compose.waitForIdle()

        compose.onNodeWithText(firstLabel).assertExists()
        compose.onNodeWithText(secondLabel).assertExists()
        assertEquals(listOf(1, 2, 2), requestedPages)
        assertEquals(2, model.uiState.value.currentPage)
    }
}
