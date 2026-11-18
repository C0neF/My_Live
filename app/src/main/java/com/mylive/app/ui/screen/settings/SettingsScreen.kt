package com.mylive.app.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylive.app.R
import com.mylive.app.ui.layout.AdaptivePageFrame
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.motion.AppMotion
import com.mylive.app.ui.motion.LocalAppMotionPolicy
import com.mylive.app.ui.motion.verticalContentTransform
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.theme.Icons

private enum class SettingsDestination(
    val key: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
    val icon: ImageVector,
    val route: Route,
    val group: String
) {
    Danmu(
        "danmu",
        R.string.settings_danmu,
        R.string.settings_danmu_subtitle,
        Icons.Default.Subtitles,
        Route.SettingsDanmu,
        "观看与播放"
    ),
    Play(
        "play",
        R.string.settings_play,
        R.string.settings_play_subtitle,
        Icons.Default.PlayCircleOutline,
        Route.SettingsPlay,
        "观看与播放"
    ),
    PlaybackPage(
        "playback_page",
        R.string.settings_playback_page,
        R.string.settings_playback_page_subtitle,
        Icons.Default.PlaylistAdd,
        Route.SettingsPlaybackPage,
        "观看与播放"
    ),
    Appearance(
        "appearance",
        R.string.settings_appearance,
        R.string.settings_appearance_subtitle,
        Icons.Default.Sun,
        Route.SettingsAppStyle,
        "界面与主页"
    ),
    Indexed(
        "indexed",
        R.string.settings_indexed,
        R.string.settings_indexed_subtitle,
        Icons.Default.Home,
        Route.SettingsIndexed,
        "界面与主页"
    ),
    Follow(
        "follow",
        R.string.settings_follow,
        R.string.settings_follow_subtitle,
        Icons.Default.FavoriteBorder,
        Route.SettingsFollow,
        "界面与主页"
    ),
    AutoExit(
        "auto_exit",
        R.string.settings_auto_exit,
        R.string.settings_auto_exit_subtitle,
        Icons.Default.Timer,
        Route.SettingsAutoExit,
        "系统与维护"
    ),
    Other(
        "other",
        R.string.settings_other,
        R.string.settings_other_subtitle,
        Icons.Default.Tune,
        Route.SettingsOther,
        "系统与维护"
    ),
    Update(
        "update",
        R.string.settings_update,
        R.string.settings_update_subtitle,
        Icons.Default.CloudDownload,
        Route.SettingsUpdate,
        "系统与维护"
    )
}

