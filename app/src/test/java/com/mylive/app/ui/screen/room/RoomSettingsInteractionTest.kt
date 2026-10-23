package com.mylive.app.ui.screen.room

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mylive.app.data.repository.LiveRoomPreferences
import com.mylive.app.ui.screen.settings.SettingsViewModel
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class RoomSettingsInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val settings = mockk<SettingsViewModel>(relaxed = true)
    private lateinit var listState: LazyListState
    private lateinit var sliderScroll: ScrollState
    private val commits = mutableListOf<Float>()

    private fun showSlider() {
        compose.setContent {
            MaterialTheme {
                sliderScroll = rememberScrollState()
                Column(Modifier.fillMaxSize().verticalScroll(sliderScroll)) {
                    Spacer(Modifier.height(48.dp))
                    RoomSettingsSlider(1f, { commits.add(it) }, 0.8f..1.5f, 6,
                        modifier = Modifier.testTag("slider"))
                    Spacer(Modifier.height(1200.dp))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun show(heightDp: Int? = null) {
        compose.setContent {
            MaterialTheme {
                listState = rememberLazyListState()
                RoomSettingsPanel(viewModel = settings, liveRoomPreferences = LiveRoomPreferences(), isHuyaOrBilibili = true, siteId = "bilibili",
                    modifier = if (heightDp != null) Modifier.fillMaxWidth().height(heightDp.dp) else Modifier.fillMaxSize(),
                    listState = listState)
            }
        }
        compose.waitForIdle()
    }

    @Test fun scrollingVerticallyFromASliderDoesNotChangeTheSetting() {
        showSlider()
        compose.onNodeWithTag("slider").performTouchInput {
            swipe(center, center - Offset(0f, 180f), durationMillis = 600)
        }
        compose.waitForIdle()
        assertTrue(commits.isEmpty())
        compose.runOnIdle { assertTrue(sliderScroll.value > 0) }
    }

    @Test fun horizontalSliderDragCommitsOnceOnRelease() {
        showSlider()
        val slider = compose.onNodeWithTag("slider")
        slider.performTouchInput {
            down(center)
            moveTo(Offset(centerX + 30f, centerY), delayMillis = 100)
            moveTo(Offset(width - 20f, centerY), delayMillis = 100)
        }
        assertTrue(commits.isEmpty())
        slider.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, commits.size)
        assertTrue(commits.single() > 1.2f)
        assertTrue(slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current > 1.2f)
    }

    @Test fun canceledSliderDragDoesNotPersistItsPreview() {
        showSlider()
        val slider = compose.onNodeWithTag("slider")
        slider.performTouchInput {
            down(center)
            moveTo(Offset(centerX + 30f, centerY), delayMillis = 100)
            moveTo(Offset(width - 20f, centerY), delayMillis = 100)
            cancel()
        }
        compose.waitForIdle()
        assertTrue(commits.isEmpty())
        assertEquals(1f, slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0.001f)
    }
    @Test fun aLongSectionCanBeCollapsedFromItsPinnedHeading() {
        show()
        compose.onNodeWithContentDescription("底部安全边距").performScrollTo()
        compose.onNodeWithTag("room_settings_section_PLAYER_DANMAKU").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        compose.onNodeWithTag("room_settings_content_PLAYER_DANMAKU").assertDoesNotExist()
        compose.onNodeWithTag("room_settings_section_PLAYER_DANMAKU").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已收起"))
        compose.onNodeWithTag("room_settings_section_FILTER").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, listState.firstVisibleItemIndex) }
    }

    @Test fun expandingAnotherSectionKeepsItsHeadingInPlace() {
        show()
        compose.onNodeWithTag("room_settings_section_PLAYER_DANMAKU").performSemanticsAction(SemanticsActions.OnClick) { it() }
        val header = compose.onNodeWithTag("room_settings_section_SHIELD")
        val before = header.fetchSemanticsNode().boundsInRoot.top
        header.performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        header.assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已展开"))
        assertEquals(before, header.fetchSemanticsNode().boundsInRoot.top, 2f)
    }
    @Test fun collapsedCardsKeepTheirGapWhileScrollingPastTheTop() {
        show(heightDp = 320)
        compose.onNodeWithTag("room_settings_section_PLAYER_DANMAKU")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        val originalHeight = compose.onNodeWithTag("room_settings_section_PLAYER_DANMAKU").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { listState.requestScrollToItem(0, 56) }
        compose.waitForIdle()
        val first = compose.onNodeWithTag("room_settings_section_PLAYER_DANMAKU").fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithTag("room_settings_section_LIVE_BEHAVIOR").fetchSemanticsNode().boundsInRoot
        val minimumGap = with(compose.density) { 12.dp.toPx() }
        assertTrue("Cards touched while scrolling: $first / $next", next.top - first.bottom >= minimumGap - 1f)
        assertTrue("A collapsed card should scroll away", first.height < originalHeight)
    }

    @Test fun aPinnedHeadingDoesNotRemainOverTheNextGroup() {
        show(heightDp = 320)
        compose.onNodeWithTag("room_settings_list")
            .performScrollToNode(hasTestTag("room_settings_section_LIVE_BEHAVIOR"))
        compose.onNodeWithTag("room_settings_section_LIVE_BEHAVIOR")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { listState.requestScrollToItem(3) }
        compose.waitForIdle()
        compose.onNodeWithTag("room_settings_section_PLAYER_DANMAKU").assertIsNotDisplayed()
        compose.onNodeWithTag("room_settings_section_LIVE_BEHAVIOR").assertIsDisplayed()
    }
}
