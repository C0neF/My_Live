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

    @Test
    fun followScreenShowsSharedBackToTopButtonThatRevealsBottomBar() {
        val source = File("src/main/java/com/mylive/app/ui/screen/follow/FollowScreen.kt").readText()

        assertTrue(source.contains("BackToTopButton("))
        assertTrue(source.contains("onRevealBottomBar()"))
    }

    @Test
    fun followViewModelSkipsConcurrentStatusRefreshes() {
        val source = File("src/main/java/com/mylive/app/ui/screen/follow/FollowViewModel.kt").readText()

        assertTrue(source.contains("private var updateJob: Job? = null"))
        assertTrue(source.contains("if (updateJob?.isActive == true) return"))
        assertTrue(source.contains("updateJob = viewModelScope.launch"))
    }

    @Test
    fun followViewModelDerivedFlowsReuseSharedRoomSubscriptions() {
        val source = File("src/main/java/com/mylive/app/ui/screen/follow/FollowViewModel.kt").readText()
        val normalized = source.replace(Regex("\\s+"), " ")

        assertTrue(normalized.contains("combine( groupMode, follows, userTags, settingsRepository.siteSort )"))
        assertTrue(normalized.contains("combine( follows, groupMode, selectedGroupId, groupOptions, userTags )"))
    }

    @Test
    fun followViewModelTagEditsDoNotReadFollowsInsideUserLoops() {
        val source = File("src/main/java/com/mylive/app/ui/screen/follow/FollowViewModel.kt").readText()

        assertTrue(source.contains("val followsById = followRepository.getAllFollows().first().associateBy { it.id }"))
        assertFalse(source.contains("for (userId in tag.userIds) {\n                val follow = followRepository.getAllFollows().first()"))
    }
}
