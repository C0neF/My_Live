package com.mylive.app.ui.screen.room

import com.mylive.app.ui.theme.Icons
import com.mylive.app.ui.theme.md_theme_dark_onSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveRoomPortraitToolbarPolicyTest {

    @Test
    fun toolbarIconsUseForegroundTintInDarkMode() {
        assertEquals(
            md_theme_dark_onSurface,
            resolveLiveRoomToolbarIconTint(md_theme_dark_onSurface)
        )
    }

    @Test
    fun portraitHeaderPlacesSettingsBetweenRefreshAndQuickAccess() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        val refreshIndex = source.indexOf("imageVector = Icons.Default.Refresh")
        val settingsIndex = source.indexOf("imageVector = Icons.Default.Settings")
        val quickAccessIndex = source.indexOf("imageVector = Icons.Default.PlaylistAdd")

        assertTrue(refreshIndex >= 0)
        assertTrue(settingsIndex > refreshIndex)
        assertTrue(quickAccessIndex > settingsIndex)
        assertTrue(source.contains("onSettingsClick = { activeAuxiliaryPanel = PortraitLiveRoomPanel.SETTINGS }"))
    }

    @Test
    fun portraitHeaderDoesNotRenderSecondaryActionRow() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        assertFalse(source.contains("PortraitSecondaryActionRow("))
    }

    @Test
    fun portraitHeaderOnlyDisplaysHostName() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val headerSource = source.substringAfter("private fun CompactPortraitRoomHeader(")
            .substringBefore("@OptIn(ExperimentalMaterial3Api::class)")

        assertFalse(headerSource.contains("detail.title"))
        assertFalse(headerSource.contains("onlineCount"))
        assertFalse(headerSource.contains(" 在线"))
    }

    @Test
    fun followButtonsUseCurrentPlatformAccentColor() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val headerSource = source.substringAfter("private fun CompactPortraitRoomHeader(")
            .substringBefore("@OptIn(ExperimentalMaterial3Api::class)")

        assertTrue(source.contains("accentColor = roomPlatformAccentColor"))
        assertTrue(source.contains("tint = accentColor"))
        assertTrue(headerSource.contains("accentColor = accentColor"))
        assertFalse(source.contains(".background(roomPlatformAccentColor)"))
        assertFalse(headerSource.contains(".background(accentColor)"))
        assertFalse(source.contains("tint = if (uiState.isFollowing) Color.Red else MaterialTheme.colorScheme.primary"))
        assertFalse(source.contains("tint = if (isFollowing) Color.Red else MaterialTheme.colorScheme.primary"))
    }

    @Test
    fun followButtonsUseHeartOutlineUntilFollowedThenFilledHeartOnly() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val followButtonSource = source.substringAfter("private fun LiveRoomFollowButton(")
            .substringBefore("@OptIn(ExperimentalMaterial3Api::class)")

        assertTrue(source.contains("LiveRoomFollowButton("))
        assertTrue(source.contains("isFollowing = uiState.isFollowing"))
        assertTrue(source.contains("isFollowing = isFollowing"))
        assertTrue(followButtonSource.contains("IconButton("))
        assertTrue(followButtonSource.contains("imageVector = if (isFollowing) Icons.Default.Favorite else Icons.Default.FavoriteBorder"))
        assertTrue(followButtonSource.contains("tint = accentColor"))
        assertFalse(followButtonSource.contains("OutlinedIconButton("))
        assertFalse(followButtonSource.contains("FilledIconButton("))
        assertFalse(followButtonSource.contains("BorderStroke("))
        assertFalse(followButtonSource.contains("containerColor = accentColor"))
    }

    @Test
    fun followButtonUsesStableComponentInsteadOfSwappingWithPlaceholder() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        assertFalse(source.contains("Spacer(modifier = Modifier.size(36.dp))"))
        assertTrue(source.contains("canToggleFollow = uiState.isFollowStatusKnown"))
        assertTrue(source.contains("canToggleFollow = detail != null && isFollowStatusKnown"))
        assertTrue(source.contains("enabled = canToggleFollow"))
    }

    @Test
    fun favoriteAndFavoriteBorderIconsAreVisuallyDistinct() {
        assertNotSame(Icons.Default.Favorite, Icons.Default.FavoriteBorder)
    }

    @Test
    fun roomAccentColorUsesRouteSiteIdBeforeViewModelUpdate() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        assertTrue(source.contains("val accentSiteId = resolveLiveRoomAccentSiteId("))
        assertTrue(source.contains("routeSiteId = key.siteId"))
        assertTrue(source.contains("accentSiteId = accentSiteId"))
        assertTrue(source.contains("siteId = accentSiteId"))
        assertFalse(source.contains("val roomPlatformAccentColor = resolveLiveRoomPlatformAccentColor(\n        siteId = viewModel.siteId"))
    }
}
