package com.mylive.app.ui.screen

import com.mylive.app.ui.navigation.Route
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IndexPresencePolicyTest {

    @Test
    fun tabContentComposedOnlyWhenIndexIsTopRoute() {
        assertTrue(shouldComposeIndexTabContent(null))
        assertTrue(shouldComposeIndexTabContent(Route.Index))
        assertFalse(
            shouldComposeIndexTabContent(
                Route.LiveRoomDetail(roomId = "1", siteId = "bilibili")
            )
        )
        assertFalse(shouldComposeIndexTabContent(Route.Search))
    }

    @Test
    fun indexScreenGatesTabCompositionWithPresencePolicy() {
        val source = File("src/main/java/com/mylive/app/ui/screen/IndexScreen.kt").readText()
        assertTrue(source.contains("shouldComposeIndexTabContent("))
        assertTrue(source.contains("composeIndexTabContent"))
    }
}
