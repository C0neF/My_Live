package com.mylive.app.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    navigator: Navigator,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

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

    // Show message snackbar
    LaunchedEffect(uiState.message) {
        val msg = uiState.message ?: return@LaunchedEffect
        val text = when (msg) {
            AccountMessage.BilibiliCookieSaved -> context.getString(R.string.account_bilibili_cookie_saved)
            AccountMessage.DouyinCookieSaved -> context.getString(R.string.account_douyin_cookie_saved)
            AccountMessage.BilibiliLoggedOut -> context.getString(R.string.account_bilibili_logged_out)
            AccountMessage.DouyinLoggedOut -> context.getString(R.string.account_douyin_logged_out)
        }
        snackbarHostState.showSnackbar(text)
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp)
        ) {
            // B站 Section
            AccountSectionHeader(title = stringResource(R.string.account_bilibili_section))
            
            AccountCard(
                title = stringResource(R.string.account_status_label),
                contentRight = {
                    Text(
                        text = if (uiState.isLoggedInBiliBili) stringResource(R.string.account_status_logged_in)
                               else stringResource(R.string.account_status_logged_out),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (uiState.isLoggedInBiliBili) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            if (uiState.isLoggedInBiliBili) {
                AccountCard(
                    title = stringResource(R.string.account_logout),
                    subtitle = stringResource(R.string.account_clear_bilibili_cookie),
                    onClick = { viewModel.logoutBiliBili() }
                )
            } else {
                AccountCard(
                    title = stringResource(R.string.account_webview_login),
                    subtitle = stringResource(R.string.account_webview_login_subtitle),
                    onClick = { navigator.navigate(Route.SettingsAccountLoginWebview) }
                )
                AccountCard(
                    title = stringResource(R.string.account_cookie_login),
                    subtitle = stringResource(R.string.account_cookie_login_bilibili_subtitle),
                    onClick = { viewModel.showBilibiliCookieDialog() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 抖音 Section
            AccountSectionHeader(title = stringResource(R.string.account_douyin_section))
            
            AccountCard(
                title = stringResource(R.string.account_status_label),
                contentRight = {
                    Text(
                        text = if (uiState.isLoggedInDouyin) stringResource(R.string.account_status_logged_in)
                               else stringResource(R.string.account_status_logged_out),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (uiState.isLoggedInDouyin) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            if (uiState.isLoggedInDouyin) {
                AccountCard(
                    title = stringResource(R.string.account_logout),
                    subtitle = stringResource(R.string.account_clear_douyin_cookie),
                    onClick = { viewModel.logoutDouyin() }
                )
            } else {
                AccountCard(
                    title = stringResource(R.string.account_webview_login),
                    subtitle = "通过内置浏览器登录抖音",
                    onClick = { navigator.navigate(Route.SettingsAccountDouyinLoginWebview) }
                )
                AccountCard(
                    title = stringResource(R.string.account_cookie_login),
                    subtitle = stringResource(R.string.account_cookie_login_douyin_subtitle),
                    onClick = { viewModel.showDouyinCookieDialog() }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // B站 Cookie Dialog
    if (uiState.showBilibiliCookieDialog) {
        CookieInputDialog(
            title = stringResource(R.string.account_bilibili_cookie_input_title),
            onConfirm = { viewModel.saveBilibiliCookie(it) },
            onDismiss = { viewModel.hideBilibiliCookieDialog() }
        )
    }

    // 抖音 Cookie Dialog
    if (uiState.showDouyinCookieDialog) {
        CookieInputDialog(
            title = stringResource(R.string.account_douyin_cookie_input_title),
            onConfirm = { viewModel.saveDouyinCookie(it) },
            onDismiss = { viewModel.hideDouyinCookieDialog() }
        )
    }
}

@Composable
private fun AccountSectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AccountCard(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    contentRight: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (contentRight != null) {
                contentRight()
            } else if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun CookieInputDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var cookieText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = cookieText,
                onValueChange = { cookieText = it },
                label = { Text(stringResource(R.string.account_cookie_label)) },
                placeholder = { Text(stringResource(R.string.account_cookie_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 8
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(cookieText) },
                enabled = cookieText.isNotBlank()
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
