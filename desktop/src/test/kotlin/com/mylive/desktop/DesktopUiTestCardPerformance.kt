package com.mylive.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.mylive.shared.ui.PlatformTab
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DesktopUiTestCardPerformance {
    @Test
    fun hoveringLoadedMediaAfterPlatformSwitchDoesNotDrawTheMediaAgain() =
        runSkikoComposeUiTest(size = Size(800f, 600f)) {
            val mediaDraws = AtomicInteger()
            var platform by mutableStateOf("douyu")
            var opened = 0
            val firstCover = Color(0xff2563eb)
            val secondCover = Color(0xfff97316)
            setContent {
                DesktopTheme(DesktopAppearancePreferences()) {
                    Column(Modifier.fillMaxSize()) {
                        Row {
                            PlatformTab("斗鱼", platform == "douyu", UiAccent, Modifier.width(120.dp)) {
                                platform = "douyu"
                            }
                            PlatformTab("虎牙", platform == "huya", UiAccent, Modifier.width(120.dp)) {
                                platform = "huya"
                            }
                        }
                        val interactions = remember { MutableInteractionSource() }
                        val hovered = interactions.collectIsHoveredAsState()
                        val focused = interactions.collectIsFocusedAsState()
                        val coverColor = if (platform == "douyu") firstCover else secondCover
                        Box(
                            Modifier.padding(20.dp).size(320.dp, 200.dp)
                                .testTag("preview-card")
                                .desktopCardSurface(
                                    shape = DesktopButtonShape,
                                    hoveredState = hovered,
                                    focusedState = focused,
                                    containerColor = UiSurface,
                                    baseBorderColor = UiHairline,
                                    highlightBorderColor = UiAccent
                                )
                                .clickable(interactions, indication = null) { opened++ }
                        ) {
                            Box(
                                Modifier.fillMaxSize().testTag("loaded-media").drawBehind {
                                    mediaDraws.incrementAndGet()
                                    drawRect(coverColor)
                                }
                            )
                            Box(
                                Modifier.align(Alignment.Center).size(42.dp).graphicsLayer {
                                    alpha = if (hovered.value || focused.value) 1f else 0f
                                }.background(Color.White)
                            )
                        }
                    }
                }
            }

            val media = onNodeWithTag("preview-card")
            media.captureToImage()
            val drawsBeforeSwitch = mediaDraws.get()
            onNodeWithText("虎牙").performClick()
            val updatedImage = media.captureToImage()
            assertEquals(secondCover, updatedImage.toPixelMap()[updatedImage.width / 2, updatedImage.height / 2])
            assertTrue("A new platform must redraw its new media", mediaDraws.get() > drawsBeforeSwitch)
            val drawsAfterSwitch = mediaDraws.get()
            val cardCenter = onNodeWithTag("preview-card").fetchSemanticsNode().boundsInRoot.center
            repeat(4) {
                onRoot().performMouseInput { moveTo(cardCenter) }
                val hoveredImage = media.captureToImage()
                assertEquals(Color.White, hoveredImage.toPixelMap()[hoveredImage.width / 2, hoveredImage.height / 2])
                onRoot().performMouseInput { moveTo(Offset(760f, 580f)) }
                val unhoveredImage = media.captureToImage()
                assertEquals(secondCover, unhoveredImage.toPixelMap()[unhoveredImage.width / 2, unhoveredImage.height / 2])
            }
            assertEquals(
                "Hover changes must keep the already loaded media drawing cached",
                drawsAfterSwitch,
                mediaDraws.get()
            )
            onNodeWithTag("preview-card").performClick()
            assertEquals(1, opened)
        }
}
