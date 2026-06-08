package com.mylive.app.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.R
import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.data.local.entity.ShieldPresetEntity
import com.mylive.app.ui.component.settings.SettingsSwitch
import com.mylive.app.ui.util.copyPlainText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldSettingsScreen(
    navigator: Navigator,
    viewModel: ShieldSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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

    val danmuShieldEnable by viewModel.danmuShieldEnable.collectAsState(initial = true)
    val danmuKeywordShieldEnable by viewModel.danmuKeywordShieldEnable.collectAsState(initial = true)
    val danmuUserShieldEnable by viewModel.danmuUserShieldEnable.collectAsState(initial = true)

    val keywords by viewModel.keywords.collectAsState(initial = emptyList())
    val presets by viewModel.presets.collectAsState(initial = emptyList())
    val activeUserShields by viewModel.activeUserShields.collectAsState(initial = emptyList())
    val selectedUserSiteId by viewModel.selectedUserSiteId.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        stringResource(R.string.shield_tab_keyword),
        stringResource(R.string.shield_tab_user),
        stringResource(R.string.shield_tab_preset)
    )

    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shield_title)) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
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
        ) {
            // Main tabs
            TabRow(selectedTabIndex = selectedTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // Global toggles
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    SettingsSwitch(
                        title = "启用弹幕屏蔽功能",
                        checked = danmuShieldEnable,
                        onCheckedChange = { viewModel.setDanmuShieldEnable(it) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    0 -> KeywordTabContent(
                        enabled = danmuShieldEnable && danmuKeywordShieldEnable,
                        toggleValue = danmuKeywordShieldEnable,
                        onToggleChange = { viewModel.setDanmuKeywordShieldEnable(it) },
                        keywords = keywords,
                        onAdd = { viewModel.addKeyword(it) },
                        onDelete = { viewModel.deleteShield(it) },
                        onClear = { viewModel.clearKeywords() }
                    )
                    1 -> UserTabContent(
                        enabled = danmuShieldEnable && danmuUserShieldEnable,
                        toggleValue = danmuUserShieldEnable,
                        onToggleChange = { viewModel.setDanmuUserShieldEnable(it) },
                        selectedSiteId = selectedUserSiteId,
                        activeUsers = activeUserShields,
                        onSiteSelect = { viewModel.selectUserSite(it) },
                        onAdd = { username -> viewModel.addUserShield(username, selectedUserSiteId) },
                        onDelete = { viewModel.deleteShield(it) },
                        onClearGroup = { viewModel.clearUserShields(selectedUserSiteId) },
                        onClearAll = { viewModel.clearAllUserShields() }
                    )
                    2 -> PresetTabContent(
                        presets = presets,
                        onSaveCurrent = { viewModel.savePreset(it) },
                        onApply = { viewModel.applyPreset(it) },
                        onDelete = { viewModel.deletePreset(it.name) },
                        onExport = {
                            coroutineScope.launch {
                                val json = viewModel.generateExportJson()
                                if (json.isNotEmpty()) {
                                    copyPlainText(context, "shield config", json)
                                    Toast.makeText(context, R.string.shield_preset_copied, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onImportClick = {
                            importJsonText = ""
                            showImportDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.shield_preset_import_title)) },
            text = {
                OutlinedTextField(
                    value = importJsonText,
                    onValueChange = { importJsonText = it },
                    placeholder = { Text(stringResource(R.string.shield_preset_import_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    maxLines = 10
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = viewModel.importPresetJson(importJsonText)
                        if (success) {
                            Toast.makeText(context, R.string.shield_preset_import_success, Toast.LENGTH_SHORT).show()
                            showImportDialog = false
                        } else {
                            Toast.makeText(context, R.string.shield_preset_import_failed, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = importJsonText.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun KeywordTabContent(
    enabled: Boolean,
    toggleValue: Boolean,
    onToggleChange: (Boolean) -> Unit,
    keywords: List<ShieldEntity>,
    onAdd: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsSwitch(
            title = "过滤关键词列表",
            checked = toggleValue,
            onCheckedChange = onToggleChange,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Add bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text(stringResource(R.string.shield_keyword_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = enabled
            )
            Button(
                onClick = {
                    onAdd(textInput)
                    textInput = ""
                },
                enabled = enabled && textInput.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.shield_add))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Clear header
        if (keywords.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onClear,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    enabled = enabled
                ) {
                    Text(stringResource(R.string.shield_clear))
                }
            }
        }

        // List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(keywords, key = { it.id }) { entity ->
                val keyword = entity.value.removePrefix("keyword:")
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = keyword,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onDelete(entity.id) },
                            enabled = enabled
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.cd_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserTabContent(
    enabled: Boolean,
    toggleValue: Boolean,
    onToggleChange: (Boolean) -> Unit,
    selectedSiteId: String,
    activeUsers: List<ShieldEntity>,
    onSiteSelect: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onClearGroup: () -> Unit,
    onClearAll: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val siteOptions = listOf(
        "__all__" to "所有平台",
        "bilibili" to "哔哩哔哩",
        "douyu" to "斗鱼",
        "huya" to "虎牙",
        "douyin" to "抖音"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsSwitch(
            title = "屏蔽用户列表",
            checked = toggleValue,
            onCheckedChange = onToggleChange,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Platform sub-tabs
        ScrollableTabRow(
            selectedTabIndex = siteOptions.indexOfFirst { it.first == selectedSiteId }.coerceAtLeast(0),
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            siteOptions.forEach { pair ->
                Tab(
                    selected = selectedSiteId == pair.first,
                    onClick = { onSiteSelect(pair.first) },
                    text = { Text(pair.second) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Add user bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text(stringResource(R.string.shield_user_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = enabled
            )
            Button(
                onClick = {
                    onAdd(textInput)
                    textInput = ""
                },
                enabled = enabled && textInput.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.shield_add))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Clear header buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onClearGroup,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                enabled = enabled && activeUsers.isNotEmpty()
            ) {
                Text(stringResource(R.string.shield_clear_group))
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = onClearAll,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                enabled = enabled
            ) {
                Text(stringResource(R.string.shield_clear_all))
            }
        }

        // List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(activeUsers, key = { it.id }) { entity ->
                val username = entity.value.substringAfterLast(":")
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = username,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onDelete(entity.id) },
                            enabled = enabled
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.cd_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetTabContent(
    presets: List<ShieldPresetEntity>,
    onSaveCurrent: (String) -> Unit,
    onApply: (ShieldPresetEntity) -> Unit,
    onDelete: (ShieldPresetEntity) -> Unit,
    onExport: () -> Unit,
    onImportClick: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Import/Export buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onImportClick,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.shield_preset_import))
            }
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.shield_preset_export))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save current as preset card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.shield_preset_save),
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text(stringResource(R.string.shield_preset_name_hint)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            onSaveCurrent(textInput)
                            textInput = ""
                        },
                        enabled = textInput.isNotBlank()
                    ) {
                        Text(stringResource(R.string.shield_add))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Presets list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(presets, key = { it.name }) { preset ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onApply(preset) }) {
                                Text(stringResource(R.string.shield_preset_apply))
                            }
                            IconButton(onClick = { onDelete(preset) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.cd_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
