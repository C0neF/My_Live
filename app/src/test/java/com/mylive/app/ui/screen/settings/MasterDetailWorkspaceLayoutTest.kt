package com.mylive.app.ui.screen.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.mylive.app.ui.layout.AppLayoutInfo
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.screen.mine.MineScreen
import com.mylive.app.ui.theme.MyLiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1100dp-h900dp")
class MasterDetailWorkspaceLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun mineScreenSwitchesBetweenPhoneBentoPushAndTabletMasterDetailInlinePane() {
        val phoneNavigator = Navigator()
        compose.setContent {
            CompositionLocalProvider(
                LocalAppLayout provides AppLayoutInfo(tablet = false, widthDp = 390, heightDp = 844)
            ) {
                MyLiveTheme {
                    Box(Modifier.width(390.dp).height(844.dp)) {
                        MineScreen(
                            navigator = phoneNavigator,
                            detailContentOverride = { route -> Text("detail:$route") }
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("mine_master_detail").assertDoesNotExist()
        compose.onNodeWithTag("mine_account").assertIsDisplayed().performClick()
        assertEquals(Route.SettingsAccount, phoneNavigator.backStack.last())
    }

    @Test
    fun tabletMineScreenKeepsSubPagesInlineInDetailPaneWithoutFullScreenPush() {
        val tabletNavigator = Navigator()
        compose.setContent {
            CompositionLocalProvider(
                LocalAppLayout provides AppLayoutInfo(tablet = true, widthDp = 1024, heightDp = 800)
            ) {
                MyLiveTheme {
                    Box(Modifier.width(1024.dp).height(800.dp)) {
                        MineScreen(
                            navigator = tabletNavigator,
                            detailContentOverride = { route -> Text("detail:$route") }
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("mine_master_detail").assertIsDisplayed()
        compose.onNodeWithTag("mine_detail_pane").assertIsDisplayed()
        compose.onNodeWithText("detail:${Route.History}").assertIsDisplayed()

        compose.onNodeWithTag("mine_sync").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("detail:${Route.Sync}").assertIsDisplayed()
        assertEquals(listOf(Route.Index), tabletNavigator.backStack)
    }

    @Test
    fun settingsScreenUsesTwoPaneMasterDetailOnWideTabletAndPushesRouteOnPhone() {
        val tabletNavigator = Navigator()
        tabletNavigator.navigate(Route.Settings)
        compose.setContent {
            CompositionLocalProvider(
                LocalAppLayout provides AppLayoutInfo(tablet = true, widthDp = 1024, heightDp = 800)
            ) {
                MyLiveTheme {
                    Box(Modifier.width(1024.dp).height(800.dp)) {
                        SettingsScreen(
                            navigator = tabletNavigator,
                            detailContentOverride = { route -> Text("settings-detail:$route") }
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("settings_master_detail").assertIsDisplayed()
        compose.onNodeWithTag("settings_detail_pane").assertIsDisplayed()
        compose.onNodeWithText("settings-detail:${Route.SettingsDanmu}").assertIsDisplayed()

        compose.onNodeWithText("播放设置").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("settings-detail:${Route.SettingsPlay}").assertIsDisplayed()
        assertEquals(listOf(Route.Index, Route.Settings), tabletNavigator.backStack)
    }
}
