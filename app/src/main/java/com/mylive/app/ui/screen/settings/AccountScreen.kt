package com.mylive.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.R
import com.mylive.app.core.site.douyin.DouyinCookieInspector
import com.mylive.app.core.site.douyu.DouyuCookieInspection
import com.mylive.app.core.site.douyu.inspectDouyuCookie
import com.mylive.app.ui.component.settings.SettingsHairlineDivider
import com.mylive.app.ui.component.settings.SettingsInsetCard
import com.mylive.app.ui.component.settings.SettingsSectionHeader
import com.mylive.app.ui.layout.AdaptivePageFrame
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.layout.pageContentWindowInsets
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.theme.Icons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    navigator: Navigator,
    embedded: Boolean = false,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    val handleBack = navigator.rememberRouteActions().onBack

    LaunchedEffect(uiState.message) {
        val msg = uiState.message ?: return@LaunchedEffect
        val text = when (msg) {
            AccountMessage.BilibiliCookieSaved -> resources.getString(R.string.account_bilibili_cookie_saved)
            AccountMessage.DouyinCookieSaved -> resources.getString(R.string.account_douyin_cookie_saved)
            AccountMessage.BilibiliLoggedOut -> resources.getString(R.string.account_bilibili_logged_out)
            AccountMessage.DouyinLoggedOut -> resources.getString(R.string.account_douyin_logged_out)
            is AccountMessage.DouyuNotice -> msg.text
        }
        snackbarHostState.showSnackbar(text)
        viewModel.clearMessage()
    }

    Scaffold(
        containerColor = if (embedded) Color.Transparent else MaterialTheme.colorScheme.background,
        contentWindowInsets = pageContentWindowInsets(embedded),
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.account_title)) },
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AdaptivePageFrame(Modifier.padding(padding), LocalAppLayout.current.settingsMaxWidthDp) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (embedded) Color.Transparent else MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 16.dp,
                        top = if (embedded) 10.dp else 12.dp,
                        end = 16.dp,
                        bottom = 12.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // B站 Section
                Column {
                    SettingsSectionHeader(stringResource(R.string.account_bilibili_section))
                    SettingsInsetCard(embedded = embedded) {
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
                        SettingsHairlineDivider()
                        if (uiState.isLoggedInBiliBili) {
                            AccountCard(
                                title = stringResource(R.string.account_clear_cookie_action),
                                subtitle = stringResource(R.string.account_clear_cookie_subtitle),
                                isDanger = true,
                                showDivider = false,
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
                                showDivider = false,
                                onClick = { viewModel.showBilibiliCookieDialog() }
                            )
                        }
                    }
                }

                // 抖音 Section
                Column {
                    SettingsSectionHeader(stringResource(R.string.account_douyin_section))
                    SettingsInsetCard(embedded = embedded) {
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
                                isDanger = true,
                                showDivider = !uiState.isLoggedInDouyin,
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
                                showDivider = false,
                                onClick = { viewModel.showDouyinCookieDialog() }
                            )
                        }
                    }
                }

                // 斗鱼 Section
                Column {
                    SettingsSectionHeader(stringResource(R.string.account_douyu_section))
                    SettingsInsetCard(embedded = embedded) {
                        val hasRenewRow = uiState.douyuInspection.canRenew && !uiState.douyuBusy
                        val hasClearRow = uiState.douyuInspection.configured && !uiState.douyuBusy
                        AccountCard(
                            title = stringResource(R.string.account_status_label),
                            subtitle = douyuCookieStatusDescription(uiState.douyuInspection),
                            contentRight = {
                                Text(
                                    text = if (uiState.douyuBusy) "处理中…" else if (uiState.douyuInspection.hasLoginToken) "已保存" else "未配置",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (uiState.douyuInspection.hasLoginToken) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
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
                            showDivider = hasRenewRow || hasClearRow,
                            onClick = { viewModel.showDouyuCookieDialog() }
                        )
                        if (hasRenewRow) {
                            AccountCard(
                                title = "立即续期",
                                showDivider = hasClearRow,
                                onClick = viewModel::renewDouyuCookie
                            )
                        }
                        if (hasClearRow) {
                            AccountCard(
                                title = stringResource(R.string.account_clear_cookie_action),
                                subtitle = stringResource(R.string.account_clear_cookie_subtitle),
                                isDanger = true,
                                showDivider = false,
                                onClick = {
                                    clearDouyuWebCookies()
                                    viewModel.logoutDouyu()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.showBilibiliCookieDialog) {
        CookieInputDialog(
            title = stringResource(R.string.account_bilibili_cookie_input_title),
            onConfirm = { viewModel.saveBilibiliCookie(it) },
            onDismiss = { viewModel.hideBilibiliCookieDialog() }
        )
    }

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
private fun AccountCard(
    title: String,
    subtitle: String? = null,
    isDanger: Boolean = false,
    showDivider: Boolean = true,
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
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (showDivider) {
            SettingsHairlineDivider()
        }
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
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        text = {
            OutlinedTextField(
                value = cookieText,
                onValueChange = { cookieText = it },
                label = { Text(stringResource(R.string.account_cookie_label)) },
                placeholder = { Text(stringResource(R.string.account_cookie_placeholder)) },
                supportingText = inputHint?.let { hint ->
                    { Text(hint(cookieText)) }
                },
                shape = RoundedCornerShape(12.dp),
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
