package com.mylive.app.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.R
import com.mylive.app.ui.component.settings.SettingsMenu
import com.mylive.app.ui.component.settings.SettingsSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowSettingsScreen(
    navigator: Navigator,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val autoUpdateFollowEnable by viewModel.autoUpdateFollowEnable.collectAsStateWithLifecycle()
    val autoUpdateFollowDuration by viewModel.autoUpdateFollowDuration.collectAsStateWithLifecycle()
    val updateFollowThreadCount by viewModel.updateFollowThreadCount.collectAsStateWithLifecycle()

    var showDurationDialog by remember { mutableStateOf(false) }
    var showConcurrencyDialog by remember { mutableStateOf(false) }
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
                title = { Text(stringResource(R.string.settings_follow)) },
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSwitch(
                title = "自动更新关注直播状态",
                checked = autoUpdateFollowEnable,
                onCheckedChange = { viewModel.setAutoUpdateFollowEnable(it) }
            )
            HorizontalDivider()

            if (autoUpdateFollowEnable) {
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
                HorizontalDivider()
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
            HorizontalDivider()
        }
    }

    if (showDurationDialog) {
        val durationOptions = listOf(5, 10, 15, 30, 60, 120, 180, 240, 360)
        val durationLabels = durationOptions.map { min ->
            val h = min / 60
            val m = min % 60
            buildString {
                if (h > 0) append("${h}小时")
                if (m > 0) append("${m}分钟")
            }
        }.toTypedArray()

        SelectionDialog(
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
        val autoValue = (cpuCount * 2.5).roundToInt().coerceIn(4, 20)
        var customInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showConcurrencyDialog = false },
            title = { Text("设置更新并发数") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("CPU 核心数: $cpuCount", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("默认值: 8", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("自动推荐值: $autoValue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("选择并发数：", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    val options = listOf(
                        0 to "自动 ($autoValue)",
                        4 to "4",
                        8 to "8 (默认)",
                        12 to "12",
                        16 to "16",
                        20 to "20"
                    )

                    options.forEach { (value, label) ->
                        val isSelected = updateFollowThreadCount == value
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setUpdateFollowThreadCount(value)
                                    showConcurrencyDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
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

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it.filter { char -> char.isDigit() } },
                        label = { Text("自定义 (0-20，0 为自动)") },
                        placeholder = { Text("输入 0-20 之间的数字") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()

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
                Text("取消")
            }
        }
    )
}
