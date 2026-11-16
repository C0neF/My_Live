package com.mylive.desktop

import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.site.LiveSiteAccount
import com.mylive.app.core.site.douyin.DouyinCookieInspector
import com.mylive.app.core.site.douyin.DouyinCookieKind
import com.mylive.app.core.site.douyu.inspectDouyuCookie
import com.mylive.app.core.site.douyu.normalizeDouyuCookie
import okhttp3.Cookie
import org.json.JSONObject
import java.net.URI

data class BilibiliQrChallenge(val key: String, val url: String) {
    override fun toString() = "BilibiliQrChallenge(redacted)"
}
data class BilibiliQrPoll(val code: Int, val cookie: String = "") {
    override fun toString() = "BilibiliQrPoll(code=$code, cookie=redacted)"
}
interface BilibiliQrApi {
    suspend fun challenge(): BilibiliQrChallenge
    suspend fun poll(key: String): BilibiliQrPoll
}

class DesktopLoginService(
    private val http: HttpClient, private val accounts: DesktopAccounts,
    private val passportBase: String = "https://passport.bilibili.com",
    private val apiBase: String = "https://api.bilibili.com"
) : BilibiliQrApi {
    override suspend fun challenge(): BilibiliQrChallenge {
        val response = http.getJson("$passportBase/x/passport-login/web/qrcode/generate") as JSONObject
        require(response.optInt("code", -1) == 0) { "二维码获取失败" }
        val data = response.getJSONObject("data")
        val uri = URI(data.getString("url"))
        require(uri.scheme == "https" && (uri.host == "bilibili.com" || uri.host.orEmpty().endsWith(".bilibili.com"))) { "二维码地址不受支持" }
        return BilibiliQrChallenge(data.getString("qrcode_key"), uri.toString())
    }
    override suspend fun poll(key: String): BilibiliQrPoll = http.getResponse(
        "$passportBase/x/passport-login/web/qrcode/poll", mapOf("qrcode_key" to key)
    ).use { response ->
        require(response.isSuccessful) { "二维码状态暂时不可用" }
        val json = JSONObject(requireNotNull(response.body).string())
        require(json.optInt("code", -1) == 0)
        val data = json.getJSONObject("data")
        BilibiliQrPoll(data.getInt("code"), if (data.getInt("code") == 0)
            Cookie.parseAll(response.request.url, response.headers).joinToString("; ") { "${it.name}=${it.value}" } else "")
    }

    suspend fun describe(account: LiveSiteAccount): String {
        val cookie = accounts.cookieFor(account)
        if (cookie.isBlank()) return "未配置 Cookie"
        return when (account) {
            LiveSiteAccount.BILIBILI -> {
                val response = http.getJson("$apiBase/x/web-interface/nav", header = mapOf("Cookie" to cookie)) as JSONObject
                if (response.optInt("code", -1) == 0 && response.optJSONObject("data")?.optBoolean("isLogin") == true)
                    "已登录：${response.getJSONObject("data").optString("uname", "哔哩哔哩用户")}"
                else "Cookie 未通过平台登录验证，请重新登录"
            }
            LiveSiteAccount.DOUYU -> inspectDouyuCookie(cookie).let { info -> when {
                info.expiresAtMillis?.let { it <= System.currentTimeMillis() } == true -> "令牌已过期${if (info.canRenew) "，可尝试续期" else "，请重新登录"}"
                info.hasLoginToken -> "已配置斗鱼登录令牌${if (info.canRenew) "，支持续期" else ""}"
                info.canRenew -> "已有续期信息，请尝试续期"
                else -> "仅有访客信息，请完成网页登录"
            } }
            LiveSiteAccount.DOUYIN -> when (DouyinCookieInspector.inspect(cookie).kind) {
                DouyinCookieKind.LoginStateShape -> "包含抖音登录字段，实际权限以平台响应为准"
                DouyinCookieKind.TtwidOnly -> "仅有 ttwid，尚未包含完整登录信息"
                else -> "未识别到完整登录信息，请重新登录"
            }
        }
    }
}

fun normalizeDesktopCookie(account: LiveSiteAccount, input: String): String = when (account) {
    LiveSiteAccount.DOUYU -> normalizeDouyuCookie(input)
    LiveSiteAccount.DOUYIN -> DouyinCookieInspector.normalizeInput(input)
    LiveSiteAccount.BILIBILI -> input.lineSequence().firstOrNull { it.trimStart().startsWith("Cookie:", true) }
        ?.substringAfter(':')?.trim() ?: input.trim()
}
