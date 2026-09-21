package com.mylive.app.ui.screen.room

import com.mylive.app.ui.motion.AppMotion
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomEntryLatencyPolicyTest {

    @Test
    fun routeMotionIsPhysicsBasedInsteadOfDurationBased() {
        val routeSpring = AppMotion.routeSpec<Float>()

        assertEquals(
            AppMotion.RouteSpringStiffness,
            routeSpring.stiffness,
            0.001f
        )
        assertEquals(
            AppMotion.DefaultDampingRatio,
            routeSpring.dampingRatio,
            0.001f
        )
    }
}
