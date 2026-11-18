package com.mylive.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.R
import com.mylive.app.ui.component.settings.SettingsHairlineDivider
import com.mylive.app.ui.component.settings.SettingsInsetCard
import com.mylive.app.ui.component.settings.SettingsSectionHeader
import com.mylive.app.ui.layout.AdaptivePageFrame
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.theme.Icons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndexedSettingsScreen(
    navigator: Navigator,
    embedded: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val siteSortStr by viewModel.siteSort.collectAsStateWithLifecycle()
    val homeSortStr by viewModel.homeSort.collectAsStateWithLifecycle()

    val handleBack = navigator.rememberRouteActions().onBack

    val siteNames = mapOf(
        "bilibili" to "哔哩哔哩",
        "douyu" to "斗鱼",
        "huya" to "虎牙",
        "douyin" to "抖音"
    )

    val homeNames = mapOf(
        "recommend" to "首页",
        "follow" to "关注",
        "category" to "分类",
        "user" to "我的"
    )

    val siteList = remember(siteSortStr) {
        val parsed = siteSortStr.split(",").filter { it.isNotBlank() }.distinct().toMutableList()
        listOf("bilibili", "douyu", "huya", "douyin").forEach { key ->
            if (key !in parsed) parsed.add(key)
        }
        parsed
    }

    val homeList = remember(homeSortStr) {
        val parsed = homeSortStr.split(",").filter { it.isNotBlank() }.distinct().toMutableList()
        listOf("recommend", "follow", "category", "user").forEach { key ->
            if (key !in parsed) parsed.add(key)
        }
        parsed
    }

    Scaffold(
        containerColor = if (embedded) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text("主页设置") },
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column {
                    SettingsSectionHeader("主页排序")
                    SettingsInsetCard(embedded = embedded) {
                        homeList.forEachIndexed { index, key ->
                            val name = homeNames[key] ?: key
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Row {
                                    IconButton(
                                        onClick = {
                                            val mutable = homeList.toMutableList()
                                            val temp = mutable[index]
                                            mutable[index] = mutable[index - 1]
                                            mutable[index - 1] = temp
                                            viewModel.setHomeSort(mutable.joinToString(","))
                                        },
                                        enabled = index > 0
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                                    }
                                    IconButton(
                                        onClick = {
                                            val mutable = homeList.toMutableList()
                                            val temp = mutable[index]
                                            mutable[index] = mutable[index + 1]
                                            mutable[index + 1] = temp
                                            viewModel.setHomeSort(mutable.joinToString(","))
                                        },
                                        enabled = index < homeList.lastIndex
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
                                    }
                                }
                            }
                            if (index < homeList.lastIndex) {
                                SettingsHairlineDivider()
                            }
                        }
                    }
                }

                Column {
                    SettingsSectionHeader("平台排序")
                    SettingsInsetCard(embedded = embedded) {
                        siteList.forEachIndexed { index, key ->
                            val name = siteNames[key] ?: key
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Row {
                                    IconButton(
                                        onClick = {
                                            val mutable = siteList.toMutableList()
                                            val temp = mutable[index]
                                            mutable[index] = mutable[index - 1]
                                            mutable[index - 1] = temp
                                            viewModel.setSiteSort(mutable.joinToString(","))
                                        },
                                        enabled = index > 0
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                                    }
                                    IconButton(
                                        onClick = {
                                            val mutable = siteList.toMutableList()
                                            val temp = mutable[index]
                                            mutable[index] = mutable[index + 1]
                                            mutable[index + 1] = temp
                                            viewModel.setSiteSort(mutable.joinToString(","))
                                        },
                                        enabled = index < siteList.lastIndex
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
                                    }
                                }
                            }
                            if (index < siteList.lastIndex) {
                                SettingsHairlineDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
