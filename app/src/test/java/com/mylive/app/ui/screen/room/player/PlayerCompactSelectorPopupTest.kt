package com.mylive.app.ui.screen.room.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.core.model.PlayQualityData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w891dp-h411dp-land")
class PlayerCompactSelectorPopupTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fullscreenQualityButtonOpensCompactFloatingPopoverAndSelectsQuality() {
        val qualities = listOf(
            LivePlayQuality("原画", PlayQualityData.BiliBili(10000)),
            LivePlayQuality("蓝光", PlayQualityData.BiliBili(400)),
            LivePlayQuality("超清", PlayQualityData.BiliBili(250))
        )
        var selectedIndex by mutableIntStateOf(0)
        var sheetCallbackInvoked = false

        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(width = 800.dp, height = 380.dp)) {
                    PlayerView(
                        playerController = null,
                        viewState = PlayerViewUiState(
                            title = "测试直播间",
                            currentQualityName = qualities[selectedIndex].quality,
                            qualities = qualities,
                            currentQualityIndex = selectedIndex,
                            danmakuEnabled = false,
                            fullscreenOverride = true
                        ),
                        actions = PlayerViewActions(
                            onQualityClick = { sheetCallbackInvoked = true },
                            onQualitySelect = { selectedIndex = it }
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        compose.onNodeWithText("原画").assertIsDisplayed().performClick()
        compose.waitForIdle()

        assertFalse("Fullscreen quality must not fall back to bottom sheet callback", sheetCallbackInvoked)
        val popup = compose.onNodeWithTag("player_compact_quality_popup")
        popup.assertIsDisplayed()
        val popupWidthPx = popup.fetchSemanticsNode().boundsInRoot.width
        val maxCompactWidthPx = with(compose.density) { 180.dp.toPx() }
        assertTrue("Expected compact width <= $maxCompactWidthPx, got $popupWidthPx", popupWidthPx <= maxCompactWidthPx + 1f)

        compose.onNodeWithText("蓝光").assertIsDisplayed().performClick()
        compose.waitForIdle()

        assertEquals(1, selectedIndex)
        assertTrue(compose.onAllNodesWithTag("player_compact_quality_popup").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun tappingOutsideFullscreenQualityPopoverDismissesIt() {
        val qualities = listOf(
            LivePlayQuality("原画", PlayQualityData.BiliBili(10000)),
            LivePlayQuality("蓝光", PlayQualityData.BiliBili(400))
        )

        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(width = 800.dp, height = 380.dp)) {
                    PlayerView(
                        playerController = null,
                        viewState = PlayerViewUiState(
                            currentQualityName = "原画",
                            qualities = qualities,
                            currentQualityIndex = 0,
                            danmakuEnabled = false,
                            fullscreenOverride = true
                        ),
                        actions = PlayerViewActions(
                            onQualitySelect = {}
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        compose.onNodeWithText("原画").performClick()
        compose.onNodeWithTag("player_compact_quality_popup").assertIsDisplayed()

        // Tap near the top-left of the player, outside the bottom-right compact popover.
        compose.onNodeWithTag("player_video_waiting").performTouchInput {
            click(Offset(40f, 40f))
        }
        compose.waitForIdle()

        assertTrue(compose.onAllNodesWithTag("player_compact_quality_popup").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun playbackDiagnosticsRendersAsFloatingCardWithCopyAndClose() {
        var dismissed = false
        val snapshot = buildPlaybackDiagnosticSnapshot(
            PlaybackDiagnosticInput(
                qualityName = "原画",
                currentUrl = "https://cdn.example.com/live.flv",
                currentLineIndex = 0,
                lineCount = 2,
                playbackState = "READY",
                sampleMimeType = "video/avc",
                codecs = "avc1",
                width = 1920,
                height = 1080,
                frameRate = 60f,
                bitrate = 8_000_000,
                decoderName = "c2.android.avc.decoder",
                droppedFrames = 0L,
                bufferingCount = 0,
                stallRecoveryCount = 0
            )
        )

        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(width = 800.dp, height = 380.dp)) {
                    PlaybackDiagnosticsFloatingCard(
                        snapshot = snapshot,
                        maxWidth = 340.dp,
                        maxHeight = 300.dp,
                        onDismiss = { dismissed = true }
                    )
                }
            }
        }

        val card = compose.onNodeWithTag("playback_diagnostics_popup")
        card.assertIsDisplayed()
        val bounds = card.fetchSemanticsNode().boundsInRoot
        val maxExpectedHeight = with(compose.density) { 300.dp.toPx() }
        assertTrue("Floating diagnostics card height must respect maxHeight", bounds.height <= maxExpectedHeight + 1f)

        compose.onNodeWithText("播放诊断").assertIsDisplayed()
        compose.onNodeWithText("复制").assertIsDisplayed().performClick()
        compose.onNodeWithText("已复制").assertIsDisplayed()

        compose.onNodeWithContentDescription("关闭播放诊断").performClick()
        compose.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun fullscreenQualityPopoverAnimatesSmoothlyOnOpenAndClose() {
        val qualities = listOf(
            LivePlayQuality("原画", PlayQualityData.BiliBili(10000)),
            LivePlayQuality("蓝光", PlayQualityData.BiliBili(400))
        )
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(width = 800.dp, height = 380.dp)) {
                    PlayerView(
                        playerController = null,
                        viewState = PlayerViewUiState(
                            currentQualityName = "原画",
                            qualities = qualities,
                            currentQualityIndex = 0,
                            danmakuEnabled = false,
                            fullscreenOverride = true
                        ),
                        actions = PlayerViewActions(onQualitySelect = {}),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("原画").performClick()
        compose.runOnIdle {
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(32)
        assertEquals(
            "Compact quality popover should appear immediately on open frame",
            1,
            compose.onAllNodesWithTag("player_compact_quality_popup").fetchSemanticsNodes().size
        )

        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("player_compact_quality_popup").assertIsDisplayed()

        // Tap outside to dismiss: popover should animate out across frames before unmounting.
        compose.onNodeWithTag("player_video_waiting").performTouchInput {
            click(Offset(40f, 40f))
        }
        compose.runOnIdle {
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(24)
        assertEquals(
            "Compact quality popover should animate out rather than vanish in 1 frame",
            1,
            compose.onAllNodesWithTag("player_compact_quality_popup").fetchSemanticsNodes().size
        )

        compose.mainClock.advanceTimeBy(1000)
        assertTrue(compose.onAllNodesWithTag("player_compact_quality_popup").fetchSemanticsNodes().isEmpty())
    }
}
