package com.mylive.app.architecture

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Stops the source-text assertion habit from spreading.
 *
 * More than half of this suite reads files out of `src/main` and asserts on their *text*. Those
 * tests break on a line wrap and pass through real behaviour changes, and the cost is not
 * hypothetical: a formatting-only refactor in this area broke two of them while genuinely broken
 * behaviour would not have.
 *
 * Rather than rewrite seventy-odd files in one go, the existing ones are listed below and the
 * list is enforced in both directions:
 *
 * - a **new** test that reads `src/main` fails here, so the debt stops growing;
 * - a listed test that **stops** reading `src/main` also fails here, so the list shrinks as tests
 *   are migrated instead of rotting.
 *
 * When you migrate one, delete its line. When you are tempted to add one, extract the logic into
 * a pure function and assert on that instead — [com.mylive.app.ui.screen.room.player.shouldShowPlayerControls]
 * and [com.mylive.app.service.isExcludedSyncInterface] are examples of what that looks like.
 */
class SourceTextAssertionGuardTest {

    @Test
    fun noNewTestAssertsOnMainSourceText() {
        val current = testFilesReadingMainSource()

        val added = (current - KNOWN_SOURCE_TEXT_TESTS).toSortedSet()
        assertEquals(
            "New test(s) assert on src/main text. Extract a pure function and test behaviour " +
                "instead; if there is genuinely no alternative, add the file to " +
                "KNOWN_SOURCE_TEXT_TESTS with a comment saying why.",
            emptySet<String>(),
            added
        )
    }

    @Test
    fun theAllowlistShrinksAsTestsAreMigrated() {
        val current = testFilesReadingMainSource()

        val stale = (KNOWN_SOURCE_TEXT_TESTS - current).toSortedSet()
        assertEquals(
            "These no longer read src/main (nice) - remove them from KNOWN_SOURCE_TEXT_TESTS.",
            emptySet<String>(),
            stale
        )
    }

