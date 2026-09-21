package com.mylive.app.ui.screen.search

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.mylive.app.core.model.AppLayoutMode
import com.mylive.app.core.site.LiveSite
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.layout.resolveAppLayout
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
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
class SearchLayoutModeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun manualPhoneModeConstrainsSearchAndPreservesTheQueryWhenSwitchingBack() {
        val mode = mutableStateOf(AppLayoutMode.Phone)
        val viewModel = mockk<SearchViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(SearchUiState())
        every { viewModel.siteTabs } returns MutableStateFlow(emptyList<LiveSite>())
        val navigator = Navigator().apply { navigate(Route.Search) }
        compose.setContent {
            CompositionLocalProvider(LocalAppLayout provides resolveAppLayout(mode.value, 1280, 800, 800, 1f)) {
                MyLiveTheme { SearchScreen(navigator, viewModel) }
            }
        }
        compose.onNodeWithTag("screen_search").assertWidthIsEqualTo(560.dp)
        compose.onNodeWithTag("search_tablet_controls").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("room")
        compose.runOnIdle { mode.value = AppLayoutMode.Tablet }
        compose.onNodeWithTag("search_tablet_controls").assertWidthIsEqualTo(360.dp)
        compose.onNode(hasSetTextAction()).assertTextContains("room")
    }
}