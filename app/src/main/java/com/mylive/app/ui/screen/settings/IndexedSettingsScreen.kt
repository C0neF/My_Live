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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndexedSettingsScreen(
    navigator: Navigator,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val siteSortStr by viewModel.siteSort.collectAsStateWithLifecycle()
    val homeSortStr by viewModel.homeSort.collectAsStateWithLifecycle()

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
        siteSortStr.split(",").filter { it.isNotBlank() }
    }

    val homeList = remember(homeSortStr) {
        homeSortStr.split(",").filter { it.isNotBlank() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主页设置") },
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
                text = "主页排序 (重启后生效)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    homeList.forEachIndexed { index, key ->
                        val name = homeNames[key] ?: key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 12.dp),
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
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "平台排序 (重启后生效)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    siteList.forEachIndexed { index, key ->
                        val name = siteNames[key] ?: key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 12.dp),
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
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
