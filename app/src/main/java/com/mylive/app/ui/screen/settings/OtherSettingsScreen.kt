package com.mylive.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.R
import com.mylive.app.ui.component.settings.SettingsHairlineDivider
import com.mylive.app.ui.component.settings.SettingsInsetCard
import com.mylive.app.ui.component.settings.SettingsMenu
import com.mylive.app.ui.component.settings.SettingsSectionHeader
import com.mylive.app.ui.component.settings.SettingsSelectionDialog
import com.mylive.app.ui.component.settings.SettingsSwitch
import com.mylive.app.ui.layout.AdaptivePageFrame
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.theme.Icons

private val chatTextSizeValues = listOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherSettingsScreen(
    navigator: Navigator,
    embedded: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()
    val logEnable by viewModel.logEnable.collectAsStateWithLifecycle()
    val superChatSortDesc by viewModel.superChatSortDesc.collectAsStateWithLifecycle()
    val chatTextSize by viewModel.chatTextSize.collectAsStateWithLifecycle()
    val chatBubbleStyle by viewModel.chatBubbleStyle.collectAsStateWithLifecycle()

    val chatTextSizeOptions = stringArrayResource(R.array.chat_text_size_options)
    val chatTextSizeDefault = stringResource(R.string.chat_text_size_default)

    var showChatTextSizeDialog by remember { mutableStateOf(false) }
    val navigation = navigator.rememberRouteActions()
    val handleBack = navigation.onBack

    Scaffold(
        containerColor = if (embedded) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_other)) },
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
        AdaptivePageFrame(Modifier.padding(padding), LocalAppLayout.current.settingsMaxWidthDp) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (embedded) Color.Transparent else MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column {
                    SettingsSectionHeader("聊天面板与醒目留言")
                    SettingsInsetCard(embedded = embedded) {
                        SettingsMenu(
                            title = stringResource(R.string.superchat_sort_title),
                            subtitle = stringResource(R.string.superchat_sort_subtitle),
                            value = if (superChatSortDesc) stringResource(R.string.superchat_sort_newest)
                            else stringResource(R.string.superchat_sort_oldest),
                            onClick = { viewModel.setSuperChatSortDesc(!superChatSortDesc) }
                        )
                        SettingsHairlineDivider()
                        SettingsMenu(
                            title = stringResource(R.string.chat_text_size_title),
                            subtitle = stringResource(R.string.chat_text_size_subtitle),
                            value = chatTextSizeOptions.getOrElse(
                                chatTextSizeValues.indexOf(chatTextSize)
                            ) { chatTextSizeDefault },
                            onClick = { showChatTextSizeDialog = true }
                        )
                        SettingsHairlineDivider()
                        SettingsMenu(
                            title = stringResource(R.string.chat_bubble_title),
                            subtitle = stringResource(R.string.chat_bubble_subtitle),
                            value = if (chatBubbleStyle) stringResource(R.string.chat_bubble_value_bubble)
                            else stringResource(R.string.chat_bubble_value_simple),
                            onClick = { viewModel.setChatBubbleStyle(!chatBubbleStyle) }
                        )
                    }
                }

                Column {
                    SettingsSectionHeader("诊断与日志")
                    SettingsInsetCard(embedded = embedded) {
                        SettingsSwitch(
                            title = stringResource(R.string.debug_mode_title),
                            subtitle = stringResource(R.string.debug_mode_subtitle),
                            checked = debugMode,
                            onCheckedChange = { viewModel.setDebugMode(it) }
                        )
                        SettingsHairlineDivider()
                        SettingsSwitch(
                            title = stringResource(R.string.log_enable_title),
                            subtitle = stringResource(R.string.log_enable_subtitle),
                            checked = logEnable,
                            onCheckedChange = { viewModel.setLogEnable(it) }
                        )
                        SettingsHairlineDivider()
                        SettingsMenu(
                            title = stringResource(R.string.settings_debug_log),
                            subtitle = stringResource(R.string.settings_debug_log_subtitle),
                            onClick = { navigation.navigate(Route.DebugLog) }
                        )
                    }
                }
            }
        }
    }

    if (showChatTextSizeDialog) {
        SettingsSelectionDialog(
            title = stringResource(R.string.chat_text_size_title),
            options = chatTextSizeOptions,
            values = chatTextSizeValues,
            selectedValue = chatTextSize,
            onSelect = { viewModel.setChatTextSize(it) },
            onDismiss = { showChatTextSizeDialog = false }
        )
    }
}
