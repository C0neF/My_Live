package com.mylive.app.ui.screen.home

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import com.mylive.app.core.model.LiveCategoryResult
import com.mylive.app.core.model.LiveRoomItem
import com.mylive.app.core.site.LiveSite
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.theme.MyLiveTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class HomePaginationFailureTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()

    @Before fun resetHomeCache() = HomeStateCache.clear()

    @After fun clearViewModel() {
        store.clear()
        HomeStateCache.clear()
    }

    @Test fun failedNextPageWaitsForExplicitRetryAndKeepsExistingRooms() {
        val first = LiveRoomItem("first", "First room", "", "First streamer")
        val second = LiveRoomItem("second", "Second room", "", "Second streamer")
        val releaseFailure = CompletableDeferred<Unit>()
        val nextPageRequests = AtomicInteger()
        val requestedPages = mutableListOf<Int>()
        val site = mockk<LiveSite> {
            every { id } returns "bilibili"
            every { name } returns "Bilibili"
        }
        coEvery { site.getRecommendRooms(any()) } coAnswers {
            val page = firstArg<Int>()
            requestedPages += page
            if (page == 1) {
                LiveCategoryResult(hasMore = true, items = listOf(first))
            } else if (nextPageRequests.incrementAndGet() == 1) {
                // Let the loading state reach the real lazy grid before completing the request.
                releaseFailure.await()
                throw IOException("Second page is temporarily unavailable")
            } else {
                // A bounded success makes the old automatic retry fail an assertion, not hang.
                LiveCategoryResult(hasMore = false, items = listOf(second))
            }
        }
        val settings = mockk<SettingsRepository> {
            every { siteSort } returns flowOf("bilibili")
        }
        val model = HomeViewModel(setOf(site), settings)
        store.put("home-pagination", model)

        compose.setContent {
            MyLiveTheme {
                HomeScreen(
                    navigator = Navigator(),
                    homeLiveRoomGridColumns = 2,
                    viewModel = model
                )
            }
        }
        compose.waitUntil(5_000) { nextPageRequests.get() == 1 }
        compose.waitForIdle()
        compose.runOnIdle { releaseFailure.complete(Unit) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertEquals("A failed visible page must not retry without a user action", 1, nextPageRequests.get())
        compose.onNodeWithText("First room").assertExists()
        compose.onNodeWithText("重试").performClick()
        compose.waitUntil(5_000) { nextPageRequests.get() == 2 }
        compose.waitForIdle()

        compose.onNodeWithText("First room").assertExists()
        compose.onNodeWithText("Second room").assertExists()
        assertEquals(listOf(1, 2, 2), requestedPages)
        assertEquals(2, model.uiState.value.currentPage)
    }
}
