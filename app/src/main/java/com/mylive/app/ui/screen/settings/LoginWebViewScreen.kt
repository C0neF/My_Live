package com.mylive.app.ui.screen.settings

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.R

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginWebViewScreen(
    navigator: Navigator,
    viewModel: AccountViewModel = hiltViewModel()
) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("哔哩哔哩账号登录") },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (!isExiting) {
                                isExiting = true
                                navigator.navigate(
                                    route = Route.SettingsAccountLoginQr,
                                    popUpToRoute = Route.SettingsAccountLoginWebview::class.java,
                                    inclusive = true
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "二维码登录"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("二维码登录")
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
            AndroidView(
                factory = { context ->
                    // Clear previous cookies to ensure a fresh login
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.removeAllCookies(null)
                    cookieManager.flush()

                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true

                        // Set mobile User-Agent matching mylive
                        settings.userAgentString = "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1 Edg/118.0.0.0"

                        webViewClient = object : WebViewClient() {
                            private var hasIntercepted = false

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url ?: return false
                                val host = url.host ?: ""
                                if (host == "m.bilibili.com" || host == "www.bilibili.com") {
                                    checkBiliCookies()
                                    return true
                                }
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                checkBiliCookies()
                            }

                            override fun onLoadResource(view: WebView?, url: String?) {
                                super.onLoadResource(view, url)
                                checkBiliCookies()
                            }

                            private fun checkBiliCookies() {
                                if (hasIntercepted) return
                                val cookies = cookieManager.getCookie("https://bilibili.com") ?: ""
                                if (cookies.contains("SESSDATA") &&
                                    cookies.contains("bili_jct") &&
                                    cookies.contains("DedeUserID")) {
                                    hasIntercepted = true
                                    // Save cookie and return
                                    viewModel.saveBilibiliCookie(cookies)
                                    post {
                                        handleBack()
                                    }
                                }
                            }
                        }

                        loadUrl("https://passport.bilibili.com/login")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
