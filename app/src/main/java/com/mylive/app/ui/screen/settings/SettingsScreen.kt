package com.mylive.app.ui.screen.settings

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
fun SettingsScreen(navigator: Navigator) {
    var isExiting by remember { mutableStateOf(false) }

    val handleBack: () -> Unit = {
        if (!isExiting) {
            isExiting = true
            navigator.goBack()
        }
    }

    BackHandler(enabled = !isExiting) {
        handleBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp)
        ) {
            SettingsCard(
                title = stringResource(R.string.settings_danmu),
                subtitle = stringResource(R.string.settings_danmu_subtitle),
                onClick = {
                    if (!isExiting) {
                        navigator.navigate(Route.SettingsDanmu)
                    }
                }
            )

            SettingsCard(
                title = stringResource(R.string.settings_play),
                subtitle = stringResource(R.string.settings_play_subtitle),
                onClick = {
                    if (!isExiting) {
                        navigator.navigate(Route.SettingsPlay)
                    }
                }
            )

            SettingsCard(
                title = stringResource(R.string.settings_appearance),
                subtitle = stringResource(R.string.settings_appearance_subtitle),
                onClick = {
                    if (!isExiting) {
                        navigator.navigate(Route.SettingsAppStyle)
                    }
                }
            )

            SettingsCard(
                title = stringResource(R.string.settings_auto_exit),
                subtitle = stringResource(R.string.settings_auto_exit_subtitle),
                onClick = {
                    if (!isExiting) {
                        navigator.navigate(Route.SettingsAutoExit)
                    }
                }
            )

            SettingsCard(
                title = stringResource(R.string.settings_other),
                subtitle = stringResource(R.string.settings_other_subtitle),
                onClick = {
                    if (!isExiting) {
                        navigator.navigate(Route.SettingsOther)
                    }
                }
            )

            SettingsCard(
                title = stringResource(R.string.settings_follow),
                subtitle = stringResource(R.string.settings_follow_subtitle),
                onClick = {
                    if (!isExiting) {
                        navigator.navigate(Route.SettingsFollow)
                    }
                }
            )

            SettingsCard(
                title = stringResource(R.string.settings_indexed),
                subtitle = stringResource(R.string.settings_indexed_subtitle),
                onClick = {
                    if (!isExiting) {
                        navigator.navigate(Route.SettingsIndexed)
                    }
                }
            )

            SettingsCard(
                title = stringResource(R.string.settings_playback_page),
                subtitle = stringResource(R.string.settings_playback_page_subtitle),
                onClick = {
                    if (!isExiting) {
                        navigator.navigate(Route.SettingsPlaybackPage)
                    }
                }
            )

            SettingsCard(
                title = stringResource(R.string.settings_update),
                subtitle = stringResource(R.string.settings_update_subtitle),
                onClick = {
                    if (!isExiting) {
                        navigator.navigate(Route.SettingsUpdate)
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsCard(
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
