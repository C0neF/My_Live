package com.mylive.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.R
import com.mylive.app.ui.component.settings.SettingsMenu
import com.mylive.app.ui.component.settings.SettingsSwitch
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable

private val chatTextSizeValues = listOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherSettingsScreen(
    navigator: Navigator,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()
    val logEnable by viewModel.logEnable.collectAsStateWithLifecycle()
    val contributionRankEnable by viewModel.contributionRankEnable.collectAsStateWithLifecycle()
    val superChatSortDesc by viewModel.superChatSortDesc.collectAsStateWithLifecycle()
    val chatTextSize by viewModel.chatTextSize.collectAsStateWithLifecycle()
    val chatBubbleStyle by viewModel.chatBubbleStyle.collectAsStateWithLifecycle()

    val chatTextSizeOptions = stringArrayResource(R.array.chat_text_size_options)
    val chatTextSizeDefault = stringResource(R.string.chat_text_size_default)

    var showChatTextSizeDialog by remember { mutableStateOf(false) }
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
                title = { Text(stringResource(R.string.settings_other)) },
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
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSwitch(
                title = stringResource(R.string.debug_mode_title),
                subtitle = stringResource(R.string.debug_mode_subtitle),
                checked = debugMode,
                onCheckedChange = { viewModel.setDebugMode(it) }
            )
            HorizontalDivider()

            SettingsSwitch(
                title = stringResource(R.string.log_enable_title),
                subtitle = stringResource(R.string.log_enable_subtitle),
                checked = logEnable,
                onCheckedChange = { viewModel.setLogEnable(it) }
            )
            HorizontalDivider()

            SettingsMenu(
                title = stringResource(R.string.settings_debug_log),
                subtitle = stringResource(R.string.settings_debug_log_subtitle),
                onClick = {
                    if (!isExiting) {
                        navigator.navigate(Route.DebugLog)
                    }
                }
            )
            HorizontalDivider()

            SettingsMenu(
                title = stringResource(R.string.contribution_rank_title),
                subtitle = stringResource(R.string.contribution_rank_subtitle),
                value = if (contributionRankEnable) stringResource(R.string.value_show)
                else stringResource(R.string.value_hide),
                onClick = { viewModel.setContributionRankEnable(!contributionRankEnable) }
            )
            HorizontalDivider()

            SettingsMenu(
                title = stringResource(R.string.superchat_sort_title),
                subtitle = stringResource(R.string.superchat_sort_subtitle),
                value = if (superChatSortDesc) stringResource(R.string.superchat_sort_newest)
                else stringResource(R.string.superchat_sort_oldest),
                onClick = { viewModel.setSuperChatSortDesc(!superChatSortDesc) }
            )
            HorizontalDivider()

            SettingsMenu(
                title = stringResource(R.string.chat_text_size_title),
                subtitle = stringResource(R.string.chat_text_size_subtitle),
                value = chatTextSizeOptions.getOrElse(
                    chatTextSizeValues.indexOf(chatTextSize)
                ) { chatTextSizeDefault },
                onClick = { showChatTextSizeDialog = true }
            )
            HorizontalDivider()

            SettingsMenu(
                title = stringResource(R.string.chat_bubble_title),
                subtitle = stringResource(R.string.chat_bubble_subtitle),
                value = if (chatBubbleStyle) stringResource(R.string.chat_bubble_value_bubble)
                else stringResource(R.string.chat_bubble_value_simple),
                onClick = { viewModel.setChatBubbleStyle(!chatBubbleStyle) }
            )
        }
    }

    if (showChatTextSizeDialog) {
        SelectionDialog(
            title = stringResource(R.string.chat_text_size_title),
            options = chatTextSizeOptions,
            values = chatTextSizeValues,
            selectedValue = chatTextSize,
            onSelect = { viewModel.setChatTextSize(it) },
            onDismiss = { showChatTextSizeDialog = false }
        )
    }
}

@Composable
private fun <T> SelectionDialog(
    title: String,
    options: Array<String>,
    values: List<T>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                values.forEachIndexed { index, value ->
                    val isSelected = value == selectedValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = options.getOrElse(index) { value.toString() },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
