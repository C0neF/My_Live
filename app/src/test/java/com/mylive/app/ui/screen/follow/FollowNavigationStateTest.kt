package com.mylive.app.ui.screen.follow

import android.app.Application
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.service.FollowRefreshEntry
import com.mylive.app.service.FollowRefreshProgress
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.navigation.rememberAppEntryDecorators
import com.mylive.app.ui.theme.MyLiveTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h800dp")
class FollowNavigationStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun returningFromRoomPreservesTabletPlatformFilter() {
        showFollowScreen()
        compose.onNodeWithText("斗鱼 12").performClick()
        compose.onNodeWithText("Bilibili 0").assertDoesNotExist()
        compose.onNodeWithText("Douyu 0").performClick()
        compose.onNodeWithText("Return to follows").performClick()

        compose.onNodeWithText("Bilibili 0").assertDoesNotExist()
        compose.onNodeWithText("Douyu 0").assertIsDisplayed()
    }

    @Test fun returningFromRoomPreservesTabletListPosition() {
        showFollowScreen()
        compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("Douyu 0")))
            .performScrollToIndex(9)
        compose.onNodeWithText("Douyu 8").performClick()
        compose.onNodeWithText("Return to follows").performClick()

        compose.onNodeWithText("Douyu 8").assertIsDisplayed()
    }

    @Test fun changingPlatformStillScrollsItsListsToTop() {
        showFollowScreen()
        compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("Douyu 0")))
            .performScrollToIndex(9)
        compose.onNodeWithText("斗鱼 12").performClick()

        compose.onNodeWithText("Douyu 0").assertIsDisplayed()
    }

    private fun showFollowScreen() {
        val follows = listOf(follow("bilibili", 0, "Bilibili 0")) +
            (0 until 12).map { follow("douyu", it, "Douyu $it") }
        val model = mockk<FollowViewModel>(relaxed = true)
        every { model.follows } returns MutableStateFlow(follows)
        every { model.filteredFollows } returns MutableStateFlow(follows)
        every { model.groupMode } returns MutableStateFlow(FollowGroupMode.STATUS)
        every { model.selectedGroupId } returns MutableStateFlow("all")
        every { model.groupOptions } returns MutableStateFlow(listOf(FollowGroupOption("all", "全部")))
        every { model.userTags } returns MutableStateFlow(emptyList())
        every { model.updatingStatus } returns MutableStateFlow(false)
        every { model.refreshProgress } returns MutableStateFlow(FollowRefreshProgress())
        every { model.refreshEntries } returns MutableStateFlow(emptyMap<String, FollowRefreshEntry>())
        val navigator = Navigator()

        compose.setContent {
            MyLiveTheme {
                NavDisplay(
                    backStack = navigator.backStack,
                    entryDecorators = rememberAppEntryDecorators(),
                    transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    entryProvider = { key ->
                        NavEntry(key) {
                            if (key is Route.LiveRoomDetail) {
                                val actions = navigator.rememberRouteActions()
                                Button(onClick = actions.onBack) { Text("Return to follows") }
                            } else {
                                FollowScreen(navigator, followCardColumns = 2, viewModel = model)
                            }
                        }
                    }
                )
            }
        }
    }

    private fun follow(siteId: String, index: Int, name: String) = FollowUserEntity(
        id = "${siteId}_$index", roomId = "$index", siteId = siteId,
        userName = name, face = "", addTime = 0, liveStatus = 1
    )
}
