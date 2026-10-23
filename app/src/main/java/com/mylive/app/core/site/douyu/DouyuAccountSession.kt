package com.mylive.app.core.site.douyu

import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.site.LiveSiteAccount
import com.mylive.app.core.site.LiveSiteAccountStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

internal const val DOUYU_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
internal const val DOUYU_ANONYMOUS_DID = "10000000000000000000000000001501"

/** Cookie values remain opaque, including URL encoding and embedded '=' characters. */
internal fun douyuCookiePairs(raw: String): Map<String, String> {
    val input = raw.lineSequence().firstOrNull { it.trimStart().startsWith("Cookie:", true) }
        ?.trim()?.substringAfter(':') ?: raw.trim()
    if (input.length > 65_536) return emptyMap()
    return buildMap {
        input.split(';').forEach { part ->
            val index = part.indexOf('=')
            if (index > 0) {
                val key = part.substring(0, index).trim()
                val value = part.substring(index + 1).trim()
                if (key.matches(Regex("[A-Za-z0-9_\\-]+")) && value.isNotEmpty() &&
                    value.none { it == '\r' || it == '\n' || it == '\u0000' }
                ) put(key, value)
            }
        }
    }
}

fun normalizeDouyuCookie(raw: String): String = douyuCookiePairs(raw).entries
    .joinToString("; ") { "${it.key}=${it.value}" }

internal fun mergeDouyuCookies(vararg values: String): String = values
    .fold(linkedMapOf<String, String>()) { all, value -> all.apply { putAll(douyuCookiePairs(value)) } }
    .entries.joinToString("; ") { "${it.key}=${it.value}" }

internal fun douyuJwtExpiryMillis(token: String?): Long? = runCatching {
    val parts = token.orEmpty().split('.')
    if (parts.size != 3) return null
    val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
    val seconds = JSONObject(payload).optLong("exp", 0L)
    seconds.takeIf { it in 1..(Long.MAX_VALUE / 1000) }?.times(1000)
}.getOrNull()

data class DouyuCookieInspection(
    val configured: Boolean,
    val hasLoginToken: Boolean,
    val canRenew: Boolean,
    val expiresAtMillis: Long?,
    val renewalExpiresAtMillis: Long?
)

fun inspectDouyuCookie(raw: String): DouyuCookieInspection {
    val pairs = douyuCookiePairs(raw)
    return DouyuCookieInspection(
        configured = pairs.isNotEmpty(),
        hasLoginToken = hasDouyuLoginToken(pairs),
        canRenew = !pairs["LTP0"].isNullOrEmpty() && !pairs["dy_did"].isNullOrEmpty(),
        expiresAtMillis = douyuJwtExpiryMillis(pairs["acf_jwt_token"]),
        renewalExpiresAtMillis = douyuJwtExpiryMillis(pairs["LTP0"])
    )
}

private fun hasDouyuLoginToken(pairs: Map<String, String>): Boolean =
    !pairs["dy_auth"].isNullOrEmpty() || !pairs["acf_jwt_token"].isNullOrEmpty() ||
        (!pairs["acf_auth"].isNullOrEmpty() && !pairs["acf_uid"].isNullOrEmpty())

/** Passport renewal credentials are never forwarded to playback APIs or third-party CDNs. */
internal class DouyuRequestSession(raw: String) {
    private val pairs = douyuCookiePairs(raw)
    val authenticated: Boolean = hasDouyuLoginToken(pairs)
    val deviceId: String = pairs["dy_did"] ?: pairs["acf_did"] ?: DOUYU_ANONYMOUS_DID
    val cookie: String = pairs.filterKeys { it != "LTP0" && it != "PHPSESSID" }
        .toMutableMap().apply { putIfAbsent("dy_did", deviceId); putIfAbsent("acf_did", deviceId) }
        .entries.joinToString("; ") { "${it.key}=${it.value}" }

    fun headers(roomId: String = ""): Map<String, String> = mapOf(
        "User-Agent" to DOUYU_USER_AGENT,
        "Referer" to "https://www.douyu.com/" + roomId,
        "Origin" to "https://www.douyu.com",
        "Cookie" to cookie
    )

    fun mediaHeaders(roomId: String): Map<String, String> = headers(roomId) +
        ("Cookie" to "dy_did=$deviceId; acf_did=$deviceId")

    override fun toString(): String = "DouyuRequestSession(credentials=redacted)"
}

