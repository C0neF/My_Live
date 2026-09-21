package com.mylive.app.ui.screen.sync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncHubScreen(
    navigator: Navigator,
    viewModel: SyncHubViewModel = hiltViewModel()
) {
    val navigation = navigator.rememberRouteActions()
    val serverHealth by viewModel.serverHealth.collectAsStateWithLifecycle()
    var showJoinRoomDialog by remember { mutableStateOf(false) }
    val handleBack = navigation.onBack

    if (showJoinRoomDialog) {
        var roomCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJoinRoomDialog = false },
            title = { Text(stringResource(R.string.sync_join_room_title)) },
            text = {
                OutlinedTextField(
                    value = roomCode,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isLetterOrDigit() }.take(6).uppercase()
                        roomCode = filtered
                    },
                    label = { Text("房间号") },
                    placeholder = { Text("请输入6位房间号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showJoinRoomDialog = false
                        if (roomCode.length == 6) {
                            navigation.navigate(Route.RemoteSyncRoom(roomId = roomCode))
                        }
                    },
                    enabled = roomCode.length == 6
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navigation.navigate(Route.SyncScan)
                    }) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.sync_scan_action)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp)
        ) {
            SyncServerHealthCard(
                state = serverHealth,
                onRefresh = viewModel::refreshServerHealth
            )

            SyncCard(
                title = stringResource(R.string.sync_local_title),
                subtitle = stringResource(R.string.sync_local_subtitle),
                onClick = {
                    navigation.navigate(Route.LocalSync)
                }
            )

            SyncCard(
                title = stringResource(R.string.sync_create_room_title),
                subtitle = stringResource(R.string.sync_create_room_subtitle),
                onClick = {
                    navigation.navigate(Route.RemoteSyncRoom())
                }
            )

            SyncCard(
                title = stringResource(R.string.sync_join_room_title),
                subtitle = stringResource(R.string.sync_join_room_subtitle),
                onClick = {
                    if (navigation.isCurrent) {
                        showJoinRoomDialog = true
                    }
                }
            )

            SyncCard(
                title = stringResource(R.string.webdav_title),
                subtitle = stringResource(R.string.sync_webdav_subtitle),
                onClick = {
                    navigation.navigate(Route.RemoteSyncWebDav)
                }
            )

            SyncCard(
                title = stringResource(R.string.sync_profile_title),
                subtitle = stringResource(R.string.sync_profile_subtitle),
                onClick = {
                    navigation.navigate(Route.ProfileBackup)
                }
            )
        }
    }
}

@Composable
private fun SyncServerHealthCard(
    state: SyncServerHealthUiState,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            tint = when (state.isReachable) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.endpointName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
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
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = "重新检测同步服务")
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun SyncCard(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (enabled) MaterialTheme.colorScheme.onSurface 
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant 
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant 
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}
