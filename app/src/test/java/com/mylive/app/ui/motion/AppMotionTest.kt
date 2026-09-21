package com.mylive.app.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMotionTest {

    @Test
    fun routeMotionUsesInterruptibleCriticallyDampedSpring() {
        val spec = AppMotion.routeSpec<Float>()

        assertEquals(AppMotion.DefaultDampingRatio, spec.dampingRatio, 0.001f)
        assertEquals(AppMotion.RouteSpringStiffness, spec.stiffness, 0.001f)
    }

    @Test
    fun momentumMotionKeepsAControlledAmountOfReleaseEnergy() {
        val spec = AppMotion.momentumSpec()

        assertEquals(AppMotion.MomentumDampingRatio, spec.dampingRatio, 0.001f)
        assertEquals(AppMotion.MomentumSpringStiffness, spec.stiffness, 0.001f)
        assertTrue(spec.dampingRatio < AppMotion.DefaultDampingRatio)
    }

    @Test
    fun reducedMotionRemovesLongSpatialSettling() {
        val normal = AppMotion.routeSpec<Float>(reducedMotion = false)
        val reduced = AppMotion.routeSpec<Float>(reducedMotion = true)

        assertTrue(reduced.stiffness > normal.stiffness)
        assertFalse(isReducedMotionScale(1f))
        assertTrue(isReducedMotionScale(0f))
    }

    @Test
    fun velocityProjectionPreservesDirectionAndMagnitude() {
        assertEquals(
            199f,
            AppMotion.projectedEndpoint(currentValue = 100f, velocity = 1_000f),
            0.01f
        )
        assertEquals(
            1f,
            AppMotion.projectedEndpoint(currentValue = 100f, velocity = -1_000f),
            0.01f
        )
    }

    @Test
    fun farPagerJumpUsesTargetAdjacentPage() {
        assertEquals(null, AppMotion.preJumpPageForTarget(currentPage = 0, targetPage = 1))
        assertEquals(null, AppMotion.preJumpPageForTarget(currentPage = 2, targetPage = 1))
        assertEquals(2, AppMotion.preJumpPageForTarget(currentPage = 0, targetPage = 3))
        assertEquals(1, AppMotion.preJumpPageForTarget(currentPage = 3, targetPage = 0))
    }

    @Test
    fun indexDirectionReportsForwardBackwardAndSamePage() {
        assertEquals(1, AppMotion.indexDirection(fromIndex = 0, toIndex = 2))
        assertEquals(-1, AppMotion.indexDirection(fromIndex = 3, toIndex = 1))
        assertEquals(0, AppMotion.indexDirection(fromIndex = 2, toIndex = 2))
    }
}
