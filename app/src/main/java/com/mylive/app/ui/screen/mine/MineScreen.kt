package com.mylive.app.ui.screen.mine

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylive.app.R
import com.mylive.app.ui.layout.AdaptivePageFrame
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.screen.other.HistoryScreen
import com.mylive.app.ui.screen.settings.AccountScreen
import com.mylive.app.ui.screen.settings.SettingsScreen
import com.mylive.app.ui.screen.sync.SyncHubScreen
import com.mylive.app.ui.theme.Icons

private enum class MineDestination(
    val key: String,
    val testTag: String,
    @param:StringRes val titleRes: Int,
    val icon: ImageVector,
    val route: Route
) {
    History("history", "mine_history", R.string.mine_history, Icons.Default.History, Route.History),
    Account("account", "mine_account", R.string.mine_account, Icons.Default.Person, Route.SettingsAccount),
    Sync("sync", "mine_sync", R.string.mine_sync, Icons.Default.Sync, Route.Sync),
    Tools("tools", "mine_tools", R.string.parse_title, Icons.Default.Link, Route.Tools),
    Settings("settings", "mine_settings", R.string.mine_settings, Icons.Default.Settings, Route.Settings)
}

@Composable
fun MineScreen(
    navigator: Navigator,
    contentBottomPadding: Dp = 96.dp,
    detailContentOverride: (@Composable (Route) -> Unit)? = null
) {
    val appLayout = LocalAppLayout.current
    var selectedSectionKey by rememberSaveable { mutableStateOf(MineDestination.History.key) }
    val selectedDestination = MineDestination.entries.firstOrNull { it.key == selectedSectionKey }
        ?: MineDestination.History
    val stateHolder = rememberSaveableStateHolder()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useMasterDetail = appLayout.tablet && maxWidth >= 820.dp
        if (useMasterDetail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = contentBottomPadding)
                    .testTag("mine_master_detail"),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .width(256.dp)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.nav_mine),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            MineDestination.entries.forEach { item ->
                                val selected = item == selectedDestination
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.surfaceVariant
                                            else Color.Transparent
                                        )
                                        .testTag(item.testTag)
                                        .clickable { selectedSectionKey = item.key }
                                        .padding(horizontal = 12.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Text(
                                        text = stringResource(item.titleRes),
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
                        }

                        Text(
                            text = stringResource(R.string.mine_version),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("mine_detail_pane"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (selectedDestination != MineDestination.Settings) {
                            val showSyncScan = selectedDestination == MineDestination.Sync
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = if (showSyncScan) 2.dp else 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(selectedDestination.titleRes),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "全屏打开",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { navigator.navigate(selectedDestination.route) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                if (showSyncScan) {
                                    TextButton(onClick = { navigator.navigate(Route.SyncScan) }) {
                                        Icon(
                                            imageVector = Icons.Default.QrCodeScanner,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.sync_scan_pair))
                                    }
                                }
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            stateHolder.SaveableStateProvider(selectedDestination.key) {
                                if (detailContentOverride != null) {
                                    detailContentOverride(selectedDestination.route)
                                } else {
                                    when (selectedDestination) {
                                        MineDestination.History -> HistoryScreen(navigator = navigator, embedded = true)
                                        MineDestination.Account -> AccountScreen(navigator = navigator, embedded = true)
                                        MineDestination.Sync -> SyncHubScreen(navigator = navigator, embedded = true)
                                        MineDestination.Tools -> ParseScreen(navigator = navigator, embedded = true)
                                        MineDestination.Settings -> SettingsScreen(navigator = navigator, embedded = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Phone & Compact: Clean iOS Settings Inset Grouped List
            val mainGroup = listOf(
                MineDestination.History,
                MineDestination.Account,
                MineDestination.Sync,
                MineDestination.Tools
            )
            AdaptivePageFrame(maxWidthDp = appLayout.settingsMaxWidthDp) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = contentBottomPadding
                        ),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.nav_mine),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.4).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column {
                            mainGroup.forEachIndexed { index, item ->
                                MineGroupedRow(
                                    item = item,
                                    onClick = { navigator.navigate(item.route) }
                                )
                                if (index < mainGroup.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 50.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        MineGroupedRow(
                            item = MineDestination.Settings,
                            onClick = { navigator.navigate(MineDestination.Settings.route) }
                        )
                    }

                    Text(
                        text = stringResource(R.string.mine_version),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MineGroupedRow(
    item: MineDestination,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(item.testTag)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = stringResource(item.titleRes),
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
