package com.mylive.app.ui.screen.search

import android.app.Application
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.mylive.app.core.model.LiveRoomItem
import com.mylive.app.core.site.LiveSite
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.navigation.rememberAppEntryDecorators
import com.mylive.app.ui.theme.MyLiveTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SearchNavigationStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun returningFromRoomPreservesSearchInputAndLetsUserSearchAgain() {
        val site = mockk<LiveSite>(relaxed = true)
        every { site.id } returns "douyu"
        every { site.name } returns "Douyu"
        val model = mockk<SearchViewModel>(relaxed = true)
        every { model.uiState } returns MutableStateFlow(
            SearchUiState(rooms = listOf(LiveRoomItem("123", "CS room", "", "Streamer")))
        )
        every { model.siteTabs } returns MutableStateFlow(listOf(site))
        val navigator = Navigator().apply { navigate(Route.Search) }

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
                                Route.Search -> SearchScreen(navigator, model)
                                is Route.LiveRoomDetail -> {
                                    val actions = navigator.rememberRouteActions()
                                    Button(onClick = actions.onBack) { Text("Return to search") }
                                }
                                else -> Text("Home")
                            }
                        }
                    }
                )
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("cs")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("CS room").performClick()
        compose.onNodeWithText("Return to search").performClick()

        compose.onNodeWithText("CS room").assertExists()
        compose.onNode(hasSetTextAction()).assertTextContains("cs")
        compose.onNode(hasSetTextAction()).performImeAction()
        verify(exactly = 2) { model.search("cs") }
    }
}
