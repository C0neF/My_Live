package com.mylive.app.ui.screen.room.player

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayerDanmakuSettingsPolicyTest {

    @Test
    fun playerViewPropagatesAdvancedDanmakuSettingsToController() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()

        assertTrue(source.contains("danmuLineCount: Int = 8"))
        assertTrue(source.contains("danmuDelayMs: Int = 0"))
        assertTrue(source.contains("danmuTopMargin: Double = 0.0"))
        assertTrue(source.contains("danmuBottomMargin: Double = 0.0"))
        assertTrue(source.contains("danmuHideTop: Boolean = false"))
        assertTrue(source.contains("danmuHideBottom: Boolean = false"))
        assertTrue(source.contains("danmuDedupeStep: Int = 2"))
        // Settings are applied through DanmakuConfig (single write surface).
        assertTrue(source.contains("applyDanmakuConfig("))
        assertTrue(source.contains("DanmakuConfig("))
        assertTrue(source.contains("lineCount = danmuLineCount"))
        assertTrue(source.contains("delayMs = danmuDelayMs"))
        assertTrue(source.contains("topMargin = danmuTopMargin"))
        assertTrue(source.contains("bottomMargin = danmuBottomMargin"))
        assertTrue(source.contains("hideTop = danmuHideTop"))
        assertTrue(source.contains("hideBottom = danmuHideBottom"))
        assertTrue(source.contains("dedupeStep = danmuDedupeStep"))
    }

    @Test
    fun danmakuSettingsBottomSheetKeepsTitleAndDividerOutsideScrollableContent() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()
        val bottomSheetSource = source.substringAfter("private fun DanmakuSettingsBottomSheet(")
            .substringBefore("private fun updateControllerConfig(")

        val titleIndex = bottomSheetSource.indexOf("text = \"弹幕快捷设置\"")
        val dividerIndex = bottomSheetSource.indexOf("HorizontalDivider()")
        val scrollIndex = bottomSheetSource.indexOf(".verticalScroll(rememberScrollState())")
        val settingsIndex = bottomSheetSource.indexOf("// Size Selector")

        assertTrue(titleIndex >= 0)
        assertTrue(dividerIndex > titleIndex)
        assertTrue(scrollIndex > dividerIndex)
        assertTrue(settingsIndex > scrollIndex)
    }

    @Test
    fun playerDanmuButtonUsesAccentColorOnlyWhenEnabled() {
        val accentColor = Color(0xFFFF5D23)

        assertEquals(accentColor, resolvePlayerDanmuButtonTint(danmuEnable = true, accentColor = accentColor))
        assertEquals(Color.White, resolvePlayerDanmuButtonTint(danmuEnable = false, accentColor = accentColor))
    }

    @Test
    fun playerViewPropagatesAccentColorToDanmuButton() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()

        assertTrue(source.contains("accentColor: Color? = null"))
        assertTrue(source.contains("val resolvedAccentColor = accentColor ?: MaterialTheme.colorScheme.primary"))
        assertTrue(source.contains("accentColor = resolvedAccentColor"))
        assertTrue(source.contains("resolvePlayerDanmuButtonTint("))
        assertTrue(source.contains("IconButton(onClick = onDanmuToggle"))
        assertTrue(!source.contains("resolvePlayerDanmuButtonContainerColor("))
    }

    @Test
    fun danmuQuickSettingsButtonOnlyShowsInPortraitPlayer() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()

        assertTrue(shouldShowPlayerDanmuSettingsButton(isPortrait = true))
        assertFalse(shouldShowPlayerDanmuSettingsButton(isPortrait = false))
        assertTrue(source.contains("if (shouldShowPlayerDanmuSettingsButton(isPortrait))"))
    }

    @Test
    fun playerBottomBarAcceptsRoomActionsForOverlay() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()
        val bottomBarSource = source.substringAfter("private fun PlayerBottomBar(")
            .substringBefore("internal fun resolvePlayerDanmuButtonTint(")

        assertTrue(source.contains("onRoomSettingsClick: (() -> Unit)? = null"))
        assertTrue(source.contains("onQuickAccessClick: (() -> Unit)? = null"))
        assertTrue(source.contains("onFollowClick: (() -> Unit)? = null"))
        assertTrue(source.contains("isFollowing: Boolean = false"))
        assertTrue(bottomBarSource.contains("contentDescription = \"快速入口\""))
        assertTrue(bottomBarSource.contains("contentDescription = if (isFollowing) \"已关注\" else \"关注\""))
    }

    @Test
    fun playerBottomBarHidesSettingsAndFullscreenActionsOutsidePortrait() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()
        val bottomBarSource = source.substringAfter("private fun PlayerBottomBar(")
            .substringBefore("internal fun shouldUseCompactPlayerBottomBar(")

        assertTrue(source.contains("internal fun shouldShowPlayerRoomSettingsAction(isPortrait: Boolean): Boolean"))
        assertTrue(source.contains("internal fun shouldShowPlayerFullscreenAction(isPortrait: Boolean): Boolean"))
        val roomSettingsPolicySource = source.substringAfter("internal fun shouldShowPlayerRoomSettingsAction(")
            .substringBefore("internal fun shouldShowPlayerFullscreenAction(")
        val fullscreenPolicySource = source.substringAfter("internal fun shouldShowPlayerFullscreenAction(")
            .substringBefore("internal fun shouldShowPlayerDanmuSettingsButton(")
        assertTrue(roomSettingsPolicySource.contains("return isPortrait"))
        assertTrue(fullscreenPolicySource.contains("return isPortrait"))
        assertTrue(bottomBarSource.contains("val showRoomSettingsAction = shouldShowPlayerRoomSettingsAction(isPortrait)"))
        assertTrue(bottomBarSource.contains("val showFullscreenAction = shouldShowPlayerFullscreenAction(isPortrait)"))
        assertTrue(bottomBarSource.contains("if (!compact && showRoomSettingsAction && onRoomSettingsClick != null)"))
        assertTrue(bottomBarSource.contains("if (showFullscreenAction)"))
    }

    @Test
    fun playerBottomBarMovesFollowActionToRightOutsidePortrait() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()
        val bottomBarSource = source.substringAfter("private fun PlayerBottomBar(")
            .substringBefore("internal fun shouldUseCompactPlayerBottomBar(")

        assertTrue(source.contains("internal fun shouldPlacePlayerFollowActionOnRight(isPortrait: Boolean): Boolean"))
        assertTrue(source.contains("return !isPortrait"))
        assertTrue(bottomBarSource.contains("val placeFollowActionOnRight = shouldPlacePlayerFollowActionOnRight(isPortrait)"))

        val leftFollowIndex = bottomBarSource.indexOf("if (!placeFollowActionOnRight && onFollowClick != null)")
        val spacerIndex = bottomBarSource.indexOf("Spacer(modifier = Modifier.weight(1f))")
        val rightFollowIndex = bottomBarSource.indexOf("if (placeFollowActionOnRight && onFollowClick != null)")

        assertTrue(leftFollowIndex >= 0)
        assertTrue(spacerIndex > leftFollowIndex)
        assertTrue(rightFollowIndex > spacerIndex)
    }

    @Test
    fun playerBottomBarUsesCompactOverflowMenuOnNarrowLandscapeWidths() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()
        val bottomBarSource = source.substringAfter("private fun PlayerBottomBar(")
            .substringBefore("internal fun shouldUseCompactPlayerBottomBar(")

        assertTrue(source.contains("internal fun shouldUseCompactPlayerBottomBar("))
        assertTrue(source.contains("availableWidthDp < 480"))
        assertTrue(source.contains("!isPortrait"))
        assertTrue(bottomBarSource.contains("BoxWithConstraints"))
        assertTrue(bottomBarSource.contains("DropdownMenu"))
        assertTrue(bottomBarSource.contains("shouldUseCompactPlayerBottomBar("))
    }
}
