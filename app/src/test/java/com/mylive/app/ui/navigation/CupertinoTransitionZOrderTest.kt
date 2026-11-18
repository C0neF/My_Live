package com.mylive.app.ui.navigation

import android.animation.ValueAnimator
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.mylive.app.ui.motion.AppMotionPolicy
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CupertinoTransitionZOrderTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun enableAnimations() {
        runCatching {
            ValueAnimator::class.java
                .getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(null, 1f)
        }
    }

    @After
    fun resetAnimations() {
        runCatching {
            ValueAnimator::class.java
                .getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(null, 1f)
        }
    }

    private fun advanceFrames(frameCount: Int) {
        compose.waitForIdle()
        repeat(frameCount) {
            compose.mainClock.advanceTimeBy(16)
            compose.waitForIdle()
        }
    }

    private fun mountRememberedNavDisplay(
        navigator: Navigator,
        recomposeTick: () -> Int = { 0 }
    ) {
        val policy = AppMotionPolicy(reducedMotion = false)
        compose.setContent {
            val tick = recomposeTick()
            val decorators = rememberAppEntryDecorators()
            val onBack = remember(navigator) {
                {
                    navigator.backStack.lastOrNull()?.let { navigator.goBack(expectedTop = it) }
                    Unit
                }
            }
            val forwardSpec = remember(policy) { CupertinoTransition.transitionSpec(policy) }
            val popSpec = remember(policy) { CupertinoTransition.popTransitionSpec(policy) }
            val predictiveSpec = remember(policy) { CupertinoTransition.predictivePopTransitionSpec(policy) }
            val provider = remember(navigator) {
                entryProvider<NavKey> {
                    entry<Route.Index> {
                        Box(Modifier.fillMaxSize().testTag("screen_Index"))
                    }
                    entry<Route.Settings> {
                        Box(Modifier.fillMaxSize().background(Color.White).testTag("screen_Settings")) {
                            Text("Settings")
                        }
                    }
                    entry<Route.SettingsDanmu> {
                        Box(Modifier.fillMaxSize().background(Color.White).testTag("screen_SettingsDanmu")) {
                            Text("SettingsDanmu")
                        }
                    }
                    entry<Route.SettingsDanmuShield> {
                        Box(Modifier.fillMaxSize().background(Color.White).testTag("screen_SettingsDanmuShield")) {
                            Text("SettingsDanmuShield")
                        }
                    }
                }
            }
            Box(Modifier.size(400.dp, 800.dp)) {
                Text("tick=$tick")
                NavDisplay(
                    backStack = navigator.backStack,
                    entryDecorators = decorators,
                    onBack = onBack,
                    transitionSpec = forwardSpec,
                    popTransitionSpec = popSpec,
                    predictivePopTransitionSpec = predictiveSpec,
                    entryProvider = provider
                )
            }
        }
    }

    @Test
    fun popFromSettledSettingsSubpageAnimatesSlideOutEvenUnderParentRecompositions() {
        val navigator = Navigator()
        var recomposeTick by mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        mountRememberedNavDisplay(navigator) { recomposeTick }
        advanceFrames(2)

        compose.runOnIdle { navigator.navigate(Route.Settings) }
        advanceFrames(30)

        compose.runOnIdle { navigator.navigate(Route.SettingsDanmu) }
        advanceFrames(30)

        compose.runOnIdle {
            assertTrue(navigator.goBack(expectedTop = Route.SettingsDanmu))
        }
        advanceFrames(3)
        compose.runOnIdle { recomposeTick++ }
        advanceFrames(3)
        compose.runOnIdle { recomposeTick++ }
        advanceFrames(2)

        val subpageNodes = compose.onAllNodes(hasTestTag("screen_SettingsDanmu")).fetchSemanticsNodes()
        assertTrue("Expected SettingsDanmu to still be animating out", subpageNodes.isNotEmpty())
        val left = subpageNodes.single().boundsInRoot.left
        val width = subpageNodes.single().boundsInRoot.width
        assertTrue("Expected SettingsDanmu to be mid-slide (left=$left, width=$width)", left > 20f && left < width - 20f)
    }

    @Test
    fun popShortlyAfterOpeningSettingsSubpageAnimatesSmoothlyInsteadOfSnapping() {
        val navigator = Navigator()
        compose.mainClock.autoAdvance = false
        mountRememberedNavDisplay(navigator)
        advanceFrames(2)

        compose.runOnIdle { navigator.navigate(Route.Settings) }
        advanceFrames(30)

        compose.runOnIdle { navigator.navigate(Route.SettingsDanmu) }
        advanceFrames(22)

        val arrivedLeft = compose.onNodeWithTag("screen_SettingsDanmu").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Expected SettingsDanmu to have arrived, actual left=$arrivedLeft", arrivedLeft < 5f)

        compose.runOnIdle {
            assertTrue(navigator.goBack(expectedTop = Route.SettingsDanmu))
        }
        advanceFrames(7)

        val subpageNodes = compose.onAllNodes(hasTestTag("screen_SettingsDanmu")).fetchSemanticsNodes()
        assertTrue("Expected SettingsDanmu to animate out rather than snap", subpageNodes.isNotEmpty())
        val midPopLeft = subpageNodes.single().boundsInRoot.left
        val width = subpageNodes.single().boundsInRoot.width
        assertTrue(
            "Expected SettingsDanmu to be sliding out smoothly (left=$midPopLeft, width=$width)",
            midPopLeft > 20f && midPopLeft < width - 20f
        )
    }

    @Test
    fun consecutiveMultiLevelSettingsPopsAllAnimateSlideOutWithParallax() {
        val navigator = Navigator()
        compose.mainClock.autoAdvance = false
        mountRememberedNavDisplay(navigator)
        advanceFrames(2)

        compose.runOnIdle { navigator.navigate(Route.Settings) }
        advanceFrames(30)

        compose.runOnIdle { navigator.navigate(Route.SettingsDanmu) }
        advanceFrames(30)

        compose.runOnIdle { navigator.navigate(Route.SettingsDanmuShield) }
        advanceFrames(30)
        val shieldArrivedLeft = compose.onNodeWithTag("screen_SettingsDanmuShield").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Expected ShieldSettings to have arrived (left=$shieldArrivedLeft)", shieldArrivedLeft < 5f)

        // Pop 1: Shield -> Danmu
        compose.runOnIdle { assertTrue(navigator.goBack(expectedTop = Route.SettingsDanmuShield)) }
        advanceFrames(7)
        val pop1ShieldNodes = compose.onAllNodes(hasTestTag("screen_SettingsDanmuShield")).fetchSemanticsNodes()
        val pop1DanmuNodes = compose.onAllNodes(hasTestTag("screen_SettingsDanmu")).fetchSemanticsNodes()
        assertTrue(
            "Expected ShieldSettings to still exist mid-pop #1 (danmuCount=${pop1DanmuNodes.size}, danmuLeft=${pop1DanmuNodes.firstOrNull()?.boundsInRoot?.left})",
            pop1ShieldNodes.isNotEmpty()
        )
        val shieldLeft = pop1ShieldNodes.single().boundsInRoot.left
        val danmuParallaxLeft = pop1DanmuNodes.single().boundsInRoot.left
        assertTrue("Expected ShieldSettings to slide out on pop #1 (left=$shieldLeft)", shieldLeft > 20f)
        assertTrue("Expected DanmuSettings to enter with Cupertino negative parallax (left=$danmuParallaxLeft)", danmuParallaxLeft < 0f)
        advanceFrames(30)

        // Pop 2: Danmu -> Settings
        compose.runOnIdle { assertTrue(navigator.goBack(expectedTop = Route.SettingsDanmu)) }
        advanceFrames(7)
        val danmuLeft = compose.onNodeWithTag("screen_SettingsDanmu").fetchSemanticsNode().boundsInRoot.left
        val settingsParallaxLeft = compose.onNodeWithTag("screen_Settings").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Expected DanmuSettings to slide out on pop #2 (left=$danmuLeft)", danmuLeft > 20f)
        assertTrue("Expected Settings to enter with Cupertino negative parallax (left=$settingsParallaxLeft)", settingsParallaxLeft < 0f)
    }
}
