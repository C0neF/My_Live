package com.mylive.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import com.mylive.app.ui.motion.AppMotionPolicy

/**
 * Shared route motion for Navigation 3.
 *
 * Predictive back previews are disabled in the manifest and use a static transform here. Regular
 * route changes use spring specs only after navigation commits, so no page owns an exit timer.
 */
object CupertinoTransition {

    fun transitionSpec(
        motionPolicy: AppMotionPolicy
    ): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        if (motionPolicy.reducedMotion) {
            fadeIn(animationSpec = motionPolicy.effectsSpec()) togetherWith
                fadeOut(animationSpec = motionPolicy.effectsSpec())
        } else {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = motionPolicy.routeSpec()
            ) togetherWith ExitTransition.None
        }
    }

    fun popTransitionSpec(
        motionPolicy: AppMotionPolicy
    ): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        popTransform(motionPolicy)
    }

    fun predictivePopTransitionSpec():
        AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform = {
        (EnterTransition.None togetherWith ExitTransition.None).using(null)
    }

    private fun AnimatedContentTransitionScope<Scene<NavKey>>.popTransform(
        motionPolicy: AppMotionPolicy
    ): ContentTransform {
        return if (motionPolicy.reducedMotion) {
            fadeIn(animationSpec = motionPolicy.effectsSpec()) togetherWith
                fadeOut(animationSpec = motionPolicy.effectsSpec())
        } else {
            EnterTransition.None togetherWith slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = motionPolicy.routeSpec()
            )
        }
    }
}
