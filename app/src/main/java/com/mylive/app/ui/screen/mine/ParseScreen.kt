package com.mylive.app.ui.screen.mine

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.ui.component.settings.SettingsInsetCard
import com.mylive.app.ui.layout.AdaptivePageFrame
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.layout.pageContentWindowInsets
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
    embedded: Boolean = false,
    viewModel: ParseViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val handleBack = navigator.rememberRouteActions().onBack

    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

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
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            title = { Text(stringResource(R.string.parse_select_quality)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    qualitiesToSelect.forEach { quality ->
                        TextButton(
                            onClick = {
                                showQualityDialog = false
                                onQualitySelectedCallback?.invoke(quality)
                            },
                            shape = RoundedCornerShape(10.dp),
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
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            title = { Text(stringResource(R.string.parse_select_line)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    playUrlToSelect!!.urls.forEachIndexed { index, url ->
                        Surface(
                            onClick = {
                                copyPlainText(context, "play url", url)
                                Toast.makeText(context, R.string.parse_copied, Toast.LENGTH_SHORT).show()
                                showLineDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "线路 ${index + 1}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        containerColor = if (embedded) Color.Transparent else MaterialTheme.colorScheme.background,
        contentWindowInsets = pageContentWindowInsets(embedded),
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.parse_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
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
        }
    ) { padding ->
        AdaptivePageFrame(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (embedded) Color.Transparent else MaterialTheme.colorScheme.background),
            maxWidthDp = LocalAppLayout.current.settingsMaxWidthDp
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val sideBySide = maxWidth >= 640.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 16.dp,
                            top = if (embedded) 10.dp else 16.dp,
                            end = 16.dp,
                            bottom = 16.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val jumpCard: @Composable (Modifier) -> Unit = { cardModifier ->
                        SettingsInsetCard(
                            modifier = cardModifier,
                            embedded = embedded
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.parse_jump_title),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                OutlinedTextField(
                                    value = jumpUrlText,
                                    onValueChange = { jumpUrlText = it },
                                    placeholder = { Text(stringResource(R.string.parse_jump_hint)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
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
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isLoading
                                ) {
                                    Icon(Icons.Default.PlayCircleOutline, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.parse_jump_btn))
                                }
                            }
                        }
                    }

                    val extractCard: @Composable (Modifier) -> Unit = { cardModifier ->
                        SettingsInsetCard(
                            modifier = cardModifier,
                            embedded = embedded
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.parse_url_title),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                OutlinedTextField(
                                    value = getUrlText,
                                    onValueChange = { getUrlText = it },
                                    placeholder = { Text(stringResource(R.string.parse_jump_hint)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
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
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isLoading
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.parse_url_btn))
                                }
                            }
                        }
                    }

                    if (sideBySide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            jumpCard(Modifier.weight(1f))
                            extractCard(Modifier.weight(1f))
                        }
                    } else {
                        jumpCard(Modifier.fillMaxWidth())
                        extractCard(Modifier.fillMaxWidth())
                    }

                    SettingsInsetCard(embedded = embedded) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.parse_help_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
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
}
