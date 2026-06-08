package com.mylive.app.ui.screen.sync

import androidx.activity.compose.BackHandler
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncHubScreen(navigator: Navigator) {
    var isExiting by remember { mutableStateOf(false) }

    var showJoinRoomDialog by remember { mutableStateOf(false) }

    val handleBack: () -> Unit = {
        if (!isExiting) {
            isExiting = true
            navigator.goBack()
        }
    }

    BackHandler(enabled = !isExiting) {
        handleBack()
    }

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
                        if (!isExiting && roomCode.length == 6) {
                            navigator.navigate(Route.RemoteSyncRoom(roomId = roomCode))
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
                        if (!isExiting) {
                            navigator.navigate(Route.SyncScan)
                        }
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
            SyncCard(
                title = stringResource(R.string.sync_local_title),
                subtitle = stringResource(R.string.sync_local_subtitle),
                onClick = {
                    if (!isExiting) {
                        navigator.navigate(Route.LocalSync)
                    }
                }
            )

            SyncCard(
                title = stringResource(R.string.sync_create_room_title),
                subtitle = stringResource(R.string.sync_create_room_subtitle),
                onClick = {
                    if (!isExiting) {
                        navigator.navigate(Route.RemoteSyncRoom())
                    }
                }
            )

            SyncCard(
                title = stringResource(R.string.sync_join_room_title),
                subtitle = stringResource(R.string.sync_join_room_subtitle),
                onClick = {
                    if (!isExiting) {
                        showJoinRoomDialog = true
                    }
                }
            )

            SyncCard(
                title = stringResource(R.string.webdav_title),
                subtitle = stringResource(R.string.sync_webdav_subtitle),
                onClick = {
                    if (!isExiting) {
                        navigator.navigate(Route.RemoteSyncWebDav)
                    }
                }
            )

            SyncCard(
                title = stringResource(R.string.sync_profile_title),
                subtitle = stringResource(R.string.sync_profile_subtitle),
                onClick = {
                    if (!isExiting) {
                        navigator.navigate(Route.ProfileBackup)
                    }
                }
            )
        }
    }
}

@Composable
private fun SyncCard(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
