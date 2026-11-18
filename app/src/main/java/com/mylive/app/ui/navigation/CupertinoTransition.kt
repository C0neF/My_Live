package com.mylive.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import com.mylive.app.ui.motion.AppMotionPolicy

/**
 * Shared Apple Cupertino route motion for Navigation 3.
 *
 * Pairs horizontal slide transitions (`DeferredAnimation` registered in layout) with an
 * imperceptible composition-time alpha anchor (`0.999f..1f`) on the 300ms iOS curve so
 * `SeekableTransitionState` always observes a non-zero `totalDurationNanos` immediately after
 * composition—even when cold-composing a previously disposed parent Settings screen on pop.
 */
object CupertinoTransition {
    internal const val RouteDurationMillis = 300
    internal const val ReducedMotionDurationMillis = 120
    private const val OpaqueAnchorAlpha = 0.999f
    private val CupertinoEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    private fun slideSpec(): FiniteAnimationSpec<IntOffset> =
        tween(durationMillis = RouteDurationMillis, easing = CupertinoEasing)

    private fun opacityAnchorSpec(): FiniteAnimationSpec<Float> =
        tween(durationMillis = RouteDurationMillis, easing = CupertinoEasing)

    private fun reducedFadeSpec(): FiniteAnimationSpec<Float> =
        tween(durationMillis = ReducedMotionDurationMillis)

    fun transitionSpec(
        motionPolicy: AppMotionPolicy
    ): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        if (motionPolicy.reducedMotion) {
            fadeIn(animationSpec = reducedFadeSpec()) togetherWith
                fadeOut(animationSpec = reducedFadeSpec())
        } else {
            val enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = slideSpec()
            ) + fadeIn(
                initialAlpha = OpaqueAnchorAlpha,
                animationSpec = opacityAnchorSpec()
            )
            val exit = if (initialState.isIndexScene()) {
                fadeOut(
                    targetAlpha = OpaqueAnchorAlpha,
                    animationSpec = opacityAnchorSpec()
                )
            } else {
                slideOutHorizontally(
                    targetOffsetX = { -it / 4 },
                    animationSpec = slideSpec()
                ) + fadeOut(
                    targetAlpha = OpaqueAnchorAlpha,
                    animationSpec = opacityAnchorSpec()
                )
            }
            (enter togetherWith exit).using(SizeTransform(clip = false))
        }
    }

    fun popTransitionSpec(
        motionPolicy: AppMotionPolicy
    ): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        popTransform(motionPolicy)
    }

    fun predictivePopTransitionSpec(
        motionPolicy: AppMotionPolicy = AppMotionPolicy.Default
    ): AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform = {
        popTransform(motionPolicy)
    }

    private fun AnimatedContentTransitionScope<Scene<NavKey>>.popTransform(
        motionPolicy: AppMotionPolicy
    ): ContentTransform {
        return if (motionPolicy.reducedMotion) {
            fadeIn(animationSpec = reducedFadeSpec()) togetherWith
                fadeOut(animationSpec = reducedFadeSpec())
        } else {
            val enter = if (targetState.isIndexScene()) {
                fadeIn(
                    initialAlpha = OpaqueAnchorAlpha,
                    animationSpec = opacityAnchorSpec()
                )
            } else {
                slideInHorizontally(
                    initialOffsetX = { -it / 4 },
                    animationSpec = slideSpec()
                ) + fadeIn(
                    initialAlpha = OpaqueAnchorAlpha,
                    animationSpec = opacityAnchorSpec()
                )
            }
            val exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = slideSpec()
            ) + fadeOut(
                targetAlpha = OpaqueAnchorAlpha,
                animationSpec = opacityAnchorSpec()
            )
            (enter togetherWith exit).using(SizeTransform(clip = false))
        }
    }

    private fun Scene<NavKey>.isIndexScene(): Boolean =
        key == Route.Index || key == Route.Index.toString()
}
