package com.mylive.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.R
import com.mylive.app.ui.component.settings.SettingsHairlineDivider
import com.mylive.app.ui.component.settings.SettingsInsetCard
import com.mylive.app.ui.component.settings.SettingsSectionHeader
import com.mylive.app.ui.component.settings.SettingsSwitch
import com.mylive.app.ui.layout.AdaptivePageFrame
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.theme.Icons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackPageSettingsScreen(
    navigator: Navigator,
    embedded: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val liveRoomQuickAccessSortStr by viewModel.liveRoomQuickAccessSort.collectAsStateWithLifecycle()
    val liveRoomQuickAccessEnabled by viewModel.liveRoomQuickAccessEnabled.collectAsStateWithLifecycle()

    val handleBack = navigator.rememberRouteActions().onBack

    val quickAccessNames = mapOf(
        "follow" to ("关注列表" to "快速切到已关注的直播间"),
        "history" to ("观看历史" to "打开已经看过的直播间记录"),
        "recommendation" to ("同类推荐" to "按当前分区查找相似直播间")
    )

    val quickAccessList = remember(liveRoomQuickAccessSortStr) {
        liveRoomQuickAccessSortStr.split(",").filter { it.isNotBlank() }
    }

    Scaffold(
        containerColor = if (embedded) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text("播放页设置") },
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
                SettingsSectionHeader("播放器快捷入口")
                SettingsInsetCard(embedded = embedded) {
                    SettingsSwitch(
                        title = "显示播放器快捷入口",
                        subtitle = "在播放器控制栏和直播间工具栏显示关注、历史和推荐入口",
                        checked = liveRoomQuickAccessEnabled,
                        onCheckedChange = { viewModel.setLiveRoomQuickAccessEnabled(it) }
                    )

                    if (liveRoomQuickAccessEnabled) {
                        SettingsHairlineDivider()
                        quickAccessList.forEachIndexed { index, key ->
                            val pair = quickAccessNames[key] ?: (key to "")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pair.first,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (pair.second.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
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
                                SettingsHairlineDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
