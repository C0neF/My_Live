package com.mylive.app.ui.screen.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.core.model.LivePlayUrl
import com.mylive.app.core.site.LiveSite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject

/**
 * Browser-like User-Agent for short-link redirect resolution. Douyin's `v.douyin.com`
 * (and similar) treat a non-browser UA such as okhttp's default as a bot and bounce
 * the request to `https://www.douyin.com` instead of 302-ing to the real room, which
 * then fails to parse. The Dart reference happens to pass because Dio's default UA is
 * accepted; we set an explicit browser UA to be safe.
 */
private const val PARSE_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

/**
 * Douyin short-link matcher. The short code may contain '_' (e.g. `6yEygkv2_Fo`),
 * so the charset MUST include underscore. A charset of only `[a-zA-Z0-9]` truncates
 * the code at the underscore, and the truncated link is bounced to `www.douyin.com`
 * (unparseable). Mirrors the Dart reference's `[\d\w]+` (where `\w` includes `_`).
 */
internal val V_DOUYIN_SHORTLINK_REGEX = Regex("""https?://v\.douyin\.com/[A-Za-z0-9_]+""")

sealed class ParseEvent {
    data class NavigateToRoom(val roomId: String, val siteId: String) : ParseEvent()
    data class ShowToast(val message: String) : ParseEvent()
    data class ShowQualitySelect(val qualities: List<LivePlayQuality>, val onSelect: (LivePlayQuality) -> Unit) : ParseEvent()
    data class ShowLineSelect(val playUrl: LivePlayUrl) : ParseEvent()
}

@HiltViewModel
class ParseViewModel @Inject constructor(
    private val sites: Set<@JvmSuppressWildcards LiveSite>,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _event = MutableSharedFlow<ParseEvent>(replay = 0)
    val event: SharedFlow<ParseEvent> = _event.asSharedFlow()

    fun jumpToRoom(url: String) {
        if (url.isBlank()) {
            sendToast("链接不能为空")
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val parseResult = parseUrl(url)
            _isLoading.value = false
            if (parseResult != null) {
                _event.emit(ParseEvent.NavigateToRoom(parseResult.first, parseResult.second))
            } else {
                sendToast("无法解析此链接")
            }
        }
    }

    fun getPlayUrl(url: String) {
        if (url.isBlank()) {
            sendToast("链接不能为空")
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val parseResult = parseUrl(url)
            if (parseResult == null) {
                _isLoading.value = false
                sendToast("无法解析此链接")
                return@launch
            }
            val roomId = parseResult.first
            val siteId = parseResult.second
            val site = sites.find { it.id == siteId }
            if (site == null) {
                _isLoading.value = false
                sendToast("未找到对应平台解析器")
                return@launch
            }

            try {
                val detail = site.getRoomDetail(roomId)
                val qualities = site.getPlayQualites(detail)
                _isLoading.value = false
                if (qualities.isEmpty()) {
                    sendToast("读取直链失败,无法读取清晰度")
                    return@launch
                }

                _event.emit(ParseEvent.ShowQualitySelect(qualities) { selectedQuality ->
                    viewModelScope.launch {
                        _isLoading.value = true
                        try {
                            val playUrl = site.getPlayUrls(detail, selectedQuality)
                            _isLoading.value = false
                            _event.emit(ParseEvent.ShowLineSelect(playUrl))
                        } catch (e: Exception) {
                            _isLoading.value = false
                            Timber.e(e, "getPlayUrls failed")
                            sendToast("读取直链失败")
                        }
                    }
                })
            } catch (e: Exception) {
                _isLoading.value = false
                Timber.e(e, "getRoomDetail/Qualities failed")
                sendToast("读取直链失败")
            }
        }
    }

    private suspend fun parseUrl(url: String): Pair<String, String>? {
        var id = ""
        val trimmedUrl = url.trim()
        
        if (trimmedUrl.contains("bilibili.com")) {
            val regExp = Regex("""bilibili\.com/([\d|\w]+)""")
            id = regExp.find(trimmedUrl)?.groupValues?.get(1) ?: ""
            return if (id.isNotEmpty()) Pair(id, "bilibili") else null
        }

        if (trimmedUrl.contains("b23.tv")) {
            val btvReg = Regex("""https?://b23\.tv/[0-9a-z-A-Z]+""")
            val u = btvReg.find(trimmedUrl)?.value ?: ""
            val location = getLocation(u)
            return if (location.isNotEmpty()) parseUrl(location) else null
        }

        if (trimmedUrl.contains("douyu.com")) {
            var regExp = Regex("""douyu\.com/([\d|\w]+)""")
            if (trimmedUrl.contains("topic")) {
                regExp = Regex("""[?&]rid=(\d+)""")
            }
            id = regExp.find(trimmedUrl)?.groupValues?.get(1) ?: ""
            return if (id.isNotEmpty()) Pair(id, "douyu") else null
        }

        if (trimmedUrl.contains("huya.com")) {
            val regExp = Regex("""huya\.com/([\d|\w]+)""")
            id = regExp.find(trimmedUrl)?.groupValues?.get(1) ?: ""
            return if (id.isNotEmpty()) Pair(id, "huya") else null
        }

        if (trimmedUrl.contains("live.douyin.com")) {
            val regExp = Regex("""live\.douyin\.com/([\d|\w]+)""")
            id = regExp.find(trimmedUrl)?.groupValues?.get(1) ?: ""
            return if (id.isNotEmpty()) Pair(id, "douyin") else null
        }

        if (trimmedUrl.contains("webcast.amemv.com")) {
            val regExp = Regex("""reflow/(\d+)""")
            id = regExp.find(trimmedUrl)?.groupValues?.get(1) ?: ""
            return if (id.isNotEmpty()) Pair(id, "douyin") else null
        }

        if (trimmedUrl.contains("v.douyin.com")) {
            // Douyin short links 302 to the real room only for browser-like requests
            // (see PARSE_USER_AGENT) and are requested WITH a trailing slash, matching
            // the Dart reference's captured short URL. The code may contain '_'.
            var u = V_DOUYIN_SHORTLINK_REGEX.find(trimmedUrl)?.value ?: ""
            if (u.isNotEmpty() && !u.endsWith("/")) u += "/"
            val location = getLocation(u)
            return if (location.isNotEmpty()) parseUrl(location) else null
        }

        return null
    }

    private suspend fun getLocation(url: String): String = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext ""
        val noRedirectClient = okHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", PARSE_USER_AGENT)
            .build()
        try {
            noRedirectClient.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    val location = response.header("Location") ?: ""
                    Timber.d("getLocation %s -> [%d] %s", url, response.code, location)
                    return@withContext location
                }
                Timber.w("getLocation %s returned non-redirect %d", url, response.code)
            }
        } catch (e: Exception) {
            Timber.e(e, "getLocation redirect failed for $url")
        }
        ""
    }

    private fun sendToast(msg: String) {
        viewModelScope.launch {
            _event.emit(ParseEvent.ShowToast(msg))
        }
    }
}
