package com.mylive.app.ui.screen.settings

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.R

internal fun parseCookiePairPreservingEquals(pair: String): Pair<String, String>? {
    val separatorIndex = pair.indexOf('=')
    if (separatorIndex <= 0) return null
    val key = pair.substring(0, separatorIndex).trim()
    val value = pair.substring(separatorIndex + 1).trim()
    if (key.isEmpty() || value.isEmpty()) return null
    return key to value
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DouyinWebLoginScreen(
    navigator: Navigator,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var isExiting by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var progressVal by remember { mutableStateOf(0f) }

    val handleBack: () -> Unit = {
        if (!isExiting) {
            isExiting = true
            navigator.goBack()
        }
    }

    BackHandler(enabled = !isExiting) {
        handleBack()
    }

    // Read cookie utility
    val getDouyinCookies: () -> String = {
        val cookieManager = CookieManager.getInstance()
        val domains = listOf(
            "https://www.douyin.com",
            "https://douyin.com",
            "https://live.douyin.com"
        )
        val cookieMap = mutableMapOf<String, String>()
        for (domain in domains) {
            val cookieStr = cookieManager.getCookie(domain) ?: ""
            cookieStr.split(";").forEach { pair ->
                val parsedPair = parseCookiePairPreservingEquals(pair)
                if (parsedPair != null) {
                    val (key, value) = parsedPair
                    cookieMap[key] = value
                }
            }
        }
        cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    val hasLoginState: (String) -> Boolean = { cookie ->
        val lower = cookie.lowercase()
        lower.contains("sessionid=") ||
                lower.contains("sid_guard=") ||
                lower.contains("sid_tt=") ||
                lower.contains("uid_tt=") ||
                lower.contains("login_status=1")
    }

    val saveAndFinish: (Boolean) -> Unit = { silent ->
        val cookies = getDouyinCookies()
        if (cookies.isNotEmpty() && hasLoginState(cookies)) {
            viewModel.saveDouyinCookie(cookies)
            Toast.makeText(context, "抖音登录态已保存", Toast.LENGTH_SHORT).show()
            handleBack()
        } else {
            if (!silent) {
                Toast.makeText(context, "未检测到登录态，请在页面中完成登录后再点击保存", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("抖音网页登录") },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { webViewInstance?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { saveAndFinish(false) }) {
                        Icon(Icons.Default.Save, contentDescription = "保存")
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
            // Description Banner
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "提示：用抖音手机 App 扫码登录，登录成功后会自动保存，或点击右上角保存按钮手动保存。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (progressVal < 1f) {
                LinearProgressIndicator(
                    progress = { progressVal },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AndroidView(
                factory = { ctx ->
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.removeAllCookies(null)
                    cookieManager.flush()

                    WebView(ctx).apply {
                        webViewInstance = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true

                        // Use Desktop User-Agent to prevent app redirection and show web login QR code correctly
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36 Edg/125.0.0.0"

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                progressVal = 1f
                                saveAndFinish(true)
                            }

                            override fun onLoadResource(view: WebView?, url: String?) {
                                super.onLoadResource(view, url)
                                saveAndFinish(true)
                            }
                        }

                        loadUrl("https://www.douyin.com/")
                    }
                },
                onRelease = { view ->
                    if (webViewInstance === view) {
                        webViewInstance = null
                    }
                    view.stopLoading()
                    view.destroy()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
