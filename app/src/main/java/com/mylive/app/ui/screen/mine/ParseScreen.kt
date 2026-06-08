package com.mylive.app.ui.screen.mine

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.R
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.core.model.LivePlayUrl
import com.mylive.app.ui.util.copyPlainText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParseScreen(
    navigator: Navigator,
    viewModel: ParseViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

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

    val isLoading by viewModel.isLoading.collectAsState()

    var jumpUrlText by remember { mutableStateOf("") }
    var getUrlText by remember { mutableStateOf("") }

    var showQualityDialog by remember { mutableStateOf(false) }
    var qualitiesToSelect by remember { mutableStateOf<List<LivePlayQuality>>(emptyList()) }
    var onQualitySelectedCallback by remember { mutableStateOf<((LivePlayQuality) -> Unit)?>(null) }

    var showLineDialog by remember { mutableStateOf(false) }
    var playUrlToSelect by remember { mutableStateOf<LivePlayUrl?>(null) }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is ParseEvent.NavigateToRoom -> {
                    navigator.navigate(Route.LiveRoomDetail(roomId = event.roomId, siteId = event.siteId))
                }
                is ParseEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is ParseEvent.ShowQualitySelect -> {
                    qualitiesToSelect = event.qualities
                    onQualitySelectedCallback = event.onSelect
                    showQualityDialog = true
                }
                is ParseEvent.ShowLineSelect -> {
                    playUrlToSelect = event.playUrl
                    showLineDialog = true
                }
            }
        }
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text(stringResource(R.string.parse_select_quality)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    qualitiesToSelect.forEach { quality ->
                        TextButton(
                            onClick = {
                                showQualityDialog = false
                                onQualitySelectedCallback?.invoke(quality)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(quality.quality, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showLineDialog && playUrlToSelect != null) {
        AlertDialog(
            onDismissRequest = { showLineDialog = false },
            title = { Text(stringResource(R.string.parse_select_line)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    playUrlToSelect!!.urls.forEachIndexed { index, url ->
                        Card(
                            onClick = {
                                copyPlainText(context, "play url", url)
                                Toast.makeText(context, R.string.parse_copied, Toast.LENGTH_SHORT).show()
                                showLineDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "线路 ${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = url,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLineDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.parse_title)) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card 1: Jump to Room
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.parse_jump_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedTextField(
                            value = jumpUrlText,
                            onValueChange = { jumpUrlText = it },
                            placeholder = { Text(stringResource(R.string.parse_jump_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                keyboardController?.hide()
                                viewModel.jumpToRoom(jumpUrlText)
                            }),
                            enabled = !isLoading
                        )
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                viewModel.jumpToRoom(jumpUrlText)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Default.PlayCircleOutline, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.parse_jump_btn))
                        }
                    }
                }

                // Card 2: Get Direct Stream Link
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.parse_url_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedTextField(
                            value = getUrlText,
                            onValueChange = { getUrlText = it },
                            placeholder = { Text(stringResource(R.string.parse_jump_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                keyboardController?.hide()
                                viewModel.getPlayUrl(getUrlText)
                            }),
                            enabled = !isLoading
                        )
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                viewModel.getPlayUrl(getUrlText)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.parse_url_btn))
                        }
                    }
                }

                // Card 3: Help / Description
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.parse_help_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.parse_help_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
