package com.mylive.app.core.site.douyin

import com.mylive.app.core.common.CoreLog
import com.mylive.app.core.common.CoreError
import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.common.WebSocketUtils
import com.mylive.app.core.model.DanmakuArgs
import com.mylive.app.core.model.LiveAnchorItem
import com.mylive.app.core.model.LiveCategory
import com.mylive.app.core.model.LiveCategoryResult
import com.mylive.app.core.model.LiveContributionRankItem
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.core.model.LivePlayUrl
import com.mylive.app.core.model.LiveRoomDetail
import com.mylive.app.core.model.LiveRoomItem
import com.mylive.app.core.model.LiveSearchAnchorResult
import com.mylive.app.core.model.LiveSearchRoomResult
import com.mylive.app.core.model.LiveSubCategory
import com.mylive.app.core.model.LiveSuperChatMessage
import com.mylive.app.core.model.PlayQualityData
import com.mylive.app.core.script.JsEngine
import com.mylive.app.core.site.firstSiteImageUrl
import com.mylive.app.core.site.LiveDanmaku
import com.mylive.app.core.site.LiveSite
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private val DOUYIN_PARTITION_IMAGE_KEYS = listOf(
    "icon",
    "icons",
    "cover",
    "background",
    "avatar_thumb",
    "image",
    "image_url",
    "url",
    "url_list",
    "static_icon"
)

internal fun resolveDouyinPartitionImageUrl(data: Any?): String? {
    return firstSiteImageUrl(data, DOUYIN_PARTITION_IMAGE_KEYS)
}

/**
 * Douyin (抖音) live streaming platform implementation.
 *
 * This is the most complex site implementation, featuring:
 * - A-Bogus URL signing via JsEngine
 * - Request throttling (1200ms minimum interval) to avoid HTTP 444 errors
 * - Three strategies for room detail: roomId API, webRid API, webRid HTML fallback
 * - Default ttwid cookie management + web cookie collection via HEAD request
 * - Category parsing from HTML
 * - Play quality extraction from live_core_sdk_data.pull_data
 */
