package com.mylive.app.ui.screen.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.mylive.app.core.model.AppLayoutMode
import com.mylive.app.core.model.TabletOrientationMode
import com.mylive.app.ui.layout.resolveAppLayout
import com.mylive.app.ui.theme.MyLiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w600dp-h960dp")
class LayoutModeSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun automaticallyEnabledTabletLayoutCanBeDisabledAndReenabled() {
        val mode = mutableStateOf(AppLayoutMode.Auto)
        val orientation = mutableStateOf(TabletOrientationMode.FollowSystem)
        compose.setContent {
            MyLiveTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    LayoutModeSettings(
                        mode.value, orientation.value,
                        resolveAppLayout(mode.value, 1280, 800, 800, 1f).tablet,
                        { mode.value = it }, { orientation.value = it }
                    )
                }
            }
        }
        compose.onNodeWithTag("layout_mode_auto").assertIsSelected()
        compose.onNodeWithTag("tablet_rotation_followsystem").assertIsSelected()
        compose.onNodeWithTag("layout_mode_phone").performClick()
        compose.onNodeWithTag("layout_mode_phone").assertIsSelected()
        compose.onNodeWithTag("tablet_rotation_landscape").assertDoesNotExist()
        compose.runOnIdle { assertEquals(AppLayoutMode.Phone, mode.value) }
        compose.onNodeWithTag("layout_mode_tablet").performClick()
        compose.onNodeWithTag("tablet_rotation_landscape").performScrollTo().performClick()
        compose.onNodeWithTag("tablet_rotation_landscape").assertIsSelected()
        compose.runOnIdle { assertEquals(TabletOrientationMode.Landscape, orientation.value) }
    }

    @Test fun modeChoicesRemainReachableAtTwoHundredPercentFontScale() {
        val mode = mutableStateOf(AppLayoutMode.Auto)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MyLiveTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        LayoutModeSettings(mode.value, TabletOrientationMode.FollowSystem, true,
                            { mode.value = it }, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("layout_mode_phone").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("layout_mode_phone").assertIsSelected()
        compose.onNodeWithTag("tablet_rotation_followsystem").performScrollTo().assertIsDisplayed()
    }
}