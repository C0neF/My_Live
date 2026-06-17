package com.mylive.app.ui.screen.room

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveRoomRoutePolicyTest {

    @Test
    fun liveRoomScreenPassesNavigation3RouteKeyToViewModel() {
        val source = File(
            "src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt"
        ).readText()

        assertTrue(
            "Navigation3 route key must be explicitly applied to LiveRoomViewModel",
            source.contains("viewModel.openRoute(key.roomId, key.siteId, key.initialIsFollowing)")
        )
    }

    @Test
    fun liveRoomViewModelReportsPlaybackSetupFailuresToPlayer() {
        val source = File(
            "src/main/java/com/mylive/app/ui/screen/room/LiveRoomViewModel.kt"
        ).readText()

        assertTrue(
            "empty quality list must not leave PlayerState.isLoading at its initial value",
            source.contains("暂无可播放画质")
        )
        assertTrue(
            "play URL fetch failures must be surfaced to PlayerController",
            source.contains("playerController?.showError")
        )
    }

    @Test
    fun liveRoomRecoversPlaybackByRequestingFreshSourceFromViewModel() {
        val screenSource = File(
            "src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt"
        ).readText()
        val viewModelSource = File(
            "src/main/java/com/mylive/app/ui/screen/room/LiveRoomViewModel.kt"
        ).readText()

        assertTrue(screenSource.contains("onPlaybackSourceExhausted = {"))
        assertTrue(screenSource.contains("viewModel.recoverPlaybackAfterSourceFailure()"))
        assertTrue(screenSource.contains("viewModel.playerController = null"))
        assertTrue(viewModelSource.contains("fun recoverPlaybackAfterSourceFailure()"))
        assertTrue(viewModelSource.contains("resetSourceRefreshAttempt = false"))
    }

    @Test
    fun liveRoomViewModelIgnoresStaleRouteCallbacks() {
        val source = File(
            "src/main/java/com/mylive/app/ui/screen/room/LiveRoomViewModel.kt"
        ).readText()

        assertTrue(
            "room detail loading must be bound to the route that started it",
            source.contains("private suspend fun loadRoomDetail(route:")
        )
        assertTrue(
            "play quality loading must ignore callbacks from a previous route",
            source.contains("private suspend fun loadPlayQualities(detail: LiveRoomDetail, route:")
        )
        assertTrue(
            "play URL loading must ignore callbacks from a previous route",
            source.contains("private suspend fun playWithQuality(") &&
                source.contains("route: Pair<String, String>")
        )
        assertTrue(
            "danmaku startup must ignore callbacks from a previous route",
            source.contains("private fun startDanmaku(detail: LiveRoomDetail, route:")
        )
        assertTrue(
            "all async room callbacks need a shared active route check",
            source.contains("private fun isActiveRoute(route:")
        )
    }

    @Test
    fun liveRoomViewModelDoesNotHideTargetSiteFailuresAsRoomMissing() {
        val source = File(
            "src/main/java/com/mylive/app/ui/screen/room/LiveRoomViewModel.kt"
        ).readText()

        assertTrue(
            "when a route specifies siteId, target-site errors must be preserved instead of becoming 未找到直播间",
            source.contains("targetSiteError")
        )
        assertTrue(
            "target-site errors should be rethrown so the UI can show the real loading failure",
            source.contains("throw targetSiteError")
        )
    }

    @Test
    fun liveRoomScreenDoesNotDisplayStaleFollowStateForNewRouteBeforeViewModelCatchesUp() {
        val staleFollowedState = LiveRoomUiState(isLoading = false, isFollowing = true)

        val displayState = liveRoomUiStateForRoute(
            uiState = staleFollowedState,
            viewModelRoomId = "old-room",
            viewModelSiteId = "douyu",
            routeRoomId = "new-room",
            routeSiteId = "douyu"
        )

        assertTrue(displayState.isLoading)
        assertFalse(displayState.isFollowing)
    }

    @Test
    fun liveRoomScreenUsesRouteInitialFollowStateBeforeViewModelCatchesUp() {
        val staleUnfollowedState = LiveRoomUiState(isLoading = false, isFollowing = false)

        val displayState = liveRoomUiStateForRoute(
            uiState = staleUnfollowedState,
            viewModelRoomId = "old-room",
            viewModelSiteId = "douyu",
            routeRoomId = "followed-room",
            routeSiteId = "douyu",
            routeInitialIsFollowing = true
        )

        assertTrue(displayState.isLoading)
        assertTrue(displayState.isFollowing)
        assertTrue(displayState.isFollowStatusKnown)
    }

    @Test
    fun liveRoomFollowButtonCanShowBeforeRoomDetailWhenFollowStateIsKnown() {
        assertTrue(
            canShowLiveRoomFollowButton(
                hasDetail = false,
                isFollowStatusKnown = true
            )
        )
    }

    @Test
    fun liveRoomFollowStateSubscribesToRepositoryChangesAfterSiteResolves() {
        val viewModelSource = File(
            "src/main/java/com/mylive/app/ui/screen/room/LiveRoomViewModel.kt"
        ).readText()
        val repositorySource = File(
            "src/main/java/com/mylive/app/data/repository/FollowRepository.kt"
        ).readText()
        val daoSource = File(
            "src/main/java/com/mylive/app/data/local/dao/FollowUserDao.kt"
        ).readText()

        assertTrue(
            "repository must expose a follow-state Flow instead of only a one-shot isFollowing read",
            repositorySource.contains("fun observeFollowing(siteId: String, roomId: String): Flow<Boolean>")
        )
        assertTrue(
            "DAO must expose a Flow query for the current room follow row",
            daoSource.contains("fun observeBySiteAndRoom(siteId: String, roomId: String): Flow<FollowUserEntity?>")
        )
        assertTrue(
            "room view model must cancel stale follow subscriptions on route changes",
            viewModelSource.contains("followStatusJob?.cancel()")
        )
        assertTrue(
            "room view model must start follow-state subscription after the actual site is known",
            viewModelSource.contains("observeCurrentRoomFollowStatus(site.id, routeRoomId, route)")
        )
        assertTrue(
            "room view model should start route follow-state subscription before delayed room detail loading when siteId is known",
            viewModelSource.contains("observeCurrentRoomFollowStatus(nextSiteId, nextRoomId, nextRoute)")
        )
        assertTrue(
            "follow-state subscription must update the visible room UI state",
            viewModelSource.contains("followRepository.observeFollowing(siteId, roomId)")
        )
        assertTrue(
            "follow-state subscription should publish a one-shot local value before collecting changes",
            viewModelSource.contains("val initialFollowing = followRepository.isFollowing(siteId, roomId)")
        )
    }

    @Test
    fun liveRoomPublishesDetailWithInitialFollowStateAlreadyResolved() {
        val viewModelSource = File(
            "src/main/java/com/mylive/app/ui/screen/room/LiveRoomViewModel.kt"
        ).readText()

        val initialFollowReadIndex = viewModelSource.indexOf(
            "val isFollowing = followRepository.isFollowing(site.id, routeRoomId)"
        )
        val publishDetailIndex = viewModelSource.indexOf(
            "_uiState.value = _uiState.value.copy(\n                isLoading = false,\n                detail = detail"
        )

        assertTrue(initialFollowReadIndex >= 0)
        assertTrue(publishDetailIndex > initialFollowReadIndex)
        assertTrue(viewModelSource.contains("isFollowStatusKnown = true"))
        assertTrue(viewModelSource.contains("loadingLiveRoomUiState(initialIsFollowing)"))
        assertTrue(viewModelSource.contains("_uiState.update { it.asLoadingStatePreservingFollow() }"))
    }

    @Test
    fun knownFollowEntrancesPassInitialFollowStateToRoomRoute() {
        val routeSource = File(
            "src/main/java/com/mylive/app/ui/navigation/Route.kt"
        ).readText()
        val navigatorSource = File(
            "src/main/java/com/mylive/app/ui/navigation/AppNavigator.kt"
        ).readText()
        val followSource = File(
            "src/main/java/com/mylive/app/ui/screen/follow/FollowScreen.kt"
        ).readText()
        val quickAccessSource = File(
            "src/main/java/com/mylive/app/ui/screen/room/quickaccess/QuickAccessPanel.kt"
        ).readText()
        val roomScreenSource = File(
            "src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt"
        ).readText()

        assertTrue(routeSource.contains("val initialIsFollowing: Boolean? = null"))
        assertTrue(navigatorSource.contains("initialIsFollowing: Boolean? = null"))
        assertTrue(followSource.contains("initialIsFollowing = true"))
        assertTrue(quickAccessSource.contains("onNavigateToRoom(user.siteId, user.roomId, true)"))
        assertTrue(roomScreenSource.contains("initialIsFollowing = true"))
    }
}