@Singleton
class DouyinSite @Inject constructor(
    private val httpClient: HttpClient,
    private val jsEngineProvider: Provider<JsEngine>,
    private val okHttpClient: OkHttpClient
) : LiveSite {

    override val id: String = "douyin"
    override val name: String = "抖音直播"

    /** User-provided cookie. When non-empty, overrides the default ttwid cookie. */
    var cookie: String = ""

    /** DouyinSign helper for A-Bogus and signature generation. */
    private val douyinSign: DouyinSign
        get() = DouyinSign(jsEngineProvider.get())

    /** Secure random for generating user unique IDs. */
    private val secureRandom = SecureRandom()

    /**
     * Stream data (`stream_url` JSON) captured per room during [getRoomDetail],
     * keyed by the returned [LiveRoomDetail.roomId] (web_rid).
     *
     * Keyed per room — NOT a single shared field — so that concurrent
     * room-detail loads for different rooms (e.g. the follow-refresh worker,
     * which fans out several [getRoomDetail] calls) cannot overwrite each
     * other and make [getPlayQualites] hand back another room's stream URL.
     * Mirrors the Dart reference, which carries the data on each returned
     * `LiveRoomDetail` instead of on the singleton site.
     */
    private val streamDataByRoom = ConcurrentHashMap<String, JSONObject>()

    /**
     * Short-TTL cache of the per-room web cookie (ttwid/__ac_nonce/msToken),
     * keyed by `"webRid|baseCookieHash"`. Mirrors the Dart reference: avoids a
     * fresh HEAD request to live.douyin.com on every room-detail resolution,
     * which raises the chance of HTTP 444 throttling during room entry.
     */
    private val webCookieCache = ConcurrentHashMap<String, String>()
    private val webCookieCacheAt = ConcurrentHashMap<String, Long>()

    /**
     * Stores the danmaku args from the last [getRoomDetail] call.
     * Used by [getContributionRank] when no explicit detail is provided.
     */
    @Volatile
    var lastDanmakuArgs: DanmakuArgs.Douyin? = null
        private set

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.97 Safari/537.36 Core/1.116.567.400 QQBrowser/19.7.6764.400"
        private const val DEFAULT_REFERER = "https://live.douyin.com"
        private const val DEFAULT_AUTHORITY = "live.douyin.com"

        /**
         * Default ttwid cookie -- only the ttwid field is required to obtain
         * all quality levels (including Blu-ray). LOGIN_STATUS=1 and other
         * fields are optional.
         */
        private const val DEFAULT_COOKIE =
            "ttwid=1%7CB1qls3GdnZhUov9o2NxOMxxYS2ff6OSvEWbv0ytbES4%7C1680522049%7C280d802d6d478e3e78d0c807f7c487e7ffec0ae4e5fdd6a0fe74c3c6af149511"

        /** Minimum interval between room detail requests (milliseconds). */
        private const val ROOM_DETAIL_THROTTLE_MS = 1200L

        /** TTL for the per-room web cookie cache (milliseconds). Mirrors Dart's 5 minutes. */
        private const val WEB_COOKIE_CACHE_TTL_MS = 5 * 60 * 1000L

        /** Timestamp of the last room detail request (companion-level, shared across instances). */
        private val lastRoomDetailRequestAt = AtomicLong(0L)
    }

    // ── Danmaku ─────────────────────────────────────────────────────────────

    override fun getDanmaku(): LiveDanmaku = DouyinDanmaku(WebSocketUtils(okHttpClient), jsEngineProvider.get())

    // ── Headers & Cookie management ─────────────────────────────────────────

    /**
     * Build the base request headers (Authority, Referer, User-Agent, cookie).
     * If [cookie] is set, uses that; otherwise falls back to [DEFAULT_COOKIE].
     */
    private fun getRequestHeaders(): MutableMap<String, String> {
        val headers = mutableMapOf(
            "Authority" to DEFAULT_AUTHORITY,
            "Referer" to DEFAULT_REFERER,
            "User-Agent" to DEFAULT_USER_AGENT
        )
        headers["cookie"] = if (cookie.isNotEmpty()) cookie else DEFAULT_COOKIE
        return headers
    }

    /**
     * Get the merged cookie for a danmaku connection.
     *
     * Merges the base cookie with web cookies obtained via a HEAD request to
     * the live room page. When a user cookie is set, the user cookie takes
     * priority over web cookies.
     */
    private suspend fun getDanmakuCookie(webRid: String): String {
        val requestHeaders = getRequestHeaders()
        val baseCookie = requestHeaders["cookie"] ?: ""
        return try {
            val webCookie = withTimeout(5000L) { getWebCookie(webRid) }
            mergeCookieValues(baseCookie, webCookie, preferBase = cookie.isNotEmpty())
        } catch (e: Exception) {
            CoreLog.error(e)
            baseCookie
        }
    }

    /**
     * Collect web cookies by sending a HEAD request to the live room page.
     *
     * Extracts ttwid, __ac_nonce, and msToken from the Set-Cookie headers.
     */
    private suspend fun getWebCookie(webRid: String): String {
        val requestHeaders = getRequestHeaders().toMutableMap()
        val baseCookie = requestHeaders["cookie"] ?: ""

        // Short-TTL cache keyed by room + base cookie, mirroring the Dart reference,
        // so repeated room-detail resolutions don't re-issue a HEAD to live.douyin.com
        // (which increases the chance of HTTP 444 throttling).
        val cacheKey = "$webRid|${baseCookie.hashCode()}"
        val cachedAt = webCookieCacheAt[cacheKey]
        val cachedValue = webCookieCache[cacheKey]
        if (cachedAt != null && cachedValue != null &&
            System.currentTimeMillis() - cachedAt < WEB_COOKIE_CACHE_TTL_MS
        ) {
            logDebug("获取直播间 Web Cookie：使用缓存 ($webRid)")
            return cachedValue
        }

        requestHeaders["Referer"] = "https://live.douyin.com/$webRid"

        val headResp = try {
            httpClient.head(
                "https://live.douyin.com/$webRid",
                header = requestHeaders
            )
        } catch (e: Exception) {
            if (baseCookie.isNotEmpty()) {
                logDebug("获取直播间 Web Cookie 的 HEAD 请求失败，使用已保存 Cookie 继续：$e")
                webCookieCache[cacheKey] = baseCookie
                webCookieCacheAt[cacheKey] = System.currentTimeMillis()
                return baseCookie
            }
            throw e
        }

        try {
            if (headResp.code == 444) {
                throw CoreError("", statusCode = 444)
            }

            val dyCookie = StringBuilder()
            if (baseCookie.isNotEmpty()) {
                dyCookie.append(ensureCookieEndsWithSemicolon(baseCookie))
            }

            headResp.headers("set-cookie").forEach { element ->
                val cookiePart = element.split(";")[0]
                if (cookiePart.contains("ttwid") ||
                    cookiePart.contains("__ac_nonce") ||
                    cookiePart.contains("msToken")
                ) {
                    dyCookie.append(cookiePart).append(";")
                }
            }
            val result = dyCookie.toString()
            webCookieCache[cacheKey] = result
            webCookieCacheAt[cacheKey] = System.currentTimeMillis()
            return result
        } finally {
            headResp.close()
        }
    }

    /**
     * Merge two cookie strings. When [preferBase] is true, base cookie values
     * override extra cookie values for the same key; otherwise extra overrides base.
     */
    private fun mergeCookieValues(
        baseCookie: String,
        extraCookie: String,
        preferBase: Boolean = false
    ): String {
        val base = parseCookiePairs(baseCookie)
        val extra = parseCookiePairs(extraCookie)
        val merged = if (preferBase) extra + base else base + extra
        return merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /**
     * Parse a cookie header string into a map of key-value pairs.
     */
    private fun parseCookiePairs(cookieValue: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (part in cookieValue.split(";")) {
            val item = part.trim()
            if (item.isEmpty()) continue
            val idx = item.indexOf("=")
            if (idx <= 0) continue
            val key = item.substring(0, idx).trim()
            val value = item.substring(idx + 1).trim()
            if (key.isNotEmpty()) map[key] = value
        }
        return map
    }

    private fun ensureCookieEndsWithSemicolon(value: String): String {
        val v = value.trim()
        return if (v.isEmpty() || v.endsWith(";")) v else "$v;"
    }

    // ── Request throttling ──────────────────────────────────────────────────

    /**
     * Throttle room detail requests to at least [ROOM_DETAIL_THROTTLE_MS] apart.
     * Douyin's servers return HTTP 444 when requests are too frequent.
     */
    private suspend fun throttleRoomDetailRequest() {
        while (true) {
            val now = System.currentTimeMillis()
            val last = lastRoomDetailRequestAt.get()
            val elapsed = now - last
            if (elapsed >= ROOM_DETAIL_THROTTLE_MS) {
                if (lastRoomDetailRequestAt.compareAndSet(last, now)) break
            } else {
                delay(ROOM_DETAIL_THROTTLE_MS - elapsed)
            }
        }
    }

    // ── Categories ──────────────────────────────────────────────────────────

    /**
     * Fetch all live streaming categories from the Douyin live page.
     *
     * Parses the HTML of `live.douyin.com` to extract the `categoryData` JSON
     * embedded in the page's render data.
     */
    override suspend fun getCategories(): List<LiveCategory> {
        val categories = mutableListOf<LiveCategory>()
        val result = httpClient.getText(
            "https://live.douyin.com/",
            queryParameters = emptyMap(),
            header = getRequestHeaders()
        )

        val renderDataJson = extractCategoryRenderData(result)
        val categoryData = renderDataJson.optJSONArray("categoryData") ?: return categories

        for (i in 0 until categoryData.length()) {
            val item = categoryData.getJSONObject(i)
            val partition = item.optJSONObject("partition") ?: continue
            val parentId = "${partition.optString("id_str", "")},${partition.optString("type", "")}"

            val subs = mutableListOf<LiveSubCategory>()
            val subPartitions = item.optJSONArray("sub_partition")
            if (subPartitions != null) {
                for (j in 0 until subPartitions.length()) {
                    val subItem = subPartitions.getJSONObject(j)
                    val subPartition = subItem.optJSONObject("partition") ?: continue
                    val subId = "${subPartition.optString("id_str", "")},${subPartition.optString("type", "")}"
                    subs.add(
                        LiveSubCategory(
                            id = subId,
                            name = subPartition.optString("title", ""),
                            parentId = parentId,
                            pic = pickPartitionImageUrl(subPartition)
                                ?: pickPartitionImageUrl(partition)
                        )
                    )
                }
            }

            // Insert the parent category as the first sub-category
            subs.add(
                0,
                LiveSubCategory(
                    id = parentId,
                    name = partition.optString("title", ""),
                    parentId = parentId,
                    pic = pickPartitionImageUrl(partition)
                )
            )

            categories.add(
                LiveCategory(
                    children = subs,
                    id = parentId,
                    name = partition.optString("title", "")
                )
            )
        }
        return categories
    }

    /**
     * Extract the `categoryData` JSON array from the Douyin live page HTML.
     *
     * The data is embedded as an escaped JSON string in the page's render data.
     * This method locates the marker, extracts the escaped JSON array, and
     * normalizes the escaping.
     */
    private fun extractCategoryRenderData(html: String): JSONObject {
        val marker = "\\\"categoryData\\\":"
        val markerIndex = html.indexOf(marker)
        if (markerIndex < 0) {
            throw CoreError("抖音分类数据解析失败")
        }
        val arrayStart = html.indexOf("[", markerIndex)
        if (arrayStart < 0) {
            throw CoreError("抖音分类数据解析失败")
        }
        val escapedArray = extractEscapedJsonArray(html, arrayStart)
        val normalizedJson = "{\"categoryData\":$escapedArray}"
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\\\\", "\\")
        return JSONObject(normalizedJson)
    }

    /**
     * Extract a balanced JSON array from a source string, respecting string
     * literals and escape sequences.
     */
    private fun extractEscapedJsonArray(source: String, startIndex: Int): String {
        val buffer = StringBuilder()
        var depth = 0
        var inString = false
        var escaped = false
        for (i in startIndex until source.length) {
            val ch = source[i]
            buffer.append(ch)
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\') {
                escaped = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (inString) continue
            when (ch) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return buffer.toString()
                }
            }
        }
        throw CoreError("抖音分类数据解析失败")
    }

    // ── Category rooms ──────────────────────────────────────────────────────

    override suspend fun getCategoryRooms(
        category: LiveSubCategory,
        page: Int
    ): LiveCategoryResult {
        val ids = category.id.split(',')
        val partitionId = ids[0]
        val partitionType = ids[1]

        val uri = URI("https://live.douyin.com/webcast/web/partition/detail/room/v2/").let { base ->
            val params = buildCommonQueryParams() + linkedMapOf(
                "count" to "15",
                "offset" to ((page - 1) * 15).toString(),
                "partition" to partitionId,
                "partition_type" to partitionType,
                "req_from" to "2"
            )
            buildUriWithParams(base, params)
        }

        val requestUrl = douyinSign.getAbogusUrl(uri.toString(), DEFAULT_USER_AGENT)
        val result = httpClient.getJson(requestUrl, header = getRequestHeaders()) as JSONObject

        val dataArray = result.optJSONObject("data")?.optJSONArray("data") ?: JSONArray()
        val hasMore = dataArray.length() >= 15
        val items = parseRoomItems(dataArray)
        return LiveCategoryResult(hasMore = hasMore, items = items)
    }

    // ── Recommend rooms ─────────────────────────────────────────────────────

    override suspend fun getRecommendRooms(page: Int): LiveCategoryResult {
        val uri = URI("https://live.douyin.com/webcast/web/partition/detail/room/v2/").let { base ->
            val params = buildCommonQueryParams() + linkedMapOf(
                "count" to "15",
                "offset" to ((page - 1) * 15).toString(),
                "partition" to "720",
                "partition_type" to "1",
                "req_from" to "2"
            )
            buildUriWithParams(base, params)
        }

        val requestUrl = douyinSign.getAbogusUrl(uri.toString(), DEFAULT_USER_AGENT)
        val result = httpClient.getJson(requestUrl, header = getRequestHeaders()) as JSONObject

        val dataArray = result.optJSONObject("data")?.optJSONArray("data") ?: JSONArray()
        val hasMore = dataArray.length() >= 15
        val items = parseRoomItems(dataArray)
        return LiveCategoryResult(hasMore = hasMore, items = items)
    }

    /**
     * Parse room items from the category/recommend API response.
     */
    private fun parseRoomItems(dataArray: JSONArray): List<LiveRoomItem> {
        val items = mutableListOf<LiveRoomItem>()
        for (i in 0 until dataArray.length()) {
            val item = dataArray.optJSONObject(i) ?: continue
            val room = item.optJSONObject("room") ?: continue
            val cover = room.optJSONObject("cover")
            val owner = room.optJSONObject("owner")
            val stats = room.optJSONObject("room_view_stats")
            items.add(
                LiveRoomItem(
                    roomId = item.optString("web_rid", ""),
                    title = room.optString("title", ""),
                    cover = cover?.optJSONArray("url_list")?.optString(0, "") ?: "",
                    userName = owner?.optString("nickname", "") ?: "",
                    faceUrl = firstImageUrl(owner?.opt("avatar_thumb")),
                    online = stats?.opt("display_value")?.toString()?.toIntOrNull() ?: 0
                )
            )
        }
        return items
    }

    // ── Room detail ─────────────────────────────────────────────────────────

    /**
     * Get room detail for a Douyin live room.
     *
     * Two types of room IDs exist:
     * - **webRid** (<= 16 chars): a stable per-user identifier like "416144012050"
     * - **roomId** (> 16 chars): a per-session identifier like "7376429659866598196"
     *
     * The method throttles requests to avoid HTTP 444 errors.
     *
     * @param roomId Either a webRid or a numeric roomId
     */
    override suspend fun getRoomDetail(roomId: String): LiveRoomDetail {
        throttleRoomDetailRequest()
        return if (roomId.length <= 16) {
            getRoomDetailByWebRid(roomId)
        } else {
            getRoomDetailByRoomId(roomId)
        }
    }

    // ── Room detail by roomId ───────────────────────────────────────────────

    /**
     * Fetch room detail using the numeric roomId via the `webcast.amemv.com` API.
     *
     * If the room status is not live (status != 2), falls back to [getRoomDetailByWebRid]
     * using the owner's web_rid.
     */
    private suspend fun getRoomDetailByRoomId(roomId: String): LiveRoomDetail {
        val roomData = getRoomDataByRoomId(roomId)
        val room = roomData.optJSONObject("data")?.optJSONObject("room")
            ?: throw CoreError("抖音直播间数据为空，可能是房间不存在、未开播或被风控限制")

        val webRid = room.optJSONObject("owner")?.optString("web_rid", "") ?: ""
        val userUniqueId = generateRandomNumberString(12)

        val status = room.optInt("status", 0)
        val categoryInfo = resolveDouyinCategoryInfo(room)

        // If the room is offline (status != 2), fall back to webRid lookup
        if (status == 4) {
            return getRoomDetailByWebRid(webRid)
        }

        val roomStatus = status == 2
        val danmakuCookie = getDanmakuCookie(webRid)
        val owner = room.optJSONObject("owner") ?: JSONObject()

        val danmakuArgs = DanmakuArgs.Douyin(
            webRid = webRid,
            roomId = roomId,
            userId = userUniqueId,
            cookie = danmakuCookie
        )
        lastDanmakuArgs = danmakuArgs
        streamDataByRoom[webRid] = (if (roomStatus) room.optJSONObject("stream_url") else null) ?: JSONObject()

        return LiveRoomDetail(
            roomId = webRid,
            title = room.optString("title", ""),
            cover = if (roomStatus) {
                room.optJSONObject("cover")?.optJSONArray("url_list")?.optString(0, "") ?: ""
            } else "",
            userName = owner.optString("nickname", ""),
            userAvatar = owner.optJSONObject("avatar_thumb")
                ?.optJSONArray("url_list")?.optString(0, "") ?: "",
            online = if (roomStatus) {
                room.optJSONObject("room_view_stats")
                    ?.opt("display_value")?.toString()?.toIntOrNull() ?: 0
            } else 0,
            status = roomStatus,
            url = "https://live.douyin.com/$webRid",
            introduction = owner.optString("signature", ""),
            notice = "",
            categoryId = categoryInfo["categoryId"],
            categoryName = categoryInfo["categoryName"],
            categoryParentId = categoryInfo["categoryParentId"],
            categoryParentName = categoryInfo["categoryParentName"],
            categoryPic = categoryInfo["categoryPic"]
        )
    }

    /**
     * Fetch raw room data by roomId from the `webcast.amemv.com` reflow API.
     */
    private suspend fun getRoomDataByRoomId(roomId: String): JSONObject {
        return httpClient.getJson(
            "https://webcast.amemv.com/webcast/room/reflow/info/",
            queryParameters = mapOf(
                "type_id" to "0",
                "live_id" to "1",
                "room_id" to roomId,
                "sec_user_id" to "",
                "version_code" to "99.99.99",
                "app_id" to "6383"
            ),
            header = getRequestHeaders()
        ) as JSONObject
    }

    // ── Room detail by webRid ───────────────────────────────────────────────

    /**
     * Fetch room detail using the webRid.
     *
     * Tries the API approach first; if it fails (except HTTP 444 which is rethrown),
     * falls back to parsing the HTML page.
     */
    private suspend fun getRoomDetailByWebRid(webRid: String): LiveRoomDetail {
        try {
            return getRoomDetailByWebRidApi(webRid)
        } catch (e: CoreError) {
            CoreLog.error(e)
            if (e.statusCode == 444) throw e
        } catch (e: Exception) {
            CoreLog.error(e)
        }
        return getRoomDetailByWebRidHtml(webRid)
    }

    /**
     * Fetch room detail via the `webcast/room/web/enter/` API.
     */
    private suspend fun getRoomDetailByWebRidApi(webRid: String): LiveRoomDetail {
        val data = getRoomDataByApi(webRid)

        val roomList = data.optJSONArray("data") ?: JSONArray()
        if (roomList.length() == 0) {
            throw CoreError("抖音直播间数据为空，可能是房间不存在、未开播或被风控限制")
        }
        val roomData = roomList.getJSONObject(0)
        val userData = data.optJSONObject("user") ?: JSONObject()
        val roomId = roomData.optString("id_str", "")
        val categoryInfo = resolveDouyinCategoryInfo(roomData)
        val userUniqueId = generateRandomNumberString(12)

        val owner = roomData.optJSONObject("owner") ?: JSONObject()
        val roomStatus = roomData.optInt("status", 0) == 2

        val danmakuCookie = getDanmakuCookie(webRid)
        val danmakuArgs = DanmakuArgs.Douyin(
            webRid = webRid,
            roomId = roomId,
            userId = userUniqueId,
            cookie = danmakuCookie
        )
        lastDanmakuArgs = danmakuArgs
        streamDataByRoom[webRid] = (if (roomStatus) roomData.optJSONObject("stream_url") else null) ?: JSONObject()

        return LiveRoomDetail(
            roomId = webRid,
            title = roomData.optString("title", ""),
            cover = if (roomStatus) {
                roomData.optJSONObject("cover")?.optJSONArray("url_list")?.optString(0, "") ?: ""
            } else "",
            userName = if (roomStatus) {
                owner.optString("nickname", "")
            } else {
                userData.optString("nickname", "")
            },
            userAvatar = if (roomStatus) {
                owner.optJSONObject("avatar_thumb")
                    ?.optJSONArray("url_list")?.optString(0, "") ?: ""
            } else {
                userData.optJSONObject("avatar_thumb")
                    ?.optJSONArray("url_list")?.optString(0, "") ?: ""
            },
            online = if (roomStatus) {
                roomData.optJSONObject("room_view_stats")
                    ?.opt("display_value")?.toString()?.toIntOrNull() ?: 0
            } else 0,
            status = roomStatus,
            url = "https://live.douyin.com/$webRid",
            introduction = owner.optString("signature", ""),
            notice = "",
            categoryId = categoryInfo["categoryId"],
            categoryName = categoryInfo["categoryName"],
            categoryParentId = categoryInfo["categoryParentId"],
            categoryParentName = categoryInfo["categoryParentName"],
            categoryPic = categoryInfo["categoryPic"]
        )
    }

    /**
     * Fetch room data from the `webcast/room/web/enter/` API with A-Bogus signing.
     */
    private suspend fun getRoomDataByApi(webRid: String): JSONObject {
        val requestHeaders = getRequestHeaders()
        requestHeaders["Referer"] = "https://live.douyin.com/$webRid"

        val uri = URI("https://live.douyin.com/webcast/room/web/enter/").let { base ->
            val params = linkedMapOf(
                "aid" to "6383",
                "app_name" to "douyin_web",
                "live_id" to "1",
                "device_platform" to "web",
                "language" to "zh-CN",
                "browser_language" to "zh-CN",
                "browser_platform" to "Win32",
                "browser_name" to "Chrome",
                "browser_version" to "125.0.0.0",
                "web_rid" to webRid,
                "msToken" to ""
            )
            buildUriWithParams(base, params)
        }

        val requestUrl = douyinSign.getAbogusUrl(uri.toString(), DEFAULT_USER_AGENT)
        val result = httpClient.getJson(requestUrl, header = requestHeaders)

        if (result !is JSONObject) {
            throw Exception("抖音接口返回格式异常")
        }

        val data = result.optJSONObject("data")
            ?: throw CoreError("抖音直播间数据为空，请稍后再试")
        val rooms = data.optJSONArray("data")
        if (rooms == null || rooms.length() == 0) {
            throw CoreError("抖音直播间数据为空，可能是房间不存在、未开播或被风控限制")
        }

        return data
    }

    /**
     * Fetch room detail by parsing the Douyin live HTML page.
     *
     * This is the fallback method when the API approach fails. It extracts
     * the render data JSON from the HTML using regex.
     */
    private suspend fun getRoomDetailByWebRidHtml(webRid: String): LiveRoomDetail {
        val roomData = getRoomDataByHtml(webRid)
        val roomId = roomData.optJSONObject("roomStore")
            ?.optJSONObject("roomInfo")?.optJSONObject("room")
            ?.optString("id_str", "") ?: ""
        val userUniqueId = roomData.optJSONObject("userStore")
            ?.optJSONObject("odin")?.optString("user_unique_id", "") ?: ""

        val room = roomData.optJSONObject("roomStore")
            ?.optJSONObject("roomInfo")?.optJSONObject("room") ?: JSONObject()
        val owner = room.optJSONObject("owner") ?: JSONObject()
        val anchor = roomData.optJSONObject("roomStore")
            ?.optJSONObject("roomInfo")?.optJSONObject("anchor") ?: JSONObject()
        val categoryInfo = resolveDouyinCategoryInfo(room)
        val roomStatus = room.optInt("status", 0) == 2

        val danmakuCookie = getDanmakuCookie(webRid)
        val danmakuArgs = DanmakuArgs.Douyin(
            webRid = webRid,
            roomId = roomId,
            userId = userUniqueId,
            cookie = danmakuCookie
        )
        lastDanmakuArgs = danmakuArgs
        streamDataByRoom[webRid] = (if (roomStatus) room.optJSONObject("stream_url") else null) ?: JSONObject()

        return LiveRoomDetail(
            roomId = webRid,
            title = room.optString("title", ""),
            cover = if (roomStatus) {
                room.optJSONObject("cover")?.optJSONArray("url_list")?.optString(0, "") ?: ""
            } else "",
            userName = if (roomStatus) {
                owner.optString("nickname", "")
            } else {
                anchor.optString("nickname", "")
            },
            userAvatar = if (roomStatus) {
                owner.optJSONObject("avatar_thumb")
                    ?.optJSONArray("url_list")?.optString(0, "") ?: ""
            } else {
                anchor.optJSONObject("avatar_thumb")
                    ?.optJSONArray("url_list")?.optString(0, "") ?: ""
            },
            online = if (roomStatus) {
                room.optJSONObject("room_view_stats")
                    ?.opt("display_value")?.toString()?.toIntOrNull() ?: 0
            } else 0,
            status = roomStatus,
            url = "https://live.douyin.com/$webRid",
            introduction = owner.optString("signature", ""),
            notice = "",
            categoryId = categoryInfo["categoryId"],
            categoryName = categoryInfo["categoryName"],
            categoryParentId = categoryInfo["categoryParentId"],
            categoryParentName = categoryInfo["categoryParentName"],
            categoryPic = categoryInfo["categoryPic"]
        )
    }

    /**
     * Fetch the raw HTML page for a Douyin live room and extract the embedded
     * render data JSON.
     */
    private suspend fun getRoomDataByHtml(webRid: String): JSONObject {
        val dyCookie = getWebCookie(webRid)
        val result = httpClient.getText(
            "https://live.douyin.com/$webRid",
            queryParameters = emptyMap(),
            header = mapOf(
                "Authority" to DEFAULT_AUTHORITY,
                "Referer" to DEFAULT_REFERER,
                "Cookie" to dyCookie,
                "User-Agent" to DEFAULT_USER_AGENT
            )
        )

        if (result.isBlank()) {
            throw CoreError("抖音直播间页面返回为空，请稍后再试")
        }
        if (!result.contains("\\\"state\\\"")) {
            throw CoreError("抖音直播间页面数据不可用，可能是访问受限或页面结构已变化")
        }

        // Extract the render data JSON using regex
        val renderDataMatch = Regex("""\{\\"state\\":\{\\"appStore.*?\]\\n""").find(result)
        val renderData = renderDataMatch?.value ?: ""
        if (renderData.isEmpty()) {
            throw CoreError("抖音直播间页面数据解析失败，请稍后再试")
        }

        val str = renderData.trim()
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("]\\n", "")

        val renderDataJson = JSONObject(str)
        val state = renderDataJson.optJSONObject("state")
            ?: throw CoreError("抖音直播间页面状态数据异常")
        return state
    }

    // ── Play qualities ──────────────────────────────────────────────────────

    /**
     * Extract available play qualities from the stored stream data.
     *
     * The stream data comes from `live_core_sdk_data.pull_data` in the room detail.
     * Two formats are supported:
     * 1. JSON `stream_data`: maps quality sdk_key to main.flv / main.hls URLs
     * 2. Non-JSON `stream_data`: uses `flv_pull_url` and `hls_pull_url_map` maps
     *
     * Qualities are sorted by level descending (highest quality first).
     */
    override suspend fun getPlayQualites(detail: LiveRoomDetail): List<LivePlayQuality> {
        val qualities = mutableListOf<LivePlayQuality>()
        try {
            val streamUrl = streamDataByRoom[detail.roomId] ?: return qualities
            val liveCoreData = streamUrl.optJSONObject("live_core_sdk_data") ?: return qualities
            val pullData = liveCoreData.optJSONObject("pull_data") ?: return qualities
            val options = pullData.optJSONObject("options")
            val qualityList = options?.optJSONArray("qualities")
            val streamData = pullData.optString("stream_data", "")

            if (qualityList == null) return qualities

            if (!streamData.startsWith("{")) {
                // Format 2: use flv_pull_url and hls_pull_url_map
                val flvMap = streamUrl.optJSONObject("flv_pull_url")
                val hlsMap = streamUrl.optJSONObject("hls_pull_url_map")
                val flvList = jsonObjectValues(flvMap)
                val hlsList = jsonObjectValues(hlsMap)

                for (i in 0 until qualityList.length()) {
                    val quality = qualityList.getJSONObject(i)
                    val level = quality.optInt("level", 0)
                    val urls = mutableListOf<String>()
                    val flvIndex = flvList.size - level
                    if (flvIndex in flvList.indices) urls.add(flvList[flvIndex])
                    val hlsIndex = hlsList.size - level
                    if (hlsIndex in hlsList.indices) urls.add(hlsList[hlsIndex])
                    if (urls.isNotEmpty()) {
                        qualities.add(
                            LivePlayQuality(
                                quality = quality.optString("name", ""),
                                sort = level,
                                data = PlayQualityData.Douyin(urls = urls)
                            )
                        )
                    }
                }
            } else {
                // Format 1: parse stream_data as JSON
                val qualityData = JSONObject(streamData).optJSONObject("data") ?: JSONObject()

                for (i in 0 until qualityList.length()) {
                    val quality = qualityList.getJSONObject(i)
                    val urls = mutableListOf<String>()
                    val sdkKey = quality.optString("sdk_key", "")

                    val sdkEntry = qualityData.optJSONObject(sdkKey)
                    val mainEntry = sdkEntry?.optJSONObject("main")
                    val flvUrl = mainEntry?.optString("flv", "")
                    if (!flvUrl.isNullOrEmpty()) urls.add(flvUrl)
                    val hlsUrl = mainEntry?.optString("hls", "")
                    if (!hlsUrl.isNullOrEmpty()) urls.add(hlsUrl)

                    if (urls.isNotEmpty()) {
                        qualities.add(
                            LivePlayQuality(
                                quality = quality.optString("name", ""),
                                sort = quality.optInt("level", 0),
                                data = PlayQualityData.Douyin(urls = urls)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            CoreLog.error(e)
        }

        qualities.sortByDescending { it.sort }
        logDebug("获取到的画质列表: ${qualities.joinToString { it.quality }}")
        return qualities
    }

    /**
     * Extract all values from a JSONObject as a list of strings.
     * Used for `flv_pull_url` and `hls_pull_url_map` maps.
     */
    private fun jsonObjectValues(obj: JSONObject?): List<String> {
        if (obj == null) return emptyList()
        return obj.keys().asSequence().map { obj.optString(it, "") }.toList()
    }

    // ── Play URLs ───────────────────────────────────────────────────────────

    override suspend fun getPlayUrls(
        detail: LiveRoomDetail,
        quality: LivePlayQuality
    ): LivePlayUrl {
        val urls = (quality.data as PlayQualityData.Douyin).urls
        return LivePlayUrl(urls = urls.toList())
    }

    // ── Search ──────────────────────────────────────────────────────────────

    /**
     * Search for live rooms by keyword.
     *
     * Performs a HEAD request to `live.douyin.com` first to collect cookies,
     * then queries the search API.
     */
    override suspend fun searchRooms(keyword: String, page: Int): LiveSearchRoomResult {
        val uri = URI("https://www.douyin.com/aweme/v1/web/live/search/").let { base ->
            val params = linkedMapOf(
                "device_platform" to "webapp",
                "aid" to "6383",
                "channel" to "channel_pc_web",
                "search_channel" to "aweme_live",
                "keyword" to keyword,
                "search_source" to "switch_tab",
                "query_correct_type" to "1",
                "is_filter_search" to "0",
                "from_group_id" to "",
                "offset" to ((page - 1) * 10).toString(),
                "count" to "10",
                "pc_client_type" to "1",
                "version_code" to "170400",
                "version_name" to "17.4.0",
                "cookie_enabled" to "true",
                "screen_width" to "1980",
                "screen_height" to "1080",
                "browser_language" to "zh-CN",
                "browser_platform" to "Win32",
                "browser_name" to "Edge",
                "browser_version" to "125.0.0.0",
                "browser_online" to "true",
                "engine_name" to "Blink",
                "engine_version" to "125.0.0.0",
                "os_name" to "Windows",
                "os_version" to "10",
                "cpu_core_num" to "12",
                "device_memory" to "8",
                "platform" to "PC",
                "downlink" to "10",
                "effective_type" to "4g",
                "round_trip_time" to "100",
                "webid" to "7382872326016435738"
            )
            buildUriWithParams(base, params)
        }

        val requestUrl = uri.toString()
        val requestHeaders = getRequestHeaders()

        // Collect cookies via HEAD request
        val dyCookie = StringBuilder()
        val savedCookie = requestHeaders["cookie"] ?: ""
        if (savedCookie.isNotEmpty()) {
            dyCookie.append(ensureCookieEndsWithSemicolon(savedCookie))
        }

        try {
            val headResp = httpClient.head("https://live.douyin.com", header = requestHeaders)
            try {
                headResp.headers("set-cookie").forEach { element ->
                    val cookiePart = element.split(";")[0]
                    if (cookiePart.contains("ttwid") || cookiePart.contains("__ac_nonce")) {
                        dyCookie.append(cookiePart).append(";")
                    }
                }
            } finally {
                headResp.close()
            }
        } catch (e: Exception) {
            if (dyCookie.isEmpty()) throw e
            logDebug("抖音搜索预取 Cookie 的 HEAD 请求失败，使用已保存 Cookie 继续：$e")
        }

        val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
        val result = httpClient.getJson(
            requestUrl,
            queryParameters = emptyMap(),
            header = mapOf(
                "Authority" to "www.douyin.com",
                "accept" to "application/json, text/plain, */*",
                "accept-language" to "zh-CN,zh;q=0.9,en;q=0.8",
                "cookie" to dyCookie.toString(),
                "priority" to "u=1, i",
                "referer" to "https://www.douyin.com/search/$encodedKeyword?type=live",
                "sec-ch-ua" to "\"Microsoft Edge\";v=\"125\", \"Chromium\";v=\"125\", \"Not.A/Brand\";v=\"24\"",
                "sec-ch-ua-mobile" to "?0",
                "sec-ch-ua-platform" to "\"Windows\"",
                "sec-fetch-dest" to "empty",
                "sec-fetch-mode" to "cors",
                "sec-fetch-site" to "same-origin",
                "user-agent" to DEFAULT_USER_AGENT
            )
        )

        if (result is String && (result.isEmpty() || result == "blocked")) {
            throw Exception("抖音直播搜索被限制，请稍后再试")
        }
        if (result is JSONObject && result.optInt("status_code") == 2483) {
            throw Exception("抖音搜索需要登录，请在账号管理中通过网页登录或手动配置完整抖音 Cookie")
        }

        val items = mutableListOf<LiveRoomItem>()
        val dataArray = when (result) {
            is JSONObject -> result.optJSONArray("data")
            else -> null
        }
        if (dataArray != null) {
            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i) ?: continue
                val lives = item.optJSONObject("lives") ?: continue
                val rawdata = lives.opt("rawdata") ?: continue
                val itemData = try {
                    JSONObject(rawdata.toString())
                } catch (_: Exception) {
                    continue
                }
                val owner = itemData.optJSONObject("owner") ?: JSONObject()
                val stats = itemData.optJSONObject("stats") ?: JSONObject()
                items.add(
                    LiveRoomItem(
                        roomId = owner.optString("web_rid", ""),
                        title = itemData.optString("title", ""),
                        cover = itemData.optJSONObject("cover")
                            ?.optJSONArray("url_list")?.optString(0, "") ?: "",
                        userName = owner.optString("nickname", ""),
                        faceUrl = firstImageUrl(owner.opt("avatar_thumb")),
                        online = stats.opt("total_user")?.toString()?.toIntOrNull() ?: 0
                    )
                )
            }
        }

        return LiveSearchRoomResult(hasMore = items.size >= 10, items = items)
    }

    /**
     * Search for anchors (streamers) by keyword.
     *
     * Reuses [searchRooms] and re-sorts results to prioritize exact username matches.
     */
    override suspend fun searchAnchors(keyword: String, page: Int): LiveSearchAnchorResult {
        val result = searchRooms(keyword, page)
        val lowerKeyword = keyword.trim().lowercase()
        val rooms = result.items.sortedWith(Comparator { a, b ->
            val aMatched = a.userName.lowercase().contains(lowerKeyword)
            val bMatched = b.userName.lowercase().contains(lowerKeyword)
            if (aMatched != bMatched) {
                return@Comparator if (aMatched) -1 else 1
            }
            b.online.compareTo(a.online)
        })
        return LiveSearchAnchorResult(
            hasMore = result.hasMore,
            items = rooms.map { room ->
                LiveAnchorItem(
                    roomId = room.roomId,
                    userName = room.userName,
                    avatar = room.cover,
                    liveStatus = true
                )
            }
        )
    }

    // ── Live status ─────────────────────────────────────────────────────────

    override suspend fun getLiveStatus(roomId: String): Boolean {
        return getRoomDetail(roomId).status
    }

    // ── Super chat (not supported on Douyin) ────────────────────────────────

    override suspend fun getSuperChatMessage(
        roomId: String,
        detail: LiveRoomDetail?
    ): List<LiveSuperChatMessage> = emptyList()

    // ── Contribution rank ───────────────────────────────────────────────────

    /**
     * Fetch the contribution (gift) rank for a live room.
     *
     * Requires the real roomId and anchor IDs which are obtained from the
     * room detail API. Uses the `webcast/ranklist/audience/` endpoint with
     * A-Bogus signing.
     */
    override suspend fun getContributionRank(
        roomId: String,
        detail: LiveRoomDetail?
    ): List<LiveContributionRankItem> {
        val roomDetail = detail ?: getRoomDetail(roomId)
        val webRid = if (roomDetail.roomId.isNotEmpty()) roomDetail.roomId else roomId
        val danmakuArgs = lastDanmakuArgs

        val roomInfo = getRoomDataByApi(webRid)
        val roomList = roomInfo.optJSONArray("data") ?: return emptyList()
        if (roomList.length() == 0) return emptyList()

        val roomData = roomList.getJSONObject(0)
        val owner = roomData.optJSONObject("owner")
            ?: roomInfo.optJSONObject("user") ?: JSONObject()
        val anchorId = owner.optString("id_str", "").ifEmpty {
            owner.opt("id")?.toString() ?: ""
        }
        val secAnchorId = owner.optString("sec_uid", "")
        val realRoomId = danmakuArgs?.roomId
            ?: roomData.optString("id_str", "")
        if (anchorId.isEmpty() || secAnchorId.isEmpty() || realRoomId.isEmpty()) {
            return emptyList()
        }

        val requestHeaders = getRequestHeaders()
        requestHeaders["Referer"] = "https://live.douyin.com/$webRid"

        val uri = URI("https://live.douyin.com/webcast/ranklist/audience/").let { base ->
            val params = linkedMapOf(
                "aid" to "6383",
                "app_name" to "douyin_web",
                "live_id" to "1",
                "device_platform" to "web",
                "language" to "zh-CN",
                "enter_from" to "link_share",
                "cookie_enabled" to "true",
                "screen_width" to "1920",
                "screen_height" to "1080",
                "browser_language" to "zh-CN",
                "browser_platform" to "Win32",
                "browser_name" to "Chrome",
                "browser_version" to "125.0.0.0",
                "os_name" to "Windows",
                "os_version" to "10",
                "webcast_sdk_version" to "2450",
                "room_id" to realRoomId,
                "anchor_id" to anchorId,
                "sec_anchor_id" to secAnchorId,
                "ignoreToast" to "true",
                "rank_type" to "30",
                "msToken" to ""
            )
            buildUriWithParams(base, params)
        }

        val requestUrl = douyinSign.getAbogusUrl(uri.toString(), DEFAULT_USER_AGENT)
        val result = httpClient.getJson(requestUrl, header = requestHeaders) as JSONObject

        val ranksArray = result.optJSONObject("data")?.optJSONArray("ranks") ?: return emptyList()
        return (0 until ranksArray.length()).mapNotNull { index ->
            try {
                val item = ranksArray.getJSONObject(index)
                val user = item.optJSONObject("user") ?: JSONObject()
                val payGrade = user.optJSONObject("pay_grade") ?: JSONObject()
                val fansData = user.optJSONObject("fans_club")
                    ?.optJSONObject("data") ?: JSONObject()
                val userLevel = payGrade.opt("level")?.toString()?.toIntOrNull()
                val fansLevel = fansData.opt("level")?.toString()?.toIntOrNull()
                val scoreText = resolveDouyinRankScore(item)
                val scoreDescription = item.optString("score_description", "").trim()
                val exactlyScore = item.optString("exactly_score", "").trim()

                val scoreDetail: String? = when {
                    scoreDescription.isNotEmpty() && scoreDescription != scoreText -> scoreDescription
                    exactlyScore.isNotEmpty() && exactlyScore != scoreText -> exactlyScore
                    else -> {
                        val gapDesc = item.optString("gap_description", "").trim()
                        gapDesc.ifEmpty { null }
                    }
                }

                val rankItem = LiveContributionRankItem(
                    rank = resolveDouyinRank(item, index),
                    userName = user.optString("nickname", ""),
                    avatar = firstImageUrl(user.opt("avatar_thumb")),
                    scoreText = scoreText,
                    scoreDetail = scoreDetail,
                    userLevel = userLevel,
                    userLevelText = if (userLevel == null || userLevel <= 0) null else "财富 $userLevel",
                    userLevelIcon = firstImageUrl(payGrade.opt("new_im_icon_with_level")),
                    fansLevel = fansLevel,
                    fansName = fansData.optString("club_name", "").ifEmpty { null },
                    fansIcon = pickDouyinBadgeIcon(fansData.optJSONObject("badge")?.opt("icons"))
                )
                if (rankItem.userName.trim().isNotEmpty()) rankItem else null
            } catch (e: Exception) {
                CoreLog.error(e)
                null
            }
        }
    }

    // ── Category / rank helper methods ──────────────────────────────────────

    /**
     * Resolve category information from room data.
     *
     * Tries multiple key paths to find the partition, parent partition,
     * and associated image URLs. Handles various API response structures.
     */
    private fun resolveDouyinCategoryInfo(roomData: JSONObject?): Map<String, String?> {
        val room = roomData ?: JSONObject()
        val partitionRoadMap = room.optJSONArray("partition_road_map")
            ?: room.optJSONArray("partitionRoadMap")

        val partition: JSONObject? = room.optJSONObject("partition")
            ?: room.optJSONObject("room_partition")
            ?: room.optJSONObject("partitionInfo")
            ?: (if (partitionRoadMap != null && partitionRoadMap.length() > 0)
                partitionRoadMap.optJSONObject(partitionRoadMap.length() - 1) else null)

        val parentPartition: JSONObject? = room.optJSONObject("parent_partition")
            ?: room.optJSONObject("partition_parent")
            ?: (partition?.let {
                it.optJSONObject("parent_partition")
                    ?: it.optJSONObject("partition_parent")
                    ?: it.optJSONObject("parent")
            })
            ?: (if (partitionRoadMap != null && partitionRoadMap.length() > 1)
                partitionRoadMap.optJSONObject(0) else null)
            ?: room.optJSONObject("partitionInfo")

        return mapOf(
            "categoryId" to resolveCategoryValue(
                partition, listOf("id_str", "id", "partition_id", "partition")
            ),
            "categoryName" to resolveCategoryValue(
                partition, listOf("title", "name", "partition_title")
            ),
            "categoryParentId" to resolveCategoryValue(
                parentPartition, listOf("id_str", "id", "partition_id", "partition")
            ),
            "categoryParentName" to resolveCategoryValue(
                parentPartition, listOf("title", "name", "partition_title")
            ),
            "categoryPic" to (pickPartitionImageUrl(partition)
                ?: pickPartitionImageUrl(parentPartition))
        )
    }

    /**
     * Recursively search a JSON value for a key from [keys].
     * Handles nested objects and arrays up to [maxDepth].
     */
    private fun resolveCategoryValue(
        source: Any?,
        keys: List<String>,
        depth: Int = 0
    ): String? {
        if (depth > 6) return null
        if (source is JSONArray) {
            for (i in 0 until source.length()) {
                val value = resolveCategoryValue(source.opt(i), keys, depth + 1)
                if (!value.isNullOrEmpty()) return value
            }
            return null
        }
        if (source !is JSONObject) return null
        for (key in keys) {
            val value = source.opt(key)?.toString()?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        for (key in source.keys()) {
            val resolved = resolveCategoryValue(source.opt(key), keys, depth + 1)
            if (!resolved.isNullOrEmpty()) return resolved
        }
        return null
    }

    /**
     * Recursively search a JSON value for an image URL.
     * Checks common image-related keys and nested structures.
     */
    private fun pickPartitionImageUrl(data: Any?): String? {
        return resolveDouyinPartitionImageUrl(data)
    }

    /**
     * Resolve the rank number from a rank item, falling back to index + 1.
     */
    private fun resolveDouyinRank(item: JSONObject, index: Int): Int {
        val parsed = item.opt("rank")?.toString()?.toIntOrNull()
        if (parsed == null || parsed <= 0) return index + 1
        if (parsed == 1 && index > 0) return index + 1
        return parsed
    }

    /**
     * Get the first URL from an image object's `url_list` array.
     */
    private fun firstImageUrl(data: Any?): String {
        return firstSiteImageUrl(data, listOf("url_list", "url")).orEmpty()
    }

    /**
     * Pick the best badge icon URL from a Douyin fans club badge icons map.
     * Tries keys "4" through "0" first (highest resolution), then any key.
     */
    private fun pickDouyinBadgeIcon(icons: Any?): String? {
        if (icons !is JSONObject) return null
        for (key in listOf("4", "3", "2", "1", "0")) {
            val url = firstImageUrl(icons.opt(key))
            if (url.isNotEmpty()) return url
        }
        for (key in icons.keys()) {
            val url = firstImageUrl(icons.opt(key))
            if (url.isNotEmpty()) return url
        }
        return null
    }

    /**
     * Resolve the score text for a contribution rank item.
     * Tries exactly_score, score_description, score, and delta in order.
     */
    private fun resolveDouyinRankScore(item: JSONObject): String {
        val exactlyScore = item.optString("exactly_score", "").trim()
        if (exactlyScore.isNotEmpty()) return exactlyScore
        val scoreDescription = item.optString("score_description", "").trim()
        if (scoreDescription.isNotEmpty()) return scoreDescription
        val score = item.optString("score", "").trim()
        if (score.isNotEmpty()) return score
        val delta = item.optString("delta", "").trim()
        if (delta.isNotEmpty()) return delta
        return "0"
    }

    // ── URI / query helpers ─────────────────────────────────────────────────

    /**
     * Build the common query parameters shared across Douyin API requests.
     */
    private fun buildCommonQueryParams(): LinkedHashMap<String, String> {
        return linkedMapOf(
            "aid" to "6383",
            "app_name" to "douyin_web",
            "live_id" to "1",
            "device_platform" to "web",
            "language" to "zh-CN",
            "enter_from" to "link_share",
            "cookie_enabled" to "true",
            "screen_width" to "1980",
            "screen_height" to "1080",
            "browser_language" to "zh-CN",
            "browser_platform" to "Win32",
            "browser_name" to "Edge",
            "browser_version" to "125.0.0.0",
            "browser_online" to "true"
        )
    }

    /**
     * Build a URI with the given query parameters.
     *
     * Each value is percent-encoded exactly once. We deliberately build the full URL
     * string and parse it with the single-arg [URI] constructor instead of the multi-arg
     * `URI(scheme, ..., query, ...)` constructor: the multi-arg constructor treats `query`
     * as a decoded string and re-encodes '%', which double-encodes already-encoded values
     * (e.g. a Chinese search keyword `王者` would become `%25E7%258E...`, breaking search).
     * The ":443" authority is preserved so a_bogus-signed callers produce an identical URL
     * for their (ASCII-only) parameters.
     */
    private fun buildUriWithParams(
        base: URI,
        params: Map<String, String>
    ): URI {
        val query = params.entries.joinToString("&") { (k, v) ->
            "$k=${encodeQueryComponent(v)}"
        }
        return URI("${base.scheme}://${base.host}:443${base.path}?$query")
    }

    /**
     * Percent-encode a query component once (UTF-8), encoding space as %20 to match the
     * browser's encodeURIComponent (Douyin's web client) rather than form-encoding (+).
     */
    private fun encodeQueryComponent(value: String): String {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    // ── Random generators ───────────────────────────────────────────────────

    /**
     * Generate a random numeric string of the given length.
     */
    private fun generateRandomNumberString(length: Int): String {
        return buildString(length) {
            repeat(length) {
                append(secureRandom.nextInt(10))
            }
        }
    }

    /**
     * Generate a random hexadecimal string of the given length.
     */
    @Suppress("unused")
    private fun generateRandomHexString(length: Int): String {
        return buildString(length) {
            repeat(length) {
                append(secureRandom.nextInt(16).toString(16))
            }
        }
    }

    // ── Logging ─────────────────────────────────────────────────────────────

    private fun logDebug(msg: String) {
        CoreLog.d("[Douyin] $msg")
    }
}
