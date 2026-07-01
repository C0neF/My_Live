package com.mylive.app.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.R
import com.mylive.app.ui.component.settings.SettingsSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackPageSettingsScreen(
    navigator: Navigator,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val liveRoomQuickAccessSortStr by viewModel.liveRoomQuickAccessSort.collectAsStateWithLifecycle()
    val liveRoomQuickAccessEnabled by viewModel.liveRoomQuickAccessEnabled.collectAsStateWithLifecycle()

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

    val quickAccessNames = mapOf(
        "follow" to ("关注列表" to "快速切到已关注的直播间"),
        "history" to ("观看历史" to "打开已经看过的直播间记录"),
        "recommendation" to ("同类推荐" to "按当前分区查找相似直播间")
    )

    val quickAccessList = remember(liveRoomQuickAccessSortStr) {
        liveRoomQuickAccessSortStr.split(",").filter { it.isNotBlank() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放页设置") },
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
                .padding(16.dp)
        ) {
            Text(
                text = "播放器快捷入口",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    SettingsSwitch(
                        title = "显示播放器快捷入口",
                        subtitle = "在播放器控制栏和直播间工具栏显示关注、历史和推荐入口",
                        checked = liveRoomQuickAccessEnabled,
                        onCheckedChange = { viewModel.setLiveRoomQuickAccessEnabled(it) }
                    )

                    if (liveRoomQuickAccessEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        quickAccessList.forEachIndexed { index, key ->
                            val pair = quickAccessNames[key] ?: (key to "")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pair.first,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (pair.second.isNotEmpty()) {
                                        Text(
                                            text = pair.second,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            val mutable = quickAccessList.toMutableList()
                                            val temp = mutable[index]
                                            mutable[index] = mutable[index - 1]
                                            mutable[index - 1] = temp
                                            viewModel.setLiveRoomQuickAccessSort(mutable.joinToString(","))
                                        },
                                        enabled = index > 0
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                                    }
                                    IconButton(
                                        onClick = {
                                            val mutable = quickAccessList.toMutableList()
                                            val temp = mutable[index]
                                            mutable[index] = mutable[index + 1]
                                            mutable[index + 1] = temp
                                            viewModel.setLiveRoomQuickAccessSort(mutable.joinToString(","))
                                        },
                                        enabled = index < quickAccessList.lastIndex
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
                                    }
                                }
                            }
                            if (index < quickAccessList.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
