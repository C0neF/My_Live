package com.mylive.app.ui.screen.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.R
import com.mylive.app.core.common.RemoteSyncCrypto
import com.mylive.app.ui.layout.AdaptivePageFrame
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.layout.pageContentWindowInsets
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.theme.Icons
import com.mylive.app.ui.theme.LiveSuccessGreen

private data class SyncActionEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncHubScreen(
    navigator: Navigator,
    viewModel: SyncHubViewModel = hiltViewModel(),
    embedded: Boolean = false
) {
    val navigation = navigator.rememberRouteActions()
    val serverHealth by viewModel.serverHealth.collectAsStateWithLifecycle()
    var showJoinRoomDialog by remember { mutableStateOf(false) }
    val handleBack = navigation.onBack
    val appLayout = LocalAppLayout.current

    if (showJoinRoomDialog) {
        var roomCode by remember { mutableStateOf("") }
        val parsedInvite = remember(roomCode) { RemoteSyncCrypto.parseInvite(roomCode) }
        AlertDialog(
            onDismissRequest = { showJoinRoomDialog = false },
            title = { Text(stringResource(R.string.sync_join_room_title)) },
            text = {
                OutlinedTextField(
                    value = roomCode,
                    onValueChange = { input ->
                        roomCode = input.take(128)
                    },
                    label = { Text("房间配对码") },
                    placeholder = { Text("粘贴或输入完整配对码（如 ABC123-K7M9P-2X4Q8）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val invite = parsedInvite
                        if (invite != null) {
                            showJoinRoomDialog = false
                            navigation.navigate(
                                Route.RemoteSyncRoom(
                                    roomId = invite.roomId,
                                    roomKey = invite.roomKey
                                )
                            )
                        }
                    },
                    enabled = parsedInvite != null
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinRoomDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    val entries = listOf(
        SyncActionEntry(
            title = stringResource(R.string.sync_local_title),
            subtitle = stringResource(R.string.sync_local_subtitle),
            icon = Icons.Default.Devices,
            onClick = { navigation.navigate(Route.LocalSync) }
        ),
        SyncActionEntry(
            title = stringResource(R.string.sync_create_room_title),
            subtitle = stringResource(R.string.sync_create_room_subtitle),
            icon = Icons.Default.QrCode,
            onClick = { navigation.navigate(Route.RemoteSyncRoom()) }
        ),
        SyncActionEntry(
            title = stringResource(R.string.sync_join_room_title),
            subtitle = stringResource(R.string.sync_join_room_subtitle),
            icon = Icons.Default.Key,
            onClick = {
                if (navigation.isCurrent) {
                    showJoinRoomDialog = true
                }
            }
        ),
        SyncActionEntry(
            title = stringResource(R.string.webdav_title),
            subtitle = stringResource(R.string.sync_webdav_subtitle),
            icon = Icons.Default.Backup,
            onClick = { navigation.navigate(Route.RemoteSyncWebDav) }
        ),
        SyncActionEntry(
            title = stringResource(R.string.sync_profile_title),
            subtitle = stringResource(R.string.sync_profile_subtitle),
            icon = Icons.Default.Save,
            onClick = { navigation.navigate(Route.ProfileBackup) }
        )
    )

    Scaffold(
        containerColor = if (embedded) Color.Transparent else MaterialTheme.colorScheme.background,
        contentWindowInsets = pageContentWindowInsets(embedded),
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.sync_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    navigationIcon = {
                        IconButton(onClick = handleBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { navigation.navigate(Route.SyncScan) }) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = stringResource(R.string.sync_scan_action)
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        AdaptivePageFrame(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            maxWidthDp = appLayout.settingsMaxWidthDp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SyncServerHealthCard(
                    state = serverHealth,
                    embedded = embedded,
                    onRefresh = viewModel::refreshServerHealth
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (embedded) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column {
                        entries.forEachIndexed { index, entry ->
                            SyncRow(
                                title = entry.title,
                                subtitle = entry.subtitle,
                                icon = entry.icon,
                                onClick = entry.onClick
                            )
                            if (index < entries.lastIndex) {
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

@Composable
private fun SyncServerHealthCard(
    state: SyncServerHealthUiState,
    embedded: Boolean,
    onRefresh: () -> Unit
) {
    val statusTone = when (state.isReachable) {
        true -> LiveSuccessGreen
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (embedded) MaterialTheme.colorScheme.background
        else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusTone)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.endpointName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = state.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRefresh, enabled = !state.isProbing) {
                if (state.isProbing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "重新检测同步服务",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(18.dp)
        )
    }
}
