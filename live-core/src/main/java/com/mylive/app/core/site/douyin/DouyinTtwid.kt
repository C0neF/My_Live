package com.mylive.app.core.site.douyin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Supplies the anonymous `ttwid` cookie Douyin issues to a normal browser.
 *
 * The web client mints it with a POST to ByteDance's ttwid union register, and the live site
 * repeats it on `Set-Cookie` from `https://live.douyin.com/`. A successful value is cached until
 * the cookie's own `Max-Age` (capped) or a short default TTL. [invalidate] drops that cache so the
 * next read fetches a replacement after Douyin rejects the current one. If both endpoints fail,
 * [FALLBACK_COOKIE] is returned and not treated as a fresh success, so a later read tries again
 * once the backoff elapses.
 */
interface DouyinTtwidSource {
    suspend fun current(forceRefresh: Boolean = false): String
    fun invalidate() {}
}

class DouyinTtwidProvider(
    client: OkHttpClient,
    private val userAgent: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val fallbackCookie: String = FALLBACK_COOKIE,
    private val registerUrl: String = REGISTER_URL,
    private val liveHomeUrl: String = LIVE_HOME
) : DouyinTtwidSource {
    private val http = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
    private val lock = Any()
    @Volatile private var cached: String? = null
    @Volatile private var expiresAtMillis: Long = 0
    @Volatile private var failedUntilMillis: Long = 0

    override fun invalidate() {
        synchronized(lock) {
            cached = null
            expiresAtMillis = 0
            failedUntilMillis = 0
        }
    }

    override suspend fun current(forceRefresh: Boolean): String {
        if (forceRefresh) invalidate()
        val now = clock()
        synchronized(lock) {
            cached?.takeIf { now < expiresAtMillis }?.let { return it }
            if (now < failedUntilMillis) return fallbackCookie
        }
        val issued = withContext(Dispatchers.IO) { fetch(now) }
        synchronized(lock) {
            if (issued == null) {
                cached = null
                expiresAtMillis = 0
                failedUntilMillis = now + FAILURE_BACKOFF_MS
                return fallbackCookie
            }
            cached = issued.cookie
            expiresAtMillis = issued.expiresAtMillis
            failedUntilMillis = 0
            return issued.cookie
        }
    }

    private fun fetch(now: Long): Issued? =
        readTtwid(registerRequest(), now) ?: readTtwid(homepageRequest(), now)

    private fun registerRequest(): Request = Request.Builder()
        .url(registerUrl)
        .header("User-Agent", userAgent)
        .header("Referer", LIVE_HOME)
        .header("Content-Type", "application/json")
        .post(REGISTER_BODY.toRequestBody(JSON))
        .build()

    private fun homepageRequest(): Request = Request.Builder()
        .url(liveHomeUrl)
        .header("User-Agent", userAgent)
        .header("Referer", LIVE_HOME)
        .get()
        .build()

    private fun readTtwid(request: Request, now: Long): Issued? = try {
        http.newCall(request).execute().use { response ->
            response.headers("Set-Cookie").firstNotNullOfOrNull { header ->
                cookieHeader(header, now)
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun cookieHeader(setCookie: String, now: Long): Issued? {
        val pair = setCookie.substringBefore(';').trim()
        val separator = pair.indexOf('=')
        if (separator <= 0) return null
        val name = pair.substring(0, separator).trim()
        val value = pair.substring(separator + 1).trim()
        if (!name.equals("ttwid", ignoreCase = true) || value.isEmpty()) return null
        if (value.any { it == ';' || it == '\r' || it == '\n' }) return null
        val maxAgeSeconds = MAX_AGE.find(setCookie)?.groupValues?.get(1)?.toLongOrNull()
        val ttl = when (maxAgeSeconds) {
            null -> DEFAULT_TTL_MS
            else -> (maxAgeSeconds * 1000L).coerceIn(1_000L, MAX_TTL_MS)
        }
        return Issued(cookie = "ttwid=$value", expiresAtMillis = now + ttl)
    }

    private data class Issued(val cookie: String, val expiresAtMillis: Long)

    companion object {
        const val REGISTER_URL = "https://ttwid.bytedance.com/ttwid/union/register/"
        const val LIVE_HOME = "https://live.douyin.com/"

        /**
         * Last-resort anonymous cookie. Douyin used to accept this static `ttwid`; it is only used
         * when a fresh one cannot be minted.
         */
        const val FALLBACK_COOKIE =
            "ttwid=1%7CB1qls3GdnZhUov9o2NxOMxxYS2ff6OSvEWbv0ytbES4%7C1680522049%7C280d802d6d478e3e78d0c807f7c487e7ffec0ae4e5fdd6a0fe74c3c6af149511"

        /**
         * Body Douyin's web SDK posts to the union register endpoint to be issued a `ttwid`.
         * `aid` 1768 is the live-web application id that endpoint expects.
         */
        internal const val REGISTER_BODY =
            """{"region":"cn","aid":1768,"needFid":false,"service":"www.ixigua.com","migrate_info":{"ticket":"","source":"node"},"cbUrlProtocol":"https","union":true}"""

        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val MAX_AGE = Regex("""(?i)\bmax-age=(\d+)""")
        internal const val DEFAULT_TTL_MS = 2 * 60 * 60 * 1000L
        private const val MAX_TTL_MS = 24 * 60 * 60 * 1000L
        private const val FAILURE_BACKOFF_MS = 60_000L
    }
}
