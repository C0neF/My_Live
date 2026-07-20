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
    fun followViewModelUsesSharedSingleFlightRefreshCoordinator() {
        val viewModel = File("src/main/java/com/mylive/app/ui/screen/follow/FollowViewModel.kt").readText()
        val coordinator = File(
            "src/main/java/com/mylive/app/service/FollowStatusRefreshCoordinator.kt"
        ).readText()

        assertTrue(viewModel.contains("FollowStatusRefreshCoordinator"))
        assertTrue(viewModel.contains("followStatusRefreshCoordinator.refreshAll()"))
        assertFalse(viewModel.contains("private var updateJob: Job?"))
        assertTrue(coordinator.contains("SuspendSingleFlight"))
        assertTrue(coordinator.contains("inFlight"))
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
