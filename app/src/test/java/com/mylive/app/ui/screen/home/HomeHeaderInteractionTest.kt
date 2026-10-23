package com.mylive.app.ui.screen.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mylive.app.core.model.LiveRoomItem
import com.mylive.app.core.site.LiveSite
import com.mylive.app.ui.layout.AppLayoutInfo
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.theme.MyLiveTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1000dp-h900dp")
class HomeHeaderInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val navigator = Navigator()
    private val viewModel = mockk<HomeViewModel>(relaxed = true)
    private val labels = listOf("哔哩哔哩", "斗鱼直播", "虎牙直播", "抖音直播")
    private val siteIds = listOf("bilibili", "douyu", "huya", "douyin")
    private val sites = siteIds.mapIndexed { index, siteId ->
        mockk<LiveSite> {
            every { id } returns siteId
            every { name } returns labels[index]
        }
    }
    private val state = MutableStateFlow(HomeUiState(
        siteId = "bilibili",
        rooms = List(40) { index ->
            LiveRoomItem(roomId = "$index", title = "直播 $index", cover = "", userName = "主播 $index")
        },
        hasMore = false
    ))
    private val refreshSignal = mutableIntStateOf(0)
    private var bottomBarRevealRequests = 0

    private fun showHome(widthDp: Int = 320, fontScale: Float = 1f) {
        HomeStateCache.clear()
        every { viewModel.uiState } returns state
        every { viewModel.siteTabs } returns MutableStateFlow(sites)
        every { viewModel.selectSite(any()) } answers {
            state.value = state.value.copy(siteId = siteIds[firstArg<Int>()])
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalAppLayout provides AppLayoutInfo(tablet = widthDp >= 600, widthDp = widthDp)
            ) {
                MyLiveTheme {
                    Box(Modifier.width(widthDp.dp).height(780.dp)) {
                        HomeScreen(
                            navigator = navigator,
                            refreshSignal = refreshSignal.intValue,
                            onRevealBottomBar = { bottomBarRevealRequests++ },
                            homeLiveRoomGridColumns = 2,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun phonePlatformsShareFourEqualCenteredSlots() {
        showHome()
        assertFourEqualSlots()
    }

    @Test fun tabletPlatformsUseTheWholeAvailableWidth() {
        showHome(widthDp = 900)
        assertFourEqualSlots()
    }

    @Test fun doubleSizeTextWrapsWithoutClippingPlatformNames() {
        showHome(fontScale = 2f)
        assertFourEqualSlots()
        labels.forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true)
                .assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            assertFalse("Platform label overflowed: $label", layouts.single().hasVisualOverflow)
        }
    }

    @Test fun scrollingUpHidesSearchAndExpandsTheGridWhilePlatformsStayPinned() {
        showHome()
        val originalGrid = compose.onNodeWithTag("home_rooms_pager").fetchSemanticsNode().boundsInRoot
        val headerHeight = compose.onNodeWithTag("home_search_header").fetchSemanticsNode().boundsInRoot.height
        scrollUp()
        compose.onNodeWithTag("home_search").assertIsNotDisplayed()
        assertEquals(0f, compose.onNodeWithTag("home_platform_bar").fetchSemanticsNode().boundsInRoot.top, 1f)
        val expandedGrid = compose.onNodeWithTag("home_rooms_pager").fetchSemanticsNode().boundsInRoot
        assertEquals(headerHeight, expandedGrid.height - originalGrid.height, 1f)
        assertFourEqualSlots()
        compose.onNodeWithTag("home_rooms_pager").performTouchInput {
            swipe(Offset(centerX, height * 0.25f), Offset(centerX, height * 0.75f), 800)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("home_search").assertIsDisplayed()
    }

    @Test fun horizontalPagingChangesPlatformWithoutHidingSearch() {
        showHome()
        compose.onNodeWithTag("home_rooms_pager").performTouchInput {
            swipe(Offset(width * 0.85f, centerY), Offset(width * 0.15f, centerY), 800)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("home_platform_douyu").assertIsSelected()
        compose.onNodeWithTag("home_search").assertIsDisplayed()
    }

    @Test fun platformTabsStillSwitchTheRoomPager() {
        showHome()
        compose.onNodeWithTag("home_platform_douyin").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("home_platform_douyin").assertIsSelected()
        assertEquals("douyin", state.value.siteId)
    }

    @Test fun backToTopRestoresSearchAndRequestsBottomNavigation() {
        showHome()
        scrollUp()
        compose.onNodeWithContentDescription("回到顶部").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("home_search").assertIsDisplayed()
        assertEquals(1, bottomBarRevealRequests)
    }

    @Test fun emptyErrorPagesRestoreSearchWithoutRequiringAScrollableGrid() {
        showHome()
        scrollUp()
        compose.runOnIdle { state.value = HomeUiState(siteId = "bilibili", error = "暂时无法加载") }
        compose.waitForIdle()
        compose.onNodeWithTag("home_search").assertIsDisplayed()
    }

    @Test fun aHomeRefreshRequestRestoresSearch() {
        showHome()
        scrollUp()
        compose.runOnIdle { refreshSignal.intValue++ }
        compose.waitForIdle()
        compose.onNodeWithTag("home_search").assertIsDisplayed()
        verify(exactly = 1) { viewModel.refresh() }
    }

    @Test fun loadingFailureRestoresSearchAfterTheSkeletonWasScrolled() {
        state.value = HomeUiState(siteId = "bilibili", isLoading = true, hasMore = false)
        showHome()
        scrollUp()
        compose.onNodeWithTag("home_search").assertIsNotDisplayed()
        compose.runOnIdle {
            state.value = state.value.copy(isLoading = false, error = "暂时无法加载")
        }
        compose.waitForIdle()
        compose.onNodeWithTag("home_search").assertIsDisplayed()
    }

    @Test fun searchStillOpensTheSearchRoute() {
        showHome()
        compose.onNodeWithTag("home_search").performClick()
        assertEquals(Route.Search, navigator.backStack.last())
    }

    private fun scrollUp() {
        compose.onNodeWithTag("home_rooms_pager").performTouchInput {
            swipe(Offset(centerX, height * 0.75f), Offset(centerX, height * 0.25f), 800)
        }
        compose.waitForIdle()
    }

    private fun assertFourEqualSlots() {
        val bar = compose.onNodeWithTag("home_platform_bar").fetchSemanticsNode().boundsInRoot
        siteIds.forEachIndexed { index, siteId ->
            val tab = compose.onNodeWithTag("home_platform_$siteId").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertEquals(bar.width / 4f, tab.width, 1f)
            assertEquals(bar.left + (index + 0.5f) * bar.width / 4f, tab.center.x, 1f)
            val label = compose.onNodeWithText(labels[index], useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(tab.center.x, label.center.x, 1f)
            assertTrue(label.top >= tab.top && label.bottom <= tab.bottom)
        }
    }
}
