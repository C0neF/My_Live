package com.mylive.app.ui.screen.category

import android.app.Application
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.mylive.app.core.model.LiveCategory
import com.mylive.app.core.model.LiveSubCategory
import com.mylive.app.core.site.LiveSite
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
@Config(sdk = [34], application = Application::class)
class CategoryNavigationStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun returningFromCategoryRoomsPreservesParentSelection() {
        val site = mockk<LiveSite>(relaxed = true)
        every { site.id } returns "bilibili"
        every { site.name } returns "Bilibili"
        val model = mockk<CategoryViewModel>(relaxed = true)
        every { model.siteList } returns MutableStateFlow(listOf(site))
        every { model.selectedSiteIndex } returns MutableStateFlow(0)
        every { model.categories } returns MutableStateFlow(listOf(
            LiveCategory("Web games", "web", listOf(LiveSubCategory("All web games", "all-web"))),
            LiveCategory("Mobile games", "mobile", listOf(LiveSubCategory("All mobile games", "all-mobile")))
        ))
        every { model.loading } returns MutableStateFlow(false)
        every { model.isRefreshing } returns MutableStateFlow(false)
        every { model.error } returns MutableStateFlow(null)
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
                            when (key) {
                                Route.Index -> CategoryScreen(navigator, viewModel = model)
                                is Route.CategoryDetail -> {
                                    val actions = navigator.rememberRouteActions()
                                    Button(onClick = actions.onBack) { Text("Return to categories") }
                                }
                                else -> Text("Other screen")
                            }
                        }
                    }
                )
            }
        }

        compose.onNodeWithText("Mobile games").performClick()
        compose.onNodeWithText("All mobile games").performClick()
        compose.onNodeWithText("Return to categories").performClick()

        compose.onNodeWithText("All mobile games").assertExists()
        compose.onNodeWithText("All web games").assertDoesNotExist()
    }
}