internal fun shouldUseSettingsMasterDetail(tablet: Boolean, widthDp: Int): Boolean =
    tablet && widthDp >= 820

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navigator: Navigator,
    embedded: Boolean = false,
    detailContentOverride: (@Composable (Route) -> Unit)? = null
) {
    val navigation = navigator.rememberRouteActions()
    val handleBack = navigation.onBack
    val appLayout = LocalAppLayout.current
    val grouped = remember { SettingsDestination.entries.groupBy { it.group } }
    var selectedKey by rememberSaveable { mutableStateOf(SettingsDestination.Danmu.key) }
    val selectedDestination = SettingsDestination.entries.firstOrNull { it.key == selectedKey }
        ?: SettingsDestination.Danmu
    val stateHolder = rememberSaveableStateHolder()

    Scaffold(
        containerColor = if (embedded) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    navigationIcon = {
                        IconButton(onClick = handleBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (embedded) Color.Transparent else MaterialTheme.colorScheme.background)
        ) {
            val useMasterDetail = shouldUseSettingsMasterDetail(
                tablet = appLayout.tablet,
                widthDp = maxWidth.value.toInt()
            )
            if (useMasterDetail) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (embedded) 12.dp else 16.dp, vertical = 10.dp)
                        .testTag("settings_master_detail"),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .width(268.dp)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (embedded) MaterialTheme.colorScheme.background
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            grouped.forEach { (groupName, items) ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = groupName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                    items.forEach { item ->
                                        SettingsMasterItem(
                                            destination = item,
                                            selected = item == selectedDestination,
                                            onClick = { selectedKey = item.key }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val motionPolicy = LocalAppMotionPolicy.current
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("settings_detail_pane"),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AnimatedContent(
                                    targetState = selectedDestination,
                                    transitionSpec = {
                                        val dir = AppMotion.indexDirection(initialState.ordinal, targetState.ordinal)
                                        verticalContentTransform(dir, motionPolicy)
                                    },
                                    label = "settingsMasterTitle"
                                ) { dest ->
                                    Text(
                                        text = stringResource(dest.titleRes),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            AnimatedContent(
                                targetState = selectedDestination,
                                transitionSpec = {
                                    val dir = AppMotion.indexDirection(initialState.ordinal, targetState.ordinal)
                                    verticalContentTransform(dir, motionPolicy)
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                label = "settingsMasterDetailContent"
                            ) { dest ->
                                Box(modifier = Modifier.fillMaxSize()) {
                                    stateHolder.SaveableStateProvider(dest.key) {
                                        if (detailContentOverride != null) {
                                            detailContentOverride(dest.route)
                                        } else {
                                            SettingsEmbeddedDetailContent(
                                                destination = dest,
                                                navigator = navigator
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                AdaptivePageFrame(Modifier.fillMaxSize(), appLayout.settingsMaxWidthDp) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        grouped.forEach { (groupName, items) ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = groupName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Column {
                                        items.forEachIndexed { index, item ->
                                            SettingsSectionRow(
                                                icon = item.icon,
                                                title = stringResource(item.titleRes),
                                                onClick = {
                                                    when (item) {
                                                        SettingsDestination.Danmu -> navigation.navigate(Route.SettingsDanmu)
                                                        SettingsDestination.Play -> navigation.navigate(Route.SettingsPlay)
                                                        SettingsDestination.PlaybackPage -> navigation.navigate(Route.SettingsPlaybackPage)
                                                        SettingsDestination.Appearance -> navigation.navigate(Route.SettingsAppStyle)
                                                        SettingsDestination.Indexed -> navigation.navigate(Route.SettingsIndexed)
                                                        SettingsDestination.Follow -> navigation.navigate(Route.SettingsFollow)
                                                        SettingsDestination.AutoExit -> navigation.navigate(Route.SettingsAutoExit)
                                                        SettingsDestination.Other -> navigation.navigate(Route.SettingsOther)
                                                        SettingsDestination.Update -> navigation.navigate(Route.SettingsUpdate)
                                                    }
                                                }
                                            )
                                            if (index < items.lastIndex) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(start = 50.dp),
                                                    thickness = 0.5.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsEmbeddedDetailContent(
    destination: SettingsDestination,
    navigator: Navigator
) {
    when (destination) {
        SettingsDestination.Danmu -> DanmuSettingsScreen(navigator = navigator, embedded = true)
        SettingsDestination.Play -> PlaySettingsScreen(navigator = navigator, embedded = true)
        SettingsDestination.PlaybackPage -> PlaybackPageSettingsScreen(navigator = navigator, embedded = true)
        SettingsDestination.Appearance -> AppearanceSettingsScreen(navigator = navigator, embedded = true)
        SettingsDestination.Indexed -> IndexedSettingsScreen(navigator = navigator, embedded = true)
        SettingsDestination.Follow -> FollowSettingsScreen(navigator = navigator, embedded = true)
        SettingsDestination.AutoExit -> AutoExitSettingsScreen(navigator = navigator, embedded = true)
        SettingsDestination.Other -> OtherSettingsScreen(navigator = navigator, embedded = true)
        SettingsDestination.Update -> AppUpdateScreen(navigator = navigator, embedded = true)
    }
}

@Composable
private fun SettingsMasterItem(
    destination: SettingsDestination,
    selected: Boolean,
    onClick: () -> Unit
) {
    val motionPolicy = LocalAppMotionPolicy.current
    val itemBg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        animationSpec = motionPolicy.effectsSpec(),
        label = "settingsMasterBg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motionPolicy.effectsSpec(),
        label = "settingsMasterTint"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(itemBg)
            .testTag("settings_nav_${destination.key}")
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(19.dp)
        )
        Text(
            text = stringResource(destination.titleRes),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 15.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SettingsSectionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(18.dp)
        )
    }
}