    private fun testFilesReadingMainSource(): Set<String> {
        val root = File("src/test/java")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains(MAIN_SOURCE_MARKER) }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .filterNot { it == SELF }
            .toSet()
    }

    private companion object {
        /** How a test reaches into production sources: `File("src/main/java/...")`. */
        const val MAIN_SOURCE_MARKER = "src/main"

        const val SELF = "com/mylive/app/architecture/SourceTextAssertionGuardTest.kt"

        val KNOWN_SOURCE_TEXT_TESTS: Set<String> = setOf(
        "com/mylive/app/ContributionRankRemovalPolicyTest.kt",
        "com/mylive/app/DeadSettingsRemovalPolicyTest.kt",
        "com/mylive/app/ImportPayloadSizePolicyTest.kt",
        "com/mylive/app/LiveRoomTabSortRemovalPolicyTest.kt",
        "com/mylive/app/RuntimeLoggingPolicyTest.kt",
        "com/mylive/app/core/common/EmojiAtlasResourcePolicyTest.kt",
        "com/mylive/app/core/common/RuntimeNetworkLogInterceptorTest.kt",
        "com/mylive/app/core/common/WebSocketConnectionPolicyTest.kt",
        "com/mylive/app/core/common/WebSocketUtilsTest.kt",
        "com/mylive/app/core/script/JsEngineStartupTest.kt",
        "com/mylive/app/core/site/douyu/DouyuSearchPolicyTest.kt",
        "com/mylive/app/core/site/huya/HuyaPlaybackPolicyTest.kt",
        "com/mylive/app/core/site/huya/tars/BaseTarsHttpTest.kt",
        "com/mylive/app/data/ProfileBackupPerformancePolicyTest.kt",
        "com/mylive/app/service/LanSyncSecurityPolicyTest.kt",
        "com/mylive/app/service/RemoteSyncServiceTest.kt",
        "com/mylive/app/ui/motion/MotionRegressionTest.kt",
        "com/mylive/app/ui/screen/BottomTabRefreshPolicyTest.kt",
        "com/mylive/app/ui/screen/IndexPresencePolicyTest.kt",
        "com/mylive/app/ui/screen/UiLifecycleCollectionPolicyTest.kt",
        "com/mylive/app/ui/screen/category/CategoryPlatformColorPolicyTest.kt",
        "com/mylive/app/ui/screen/category/CategoryRefreshPolicyTest.kt",
        "com/mylive/app/ui/screen/follow/FollowCompactLayoutPolicyTest.kt",
        "com/mylive/app/ui/screen/follow/FollowRefreshPolicyTest.kt",
        "com/mylive/app/ui/screen/home/HomeLoadingPolicyTest.kt",
        "com/mylive/app/ui/screen/home/HomeNavigationPolicyTest.kt",
        "com/mylive/app/ui/screen/home/HomePlatformInteractionPolicyTest.kt",
        "com/mylive/app/ui/screen/other/DebugLogScreenPolicyTest.kt",
        "com/mylive/app/ui/screen/room/ChatAutoScrollPolicyTest.kt",
        "com/mylive/app/ui/screen/room/DanmuDelayPolicyTest.kt",
        "com/mylive/app/ui/screen/room/LiveMessageShieldPolicyTest.kt",
        "com/mylive/app/ui/screen/room/LiveRoomAutoFullscreenPolicyTest.kt",
        "com/mylive/app/ui/screen/room/LiveRoomComposeStateIsolationPolicyTest.kt",
        "com/mylive/app/ui/screen/room/LiveRoomFollowPanelPolicyTest.kt",
        "com/mylive/app/ui/screen/room/LiveRoomLifecyclePolicyTest.kt",
        "com/mylive/app/ui/screen/room/LiveRoomPlayerSuperChatPolicyTest.kt",
        "com/mylive/app/ui/screen/room/LiveRoomPortraitToolbarPolicyTest.kt",
        "com/mylive/app/ui/screen/room/LiveRoomQualityPolicyTest.kt",
        "com/mylive/app/ui/screen/room/LiveRoomQuickAccessPolicyTest.kt",
        "com/mylive/app/ui/screen/room/LiveRoomRoutePolicyTest.kt",
        "com/mylive/app/ui/screen/room/LiveRoomSettingsCollectionPolicyTest.kt",
        "com/mylive/app/ui/screen/room/LiveRoomSiteBoundaryPolicyTest.kt",
        "com/mylive/app/ui/screen/room/RoomSettingsPanelPolicyTest.kt",
        "com/mylive/app/ui/screen/room/player/DanmakuConfigPolicyTest.kt",
        "com/mylive/app/ui/screen/room/player/DanmakuSurfaceViewPolicyTest.kt",
        "com/mylive/app/ui/screen/room/player/FixedDanmakuPolicyTest.kt",
        "com/mylive/app/ui/screen/room/player/LiveDanmakuSurfaceFeedPolicyTest.kt",
        "com/mylive/app/ui/screen/room/player/LivePlaybackEnginePortTest.kt",
        "com/mylive/app/ui/screen/room/player/LivePlaybackHostSignalsTest.kt",
        "com/mylive/app/ui/screen/room/player/LivePlaybackPipPolicyTest.kt",
        "com/mylive/app/ui/screen/room/player/LivePlaybackSessionPolicyTest.kt",
        "com/mylive/app/ui/screen/room/player/PlaybackNetworkSecurityPolicyTest.kt",
        "com/mylive/app/ui/screen/room/player/PlayerControllerPolicyTest.kt",
        "com/mylive/app/ui/screen/room/player/PlayerDanmakuSettingsPolicyTest.kt",
        "com/mylive/app/ui/screen/room/player/PlayerErrorPolicyTest.kt",
        "com/mylive/app/ui/screen/room/player/PlayerGestureIndicatorPolicyTest.kt",
        "com/mylive/app/ui/screen/room/player/PlayerSurfaceReleasePolicyTest.kt",
        "com/mylive/app/ui/screen/room/quickaccess/QuickAccessPanelLayoutPolicyTest.kt",
        "com/mylive/app/ui/screen/search/SearchScreenPolicyTest.kt",
        "com/mylive/app/ui/screen/search/SearchViewModelPolicyTest.kt",
        "com/mylive/app/ui/screen/settings/DanmuSettingsScreenPolicyTest.kt",
        "com/mylive/app/ui/screen/settings/PlaySettingsScreenPolicyTest.kt",
        "com/mylive/app/ui/screen/settings/SettingsSemanticsPolicyTest.kt",
        "com/mylive/app/ui/screen/settings/ShieldSettingsScreenPolicyTest.kt",
        "com/mylive/app/ui/screen/settings/WebViewLifecyclePolicyTest.kt",
        "com/mylive/app/ui/screen/sync/RemoteSyncFavoritePolicyTest.kt",
        "com/mylive/app/ui/screen/sync/SyncScanPortraitPolicyTest.kt",
        "com/mylive/app/ui/screen/sync/SyncVersionPolicyTest.kt",
        "com/mylive/app/update/AppUpdatePolicyTest.kt",
        )
    }
}