@Singleton
class DouyuAccountSession internal constructor(
    private val accounts: LiveSiteAccountStore,
    private val httpClient: HttpClient,
    private val nowMillis: () -> Long
) {
    @Inject constructor(accounts: LiveSiteAccountStore, httpClient: HttpClient) :
        this(accounts, httpClient, System::currentTimeMillis)
    private val mutex = Mutex()
    private var attemptedCookie: String? = null
    private var attemptedAtMillis = 0L
    private var rejectedCookie: String? = null
    private var rejectedAtMillis = 0L
    private var renewalVersion = 0L
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    internal suspend fun forRequest(forceRenewal: Boolean = false): DouyuRequestSession = mutex.withLock {
        prepareSession(forceRenewal)
    }

    /** A valid JWT expiry does not guarantee that passport still accepts this login. */
    internal suspend fun renewAfterRejection(rejected: DouyuRequestSession): DouyuRequestSession? = mutex.withLock {
        val original = accounts.cookieFor(LiveSiteAccount.DOUYU)
        val current = DouyuRequestSession(original)
        // Another request or the user may already have renewed, changed accounts, or signed out.
        if (current.cookie != rejected.cookie) {
            return@withLock prepareSession(forceRenewal = false).takeIf { it.authenticated }
        }
        val inspection = inspectDouyuCookie(original)
        val now = nowMillis()
        if (!current.authenticated || !inspection.canRenew ||
            inspection.renewalExpiresAtMillis?.let { it <= now } == true
        ) return@withLock null
        if (rejectedCookie == original && now >= rejectedAtMillis && now - rejectedAtMillis < 60_000) {
            return@withLock null
        }
        rejectedCookie = original
        rejectedAtMillis = now
        val previousRenewal = renewalVersion
        val renewed = prepareSession(forceRenewal = true)
        renewed.takeIf {
            it.authenticated && (it.cookie != current.cookie || renewalVersion != previousRenewal)
        }
    }

    // Both request preparation and rejection recovery hold mutex while reading and renewing.
    private suspend fun prepareSession(forceRenewal: Boolean): DouyuRequestSession {
        val original = accounts.cookieFor(LiveSiteAccount.DOUYU)
        val inspection = inspectDouyuCookie(original)
        if (!inspection.configured) return DouyuRequestSession("")
        val now = nowMillis()
        val expired = inspection.expiresAtMillis?.let { it <= now } == true
        val renewalExpired = inspection.renewalExpiresAtMillis?.let { it <= now } == true
        val needsRenewal = forceRenewal || !inspection.hasLoginToken ||
            inspection.expiresAtMillis?.let { it <= now + 60_000 } != false
        if (!needsRenewal || !inspection.canRenew || renewalExpired) {
            if (expired || !inspection.hasLoginToken) {
                _message.value = "斗鱼登录态已失效，请重新网页登录"
                error("斗鱼登录态已失效，请重新网页登录")
            }
            return DouyuRequestSession(original)
        }
        // Failed renewal is bounded across concurrent quality/CDN requests.
        if (!forceRenewal && attemptedCookie == original && now >= attemptedAtMillis &&
            now - attemptedAtMillis < 60_000
        ) {
            if (expired || !inspection.hasLoginToken) error("斗鱼登录态续期未成功，请重新登录")
            return DouyuRequestSession(original)
        }
        attemptedCookie = original
        attemptedAtMillis = now
        try {
            val pairs = douyuCookiePairs(original)
            val t = now.toString()
            val renewal = httpClient.getResponse(
                "https://passport.douyu.com/lapi/passport/iframe/safeAuth",
                queryParameters = mapOf("client_id" to "1", "t" to t, "_" to t, "callback" to "axiosJsonpCallback"),
                header = DouyuRequestSession(original).headers() +
                    ("Cookie" to "dy_did=${pairs.getValue("dy_did")}; LTP0=${pairs.getValue("LTP0")}")
            ).use { response ->
                check(response.isSuccessful) { "斗鱼登录态续期请求失败" }
                val returned = response.headers.values("Set-Cookie")
                    .joinToString("; ") { it.substringBefore(';') }
                check(hasDouyuLoginToken(douyuCookiePairs(returned))) { "斗鱼未返回有效登录态" }
                mergeDouyuCookies(original, returned)
            }
            val replaced = accounts.replaceCookieIfUnchanged(LiveSiteAccount.DOUYU, original, renewal)
            val current = if (replaced) renewal else accounts.cookieFor(LiveSiteAccount.DOUYU)
            if (replaced) renewalVersion++
            attemptedCookie = current
            _message.value = if (replaced) "斗鱼登录态已续期" else null
            return DouyuRequestSession(current)
        } catch (cancelled: CancellationException) {
            // Leaving a room must not put the next entry into a failed-renewal cooldown.
            attemptedCookie = null
            if (rejectedCookie == original) rejectedCookie = null
            throw cancelled
        } catch (_: Exception) {
            // The old credential is retained; no anonymous fallback hides an expired login.
            val current = accounts.cookieFor(LiveSiteAccount.DOUYU)
            if (current != original) return DouyuRequestSession(current)
            _message.value = "斗鱼登录态续期未成功，已保留原配置"
            if (expired || !inspection.hasLoginToken) error("斗鱼登录态续期未成功，请重新登录")
            return DouyuRequestSession(original)
        }
    }

    suspend fun renew(): Boolean = try {
        mutex.withLock {
            val previousRenewal = renewalVersion
            prepareSession(forceRenewal = true)
            renewalVersion != previousRenewal
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) { false }
}
