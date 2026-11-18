package com.mylive.app.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.R
import com.mylive.app.service.followUpdateDurationOptions
import com.mylive.app.service.resolveFollowUpdateConcurrency
import com.mylive.app.ui.component.settings.SettingsHairlineDivider
import com.mylive.app.ui.component.settings.SettingsInsetCard
import com.mylive.app.ui.component.settings.SettingsMenu
import com.mylive.app.ui.component.settings.SettingsSectionHeader
import com.mylive.app.ui.component.settings.SettingsSelectionDialog
import com.mylive.app.ui.component.settings.SettingsSwitch
import com.mylive.app.ui.layout.AdaptivePageFrame
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.motion.LocalAppMotionPolicy
import com.mylive.app.ui.motion.expandCollapseEnter
import com.mylive.app.ui.motion.expandCollapseExit
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.theme.Icons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowSettingsScreen(
    navigator: Navigator,
    embedded: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val autoUpdateFollowEnable by viewModel.autoUpdateFollowEnable.collectAsStateWithLifecycle()
    val autoUpdateFollowDuration by viewModel.autoUpdateFollowDuration.collectAsStateWithLifecycle()
    val updateFollowThreadCount by viewModel.updateFollowThreadCount.collectAsStateWithLifecycle()

    var showDurationDialog by remember { mutableStateOf(false) }
    var showConcurrencyDialog by remember { mutableStateOf(false) }
    val handleBack = navigator.rememberRouteActions().onBack

    Scaffold(
        containerColor = if (embedded) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_follow)) },
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
    ) { paddingValues ->
        AdaptivePageFrame(Modifier.padding(paddingValues), LocalAppLayout.current.settingsMaxWidthDp) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (embedded) Color.Transparent else MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                val motionPolicy = LocalAppMotionPolicy.current
                SettingsSectionHeader("后台刷新与并发")
                SettingsInsetCard(embedded = embedded) {
                    SettingsSwitch(
                        title = "自动更新关注直播状态",
                        checked = autoUpdateFollowEnable,
                        onCheckedChange = { viewModel.setAutoUpdateFollowEnable(it) }
                    )
                    SettingsHairlineDivider()

                    AnimatedVisibility(
                        visible = autoUpdateFollowEnable,
                        enter = expandCollapseEnter(motionPolicy),
                        exit = expandCollapseExit(motionPolicy)
                    ) {
                        Column {
                            val hours = autoUpdateFollowDuration / 60
                            val minutes = autoUpdateFollowDuration % 60
                            val durationText = buildString {
                                if (hours > 0) append("${hours}小时")
                                append("${minutes}分钟")
                            }
                            SettingsMenu(
                                title = "自动更新间隔",
                                value = durationText,
                                onClick = { showDurationDialog = true }
                            )
                            SettingsHairlineDivider()
                        }
                    }

                    val threadText = when (updateFollowThreadCount) {
                        0 -> "自动 (根据 CPU 核心数)"
                        8 -> "8 (默认)"
                        else -> "$updateFollowThreadCount"
                    }
                    SettingsMenu(
                        title = "更新并发数",
                        subtitle = "默认 8；0 = 自动根据 CPU 核心数优化；可手动设置 1-20",
                        value = threadText,
                        onClick = { showConcurrencyDialog = true }
                    )
                }
            }
        }
    }

    if (showDurationDialog) {
        val durationOptions = followUpdateDurationOptions()
        val durationLabels = durationOptions.map { min ->
            val h = min / 60
            val m = min % 60
            buildString {
                if (h > 0) append("${h}小时")
                if (m > 0) append("${m}分钟")
            }
        }.toTypedArray()

        SettingsSelectionDialog(
            title = "自动更新间隔",
            options = durationLabels,
            values = durationOptions,
            selectedValue = autoUpdateFollowDuration,
            onSelect = { viewModel.setAutoUpdateFollowDuration(it) },
            onDismiss = { showDurationDialog = false }
        )
    }

    if (showConcurrencyDialog) {
        val cpuCount = Runtime.getRuntime().availableProcessors()
        val autoValue = resolveFollowUpdateConcurrency(setting = 0, cpuCount = cpuCount)
        var customInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showConcurrencyDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            title = {
                Text(
                    text = "设置更新并发数",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "CPU 核心数: $cpuCount · 默认: 8 · 自动推荐: $autoValue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val options = listOf(
                        0 to "自动 ($autoValue)",
                        4 to "4",
                        8 to "8 (默认)",
                        12 to "12",
                        16 to "16",
                        20 to "20"
                    )

                    options.forEachIndexed { index, (value, label) ->
                        val isSelected = updateFollowThreadCount == value
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.setUpdateFollowThreadCount(value)
                                    showConcurrencyDialog = false
                                }
                                .padding(vertical = 11.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (index < options.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it.filter { char -> char.isDigit() } },
                        label = { Text("自定义 (0-20，0 为自动)") },
                        placeholder = { Text("输入 0-20 之间的数字") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val num = customInput.toIntOrNull()
                        if (num != null && num in 0..20) {
                            viewModel.setUpdateFollowThreadCount(num)
                            showConcurrencyDialog = false
                        }
                    },
                    enabled = customInput.toIntOrNull() != null && customInput.toInt() in 0..20
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConcurrencyDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
