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
        val headerSource = source.substringAfter("private fun CompactPortraitRoomHeader(")
            .substringBefore("@OptIn(ExperimentalMaterial3Api::class)")

        val refreshIndex = headerSource.indexOf("imageVector = Icons.Default.Refresh")
        val settingsIndex = headerSource.indexOf("imageVector = Icons.Default.Settings")
        val quickAccessIndex = headerSource.indexOf("imageVector = Icons.Default.PlaylistAdd")

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

    @Test
    fun landscapeSidePanelShowsOnlyIdentityAndChat() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val landscapeSource = source.substringAfter("private fun LandscapeLayout(")
            .substringBefore("// ── Room Info Bar")

        val headerIndex = landscapeSource.indexOf("LandscapeRoomSidePanelHeader(")
        val chatPanelIndex = landscapeSource.indexOf("ChatPanel(")

        assertEquals(listOf(LiveRoomTabType.CHAT), resolveLandscapeLiveRoomTabs())
        assertTrue(headerIndex >= 0)
        assertTrue(chatPanelIndex > headerIndex)
        assertTrue(landscapeSource.contains("val roomTabs = remember { resolveLandscapeLiveRoomTabs() }"))
        assertFalse(landscapeSource.contains("TabRow("))
        assertFalse(landscapeSource.contains("HorizontalPager("))
        assertFalse(landscapeSource.contains("LiveRoomTabType.FOLLOW"))
        assertFalse(landscapeSource.contains("LiveRoomTabType.SETTINGS"))
    }

    @Test
    fun landscapeRoomActionHeaderShowsOnlyHostIdentity() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val headerStart = source.indexOf("private fun LandscapeRoomSidePanelHeader(")

        assertTrue(headerStart >= 0)
        val headerSource = source.substring(headerStart)
            .substringBefore("@Composable\nprivate fun CompactPortraitRoomHeader")
        assertTrue(headerSource.contains("AsyncImage("))
        assertTrue(headerSource.contains("model = detail.userAvatar"))
        assertTrue(headerSource.contains("text = detail.userName"))
        assertFalse(headerSource.contains("LiveRoomFollowButton("))
        assertFalse(headerSource.contains("imageVector = Icons.Default.Settings"))
        assertFalse(headerSource.contains("imageVector = Icons.Default.PlaylistAdd"))
        assertFalse(headerSource.contains("canToggleFollow = detail != null && isFollowStatusKnown"))
        assertFalse(headerSource.contains("imageVector = Icons.Default.Refresh"))
        assertFalse(headerSource.contains("onRefreshClick"))
        assertFalse(headerSource.contains("detail.title"))
    }

    @Test
    fun landscapePlayerOwnsFollowAndQuickAccessActions() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val landscapeSource = source.substringAfter("private fun LandscapeLayout(")
            .substringBefore("// ── Room Info Bar")

        assertTrue(landscapeSource.contains("extraTabs = landscapeQuickAccessExtraTabs("))
        assertTrue(landscapeSource.contains("quickAccessInitialTabKey = \"follow\""))
        assertTrue(landscapeSource.contains("val liveRoomQuickAccessEnabled by settingsViewModel.liveRoomQuickAccessEnabled.collectAsState()"))
        assertTrue(landscapeSource.contains("val quickAccessAction = liveRoomQuickAccessAction("))
        assertTrue(landscapeSource.contains("onQuickAccessClick = quickAccessAction"))
        assertTrue(landscapeSource.contains("onFollowClick = { viewModel.toggleFollow() }"))
        assertTrue(landscapeSource.contains("isFollowing = uiState.isFollowing"))
        assertFalse(landscapeSource.contains("quickAccessInitialTabKey = \"room_settings\""))
        assertFalse(landscapeSource.contains("onRoomSettingsClick = {"))
    }

    @Test
    fun landscapeSidePanelStartsHiddenAndRightToLeftSwipeOpens() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val landscapeSource = source.substringAfter("private fun LandscapeLayout(")
            .substringBefore("// ── Room Info Bar")

        assertTrue(source.contains("internal fun liveRoomInitialSidePanelOffsetPx(panelWidthPx: Float): Float"))
        assertTrue(source.contains("internal fun liveRoomSidePanelOffsetAfterDrag("))
        assertTrue(landscapeSource.contains("Animatable(liveRoomInitialSidePanelOffsetPx(panelWidthPx))"))
        assertTrue(landscapeSource.contains("val showSidePanel = panelOffset.value < panelWidthPx"))
        assertTrue(landscapeSource.contains("BackHandler(enabled = showSidePanel)"))
        assertTrue(landscapeSource.contains("liveRoomSidePanelOffsetAfterDrag("))
        assertFalse(source.contains("liveRoomUsePersistentSidePanel"))
        assertFalse(landscapeSource.contains("usePersistentSidePanel"))
    }

    @Test
    fun rightToLeftSwipeOpensSidePanelAndLeftToRightSwipeClosesIt() {
        val panelWidthPx = 320f

        assertEquals(panelWidthPx, liveRoomInitialSidePanelOffsetPx(panelWidthPx))
        assertEquals(
            200f,
            liveRoomSidePanelOffsetAfterDrag(
                currentOffsetPx = panelWidthPx,
                deltaX = -120f,
                panelWidthPx = panelWidthPx
            )
        )
        assertEquals(
            panelWidthPx,
            liveRoomSidePanelOffsetAfterDrag(
                currentOffsetPx = 120f,
                deltaX = 240f,
                panelWidthPx = panelWidthPx
            )
        )
    }

    @Test
    fun landscapePlayerDoesNotExposeFullscreenButton() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val landscapeSource = source.substringAfter("private fun LandscapeLayout(")
            .substringBefore("// ── Room Info Bar")

        assertFalse(landscapeSource.contains("isFullscreenOverride = liveRoomControlsFullscreen(showSidePanel)"))
        assertFalse(landscapeSource.contains("onFullscreenClick = {"))
        assertFalse(landscapeSource.contains("liveRoomSidePanelTargetOffsetForFullscreenClick("))
    }
}
