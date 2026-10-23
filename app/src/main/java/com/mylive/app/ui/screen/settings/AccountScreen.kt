package com.mylive.app.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.R
import com.mylive.app.core.site.douyin.DouyinCookieInspector
import com.mylive.app.core.site.douyu.DouyuCookieInspection
import com.mylive.app.core.site.douyu.inspectDouyuCookie
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    navigator: Navigator,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val handleBack = navigator.rememberRouteActions().onBack

    // Show message snackbar
    LaunchedEffect(uiState.message) {
        val msg = uiState.message ?: return@LaunchedEffect
        val text = when (msg) {
            AccountMessage.BilibiliCookieSaved -> context.getString(R.string.account_bilibili_cookie_saved)
            AccountMessage.DouyinCookieSaved -> context.getString(R.string.account_douyin_cookie_saved)
            AccountMessage.BilibiliLoggedOut -> context.getString(R.string.account_bilibili_logged_out)
            AccountMessage.DouyinLoggedOut -> context.getString(R.string.account_douyin_logged_out)
            is AccountMessage.DouyuNotice -> msg.text
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
                    title = stringResource(R.string.account_clear_cookie_action),
                    subtitle = stringResource(R.string.account_clear_cookie_subtitle),
                    onClick = { viewModel.logoutBiliBili() }
                )
            } else {
                AccountCard(
                    title = stringResource(R.string.account_web_login),
                    subtitle = stringResource(R.string.account_web_login_subtitle),
                    onClick = { navigator.navigate(Route.SettingsAccountLoginWebview) }
                )
                AccountCard(
                    title = stringResource(R.string.account_cookie_login),
                    subtitle = stringResource(R.string.account_cookie_login_subtitle),
                    onClick = { viewModel.showBilibiliCookieDialog() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 抖音 Section
            AccountSectionHeader(title = stringResource(R.string.account_douyin_section))
            
            AccountCard(
                title = stringResource(R.string.account_status_label),
                subtitle = douyinCookieStatusDescription(uiState.douyinCookieInspection),
                contentRight = {
                    Text(
                        text = douyinCookieStatusLabel(uiState.douyinCookieInspection.kind),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (uiState.isLoggedInDouyin) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            if (uiState.hasDouyinCookie) {
                AccountCard(
                    title = stringResource(R.string.account_clear_cookie_action),
                    subtitle = stringResource(R.string.account_clear_cookie_subtitle),
                    onClick = { viewModel.logoutDouyin() }
                )
            }
            if (!uiState.isLoggedInDouyin) {
                AccountCard(
                    title = stringResource(R.string.account_web_login),
                    subtitle = stringResource(R.string.account_web_login_subtitle),
                    onClick = { navigator.navigate(Route.SettingsAccountDouyinLoginWebview) }
                )
                AccountCard(
                    title = stringResource(R.string.account_cookie_login),
                    subtitle = stringResource(R.string.account_cookie_login_subtitle),
                    onClick = { viewModel.showDouyinCookieDialog() }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            AccountSectionHeader(title = stringResource(R.string.account_douyu_section))
            AccountCard(
                title = stringResource(R.string.account_status_label),
                subtitle = douyuCookieStatusDescription(uiState.douyuInspection),
                contentRight = {
                    Text(
                        if (uiState.douyuBusy) "处理中…" else if (uiState.douyuInspection.hasLoginToken) "已保存" else "未配置",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            AccountCard(
                title = stringResource(R.string.account_web_login),
                subtitle = stringResource(R.string.account_web_login_subtitle),
                onClick = { navigator.navigate(Route.SettingsAccountDouyuLoginWebview) }
            )
            AccountCard(
                title = stringResource(R.string.account_cookie_login),
                subtitle = stringResource(R.string.account_cookie_login_subtitle),
                onClick = { viewModel.showDouyuCookieDialog() }
            )
            if (uiState.douyuInspection.canRenew && !uiState.douyuBusy) {
                AccountCard(title = "立即续期", onClick = viewModel::renewDouyuCookie)
            }
            if (uiState.douyuInspection.configured && !uiState.douyuBusy) {
                AccountCard(
                    title = stringResource(R.string.account_clear_cookie_action),
                    subtitle = stringResource(R.string.account_clear_cookie_subtitle),
                    onClick = {
                        clearDouyuWebCookies()
                        viewModel.logoutDouyu()
                    }
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
            inputHint = { text ->
                douyinCookieStatusDescription(DouyinCookieInspector.inspect(text))
            },
            onConfirm = { viewModel.saveDouyinCookie(it) },
            onDismiss = { viewModel.hideDouyinCookieDialog() }
        )
    }
    if (uiState.showDouyuCookieDialog) {
        CookieInputDialog(
            title = stringResource(R.string.account_douyu_cookie_input_title),
            inputHint = { douyuCookieStatusDescription(inspectDouyuCookie(it)) },
            maskInput = true,
            inputTag = "douyu_cookie_input",
            onConfirm = { viewModel.saveDouyuCookie(it) },
            onDismiss = viewModel::hideDouyuCookieDialog
        )
    }
}

internal fun douyuCookieStatusDescription(info: DouyuCookieInspection): String = when {
    !info.configured -> "支持网页登录或导入 Cookie"
    !info.hasLoginToken && info.canRenew -> "已保存续期凭据，需要续期取得登录状态"
    !info.hasLoginToken -> "仅检测到设备标识，请重新登录"
    info.expiresAtMillis?.let { it <= System.currentTimeMillis() } == true -> "登录状态已过期，请续期或重新登录"
    info.canRenew -> "检测到登录状态，已配置自动续期；实际可用性以平台响应为准"
    else -> "检测到登录状态；未获取到续期凭据，过期后需重新登录"
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
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) Modifier.clickable(onClick = onClick)
                    else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
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
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun CookieInputDialog(
    title: String,
    inputHint: ((String) -> String)? = null,
    maskInput: Boolean = false,
    inputTag: String = "account_cookie_input",
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
                supportingText = inputHint?.let { hint ->
                    { Text(hint(cookieText)) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(inputTag)
                    .heightIn(min = 120.dp),
                visualTransformation = if (maskInput) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = if (maskInput) {
                    KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
                } else KeyboardOptions.Default,
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
