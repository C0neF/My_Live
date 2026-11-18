package com.mylive.app.ui.screen.room

import com.mylive.app.testing.liveRoomUiSource

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveRoomQuickAccessPolicyTest {

    @Test
    fun disabledQuickAccessDoesNotExposeAnAction() {
        var invoked = false

        assertNull(liveRoomQuickAccessAction(enabled = false) { invoked = true })
        assertFalse(invoked)
    }

    @Test
    fun enabledQuickAccessExposesTheOriginalAction() {
        var invoked = false

        val action = liveRoomQuickAccessAction(enabled = true) { invoked = true }

        assertNotNull(action)
        action?.invoke()
        assertTrue(invoked)
    }

    @Test
    fun portraitAndLandscapeGateAllQuickAccessButtons() {
        val source = liveRoomUiSource()
        val portrait = source.substringAfter("internal fun PortraitLayout(")
            .substringBefore("private fun LiveRoomTabPage(")
        val landscape = source.substringAfter("internal fun LandscapeLayout(")
            .substringBefore("// ── Room Info Bar")
        val header = source.substringAfter("internal fun CompactPortraitRoomHeader(")
            .substringBefore("@Composable\nprivate fun")

        assertTrue(portrait.contains("liveRoomQuickAccessEnabled"))
        assertTrue(portrait.contains("liveRoomQuickAccessAction("))
        assertTrue(landscape.contains("liveRoomQuickAccessEnabled"))
        assertTrue(landscape.contains("liveRoomQuickAccessAction("))
        assertTrue(header.contains("onQuickAccessClick: (() -> Unit)?"))
        assertTrue(header.contains("if (onQuickAccessClick != null)"))
    }
}
