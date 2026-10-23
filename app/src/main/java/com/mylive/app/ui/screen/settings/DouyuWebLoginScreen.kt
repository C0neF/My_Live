package com.mylive.app.ui.screen.settings

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.mylive.app.R
import com.mylive.app.core.site.douyu.DOUYU_USER_AGENT
import com.mylive.app.core.site.douyu.inspectDouyuCookie
import com.mylive.app.core.site.douyu.mergeDouyuCookies
import com.mylive.app.core.site.douyu.douyuCookiePairs
import com.mylive.app.ui.navigation.Navigator

internal val douyuLoginCookieUrls = listOf(
    "https://www.douyu.com", "https://douyu.com", "https://passport.douyu.com"
)

internal fun isDouyuLoginPage(url: String?): Boolean {
    val uri = url?.let(Uri::parse) ?: return false
    val host = uri.host.orEmpty().lowercase()
    return uri.scheme.equals("https", true) && (host == "douyu.com" || host.endsWith(".douyu.com"))
}

internal fun readDouyuWebCookies(manager: CookieManager): String = mergeDouyuCookies(
    *douyuLoginCookieUrls.map { manager.getCookie(it).orEmpty() }.toTypedArray()
)

/** Local removal only: it does not call Douyu's server-side logout endpoint. */
internal fun clearDouyuWebCookies() {
    val manager = CookieManager.getInstance()
    douyuLoginCookieUrls.forEach { url ->
        douyuCookiePairs(manager.getCookie(url).orEmpty()).keys.forEach { name ->
            val expired = "$name=; Path=/; Max-Age=0; Secure"
            manager.setCookie(url, expired)
            manager.setCookie(url, "$expired; Domain=.douyu.com")
        }
    }
    manager.flush()
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DouyuWebLoginScreen(navigator: Navigator, viewModel: AccountViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val handleBack = navigator.rememberRouteActions().onBack
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var saving by remember { mutableStateOf(false) }
    val manager = remember { CookieManager.getInstance() }

    fun save(manual: Boolean) {
        if (saving) return
        val cookie = readDouyuWebCookies(manager)
        val info = inspectDouyuCookie(cookie)
        // Automatic completion waits for the passport token as well as website cookies.
        if ((manual && (info.hasLoginToken || info.canRenew)) || (!manual && info.canRenew)) {
            saving = true
            manager.flush()
            viewModel.saveDouyuCookie(
                cookie,
                onSaved = {
                    Toast.makeText(context, "斗鱼登录配置已保存", Toast.LENGTH_SHORT).show()
                    handleBack()
                },
                onSaveFailed = {
                    saving = false
                    Toast.makeText(context, "保存失败，请重试", Toast.LENGTH_SHORT).show()
                }
            )
        } else if (manual) {
            Toast.makeText(context, "请先在斗鱼页面完成登录", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(stringResource(R.string.account_web_login_title, stringResource(R.string.account_douyu_section)))
            },
            navigationIcon = { TextButton(onClick = handleBack) { Text("返回") } },
            actions = {
                TextButton(onClick = { webView?.reload() }, enabled = !saving) { Text("刷新") }
                TextButton(onClick = { save(true) }, enabled = !saving) { Text(if (saving) "保存中…" else "保存") }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).testTag("douyu_web_login")) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    "在斗鱼官方页面扫码或验证码登录，完成后保存到本机。保存后直接返回即可。",
                    Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall
                )
            }
            if (progress < 1f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        // Never clear the shared cookie jar: Bilibili and Douyin may also use it.
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.userAgentString = DOUYU_USER_AGENT
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            private fun fitPassportPage(view: WebView?, url: String?) {
                                if (view == null || view.width == 0 || !isDouyuLoginPage(url) ||
                                    !url?.toUri()?.host.equals("passport.douyu.com", true)
                                ) return
                                // Passport fixes its layout at 1200px and disables zoom on phones.
                                val scale = (view.width / (view.resources.displayMetrics.density * 1200f))
                                    .coerceAtMost(1f)
                                view.evaluateJavascript(
                                    """(function(){var v=document.querySelector('meta[name="viewport"]');if(v)v.setAttribute('content','width=1200, initial-scale=$scale, minimum-scale=$scale, maximum-scale=3, user-scalable=yes');})();""",
                                    null
                                )
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                                !isSafeLoginWebUrl(request?.url)

                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                fitPassportPage(view, url)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (isDouyuLoginPage(url)) {
                                    fitPassportPage(view, url)
                                    save(false)
                                }
                            }

                            override fun onLoadResource(view: WebView?, url: String?) {
                                if (isDouyuLoginPage(view?.url)) save(false)
                            }
                        }
                        loadUrl("https://passport.douyu.com/")
                    }
                },
                onRelease = { view ->
                    webView = null
                    view.stopLoading()
                    view.destroy()
                }
            )
        }
    }
}
