package com.mylive.app.ui.screen

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mylive.app.R
import com.mylive.app.ui.motion.AppMotionPolicy
import com.mylive.app.ui.motion.LocalAppMotionPolicy
import com.mylive.app.ui.theme.Icons
import com.mylive.app.ui.theme.MyLiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class IndexScreenTest {
    @get:Rule val compose = createComposeRule()
    private val clicks = mutableListOf<String>()
    private val destinations = listOf(
        BottomNavItem("recommend", R.string.nav_home, Icons.Default.Home),
        BottomNavItem("follow", R.string.nav_follow, Icons.Default.Favorite),
        BottomNavItem("category", R.string.nav_category, Icons.Default.Category),
        BottomNavItem("user", R.string.nav_mine, Icons.Default.Person)
    )

    private fun showDock(
        fontScale: Float = 1f,
        rtl: Boolean = false,
        reducedMotion: Boolean = false,
        items: List<BottomNavItem> = destinations
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalAppMotionPolicy provides AppMotionPolicy(reducedMotion)
            ) {
                MyLiveTheme {
                    var selected by remember { mutableIntStateOf(0) }
                    Box(Modifier.width(320.dp)) {
                        IndexNavigationDock(items, selected, Color.Blue, {
                            clicks.add(it.key)
                            selected = items.indexOf(it)
                        })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun allDestinationsAreReachableAndExposeSelection() {
        showDock()
        listOf("首页", "关注", "分类", "我的").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithTag("index_nav_recommend").assertIsSelected()
        compose.onNodeWithTag("index_nav_follow").performClick().assertIsSelected()
        compose.onNodeWithTag("index_nav_recommend").assertIsNotSelected()
        assertEquals(listOf("follow"), clicks)
    }

    @Test fun selectedTabRemainsClickableForRefresh() {
        showDock()
        repeat(2) { compose.onNodeWithTag("index_nav_recommend").performClick() }
        assertEquals(listOf("recommend", "recommend"), clicks)
    }

    @Test fun reorderedDestinationsKeepTheirKeysAndActions() {
        showDock(items = destinations.reversed())
        compose.onNodeWithTag("index_nav_user").assertIsSelected()
        compose.onNodeWithTag("index_nav_category").performClick().assertIsSelected()
        assertEquals(listOf("category"), clicks)
    }

    @Test fun draggingAcrossTheDockSelectsOnceOnRelease() {
        showDock()
        val dock = compose.onNodeWithTag("index_navigation_dock")
        dock.performTouchInput {
            down(Offset(width * 0.125f, centerY))
            moveTo(Offset(width * 0.45f, centerY), delayMillis = 150)
            moveTo(Offset(width * 0.875f, centerY), delayMillis = 150)
        }
        assertTrue(clicks.isEmpty())
        compose.onNodeWithTag("index_nav_recommend").assertIsSelected()
        dock.performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithTag("index_nav_user").assertIsSelected()
        assertEquals(listOf("user"), clicks)
    }

    @Test fun canceledDragRestoresTheOriginalSelection() {
        showDock()
        compose.onNodeWithTag("index_navigation_dock").performTouchInput {
            down(Offset(width * 0.125f, centerY))
            moveTo(Offset(width * 0.875f, centerY), delayMillis = 300)
            cancel()
        }
        compose.waitForIdle()
        assertTrue(clicks.isEmpty())
        compose.onNodeWithTag("index_nav_recommend").assertIsSelected()
        assertLensCenteredOn("recommend")
    }

    @Test fun aSingleDragMoveSelectsTheDestinationUnderTheFinger() {
        showDock()
        compose.onNodeWithTag("index_navigation_dock").performTouchInput {
            down(Offset(width * 0.125f, centerY))
            moveTo(Offset(width * 0.375f, centerY), delayMillis = 300)
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("index_nav_follow").assertIsSelected()
        assertEquals(listOf("follow"), clicks)
    }

    @Test fun largeTextKeepsLabelsInsideAccessibleTouchTargets() {
        showDock(fontScale = 2f)
        destinations.zip(listOf("首页", "关注", "分类", "我的")).forEach { (item, label) ->
            val tab = compose.onNodeWithTag("index_nav_${item.key}")
            tab.assertHeightIsAtLeast(72.dp)
            val labelBounds = compose.onNodeWithText(label, useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val tabBounds = tab.fetchSemanticsNode().boundsInRoot
            assertTrue(labelBounds.top >= tabBounds.top && labelBounds.bottom <= tabBounds.bottom)
        }
    }

    @Test fun rightToLeftLayoutKeepsLensAndDragInTheSameDirection() {
        showDock(rtl = true)
        assertLensCenteredOn("recommend")
        compose.onNodeWithTag("index_navigation_dock").performTouchInput {
            swipe(Offset(width * 0.875f, centerY), Offset(width * 0.125f, centerY), 500)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("index_nav_user").assertIsSelected()
        assertLensCenteredOn("user")
    }

    @Test fun reducedMotionMovesLensDirectlyToTheSelectedDestination() {
        showDock(reducedMotion = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("index_nav_category").performClick()
        compose.runOnIdle {
            assertEquals(listOf("category"), clicks)
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("index_nav_category").assertIsSelected()
        assertLensCenteredOn("category")
    }

    private fun assertLensCenteredOn(key: String) {
        val lens = compose.onNodeWithTag("index_navigation_lens", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val tab = compose.onNodeWithTag("index_nav_$key").fetchSemanticsNode().boundsInRoot
        assertEquals(tab.center.x, lens.center.x, 1.5f)
        assertEquals(tab.center.y, lens.center.y, 1.5f)
    }
}
