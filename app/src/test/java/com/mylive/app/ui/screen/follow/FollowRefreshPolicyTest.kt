package com.mylive.app.ui.screen.follow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FollowRefreshPolicyTest {

    @Test
    fun followScreenKeepsPullRefreshWithoutHeaderRefreshAction() {
        val source = File("src/main/java/com/mylive/app/ui/screen/follow/FollowScreen.kt").readText()

        assertTrue(source.contains("PullToRefreshBox("))
        assertTrue(source.contains("onRefresh = { viewModel.updateFollowStatus() }"))
        assertFalse(source.contains("contentDescription = \"刷新关注\""))
    }
}
