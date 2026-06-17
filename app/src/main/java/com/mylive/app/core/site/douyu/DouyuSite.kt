package com.mylive.app.core.site.douyu

import com.mylive.app.core.common.CoreLog
import com.mylive.app.core.common.HttpClient
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
import com.mylive.app.core.common.WebSocketUtils
import com.mylive.app.core.script.JsEngine
import com.mylive.app.core.site.firstSiteImageUrl
import com.mylive.app.core.site.normalizeSiteImageUrl
import com.mylive.app.core.site.LiveDanmaku
import com.mylive.app.core.site.LiveSite
import com.mylive.app.core.site.optNullableStringValue
import com.mylive.app.core.site.optStringValue
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private val DOUYU_AVATAR_KEYS = listOf(
    "av",
    "avatar",
    "avatar_mid",
    "owner_avatar",
    "avatarSmall",
    "avatar_small",
    "avator",
    "face",
    "faceUrl"
)

private val DOUYU_COVER_KEYS = listOf(
    "rs16",
    "roomSrc",
    "room_src",
    "room_pic",
    "verticalSrc",
    "cover"
)

internal const val DOUYU_SIGN_ARGS_TTL_MS = 60_000L
private const val DOUYU_SEARCH_PAGE_SIZE = 20

internal data class DouyuSignArgsCacheEntry(
    val value: String,
    val createdAtMillis: Long
)

internal fun shouldRefreshDouyuSignArgs(
    cached: DouyuSignArgsCacheEntry?,
    nowMillis: Long
): Boolean {
    if (cached == null) return true
    if (nowMillis < cached.createdAtMillis) return true
    return nowMillis - cached.createdAtMillis >= DOUYU_SIGN_ARGS_TTL_MS
}

internal fun resolveDouyuRoomFaceUrl(item: JSONObject): String {
    return firstJsonImageUrlByKeys(item, DOUYU_AVATAR_KEYS).orEmpty()
}

internal fun resolveDouyuRoomCoverUrl(item: JSONObject): String {
    return firstJsonImageUrlByKeys(item, DOUYU_COVER_KEYS).orEmpty()
}

private fun douyuSearchHasMore(itemCount: Int): Boolean {
    return itemCount >= DOUYU_SEARCH_PAGE_SIZE
}

private fun firstJsonImageUrlByKeys(item: JSONObject, keys: List<String>): String? {
    for (key in keys) {
        val value = firstSiteImageUrl(item.opt(key))
            ?: normalizeSiteImageUrl(item.opt(key))
        if (!value.isNullOrEmpty()) return value
    }
    return null
}

@Singleton
class DouyuSite @Inject constructor(
    private val httpClient: HttpClient,
    private val jsEngineProvider: Provider<JsEngine>,
    private val okHttpClient: OkHttpClient
) : LiveSite {

    override val id: String = "douyu"
    override val name: String = "斗鱼直播"

    /**
     * Cache of signing parameters keyed by roomId.
     * Populated by [getRoomDetail], consumed by [getPlayQualites] and [getPlayUrls].
     * The value is a query-string-style result from DouyuSign, e.g.
     * "ver=23061205&rid=1234&did=xxx&time=xxx&sign=xxx"
     */
    private val signArgsCache = mutableMapOf<String, DouyuSignArgsCacheEntry>()

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36 Edg/114.0.1823.43"
        private const val SEARCH_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36 Edg/114.0.1823.51"
    }

    override fun getDanmaku(): LiveDanmaku = DouyuDanmaku(WebSocketUtils(okHttpClient))

    // ── Categories ──────────────────────────────────────────────────────

    override suspend fun getCategories(): List<LiveCategory> {
        val result = httpClient.getJson(
            "https://m.douyu.com/api/cate/list"
        ) as JSONObject

        val data = result.getJSONObject("data")
        val subCateList = data.getJSONArray("cate2Info")
        val cate1Array = data.getJSONArray("cate1Info")

        val categories = mutableListOf<LiveCategory>()
        for (i in 0 until cate1Array.length()) {
            val item = cate1Array.getJSONObject(i)
            val cate1Id = item.optStringValue("cate1Id")
            val cate1Name = item.optStringValue("cate1Name")

            val subCategories = mutableListOf<LiveSubCategory>()
            for (j in 0 until subCateList.length()) {
                val subItem = subCateList.getJSONObject(j)
                if (subItem.optStringValue("cate1Id") == cate1Id) {
                    subCategories.add(
                        LiveSubCategory(
                            pic = subItem.optString("icon", ""),
                            id = subItem.optStringValue("cate2Id"),
                            parentId = cate1Id,
                            name = subItem.optStringValue("cate2Name")
                        )
                    )
                }
            }

            categories.add(
                LiveCategory(
                    id = cate1Id,
                    name = cate1Name,
                    children = subCategories
                )
            )
        }

        // Sort by ID
        categories.sortBy { it.id.toIntOrNull() ?: 0 }
        return categories
    }

    // ── Category rooms ──────────────────────────────────────────────────

    override suspend fun getCategoryRooms(
        category: LiveSubCategory,
        page: Int
    ): LiveCategoryResult {
        val result = httpClient.getJson(
            "https://www.douyu.com/gapi/rkc/directory/mixList/2_${category.id}/$page"
        ) as JSONObject

        val data = result.getJSONObject("data")
        val rl = data.getJSONArray("rl")
        val items = mutableListOf<LiveRoomItem>()

        for (i in 0 until rl.length()) {
            val item = rl.getJSONObject(i)
            if (item.optInt("type") != 1) continue
            items.add(
                LiveRoomItem(
                    cover = resolveDouyuRoomCoverUrl(item),
                    online = item.optInt("ol"),
                    roomId = item.optStringValue("rid"),
                    title = item.optStringValue("rn"),
                    userName = item.optStringValue("nn"),
                    faceUrl = resolveDouyuRoomFaceUrl(item)
                )
            )
        }

        val hasMore = page < data.optInt("pgcnt")
        return LiveCategoryResult(hasMore = hasMore, items = items)
    }

    // ── Recommended rooms ───────────────────────────────────────────────

    override suspend fun getRecommendRooms(page: Int): LiveCategoryResult {
        val result = httpClient.getJson(
            "https://www.douyu.com/japi/weblist/apinc/allpage/6/$page"
        ) as JSONObject

        val data = result.getJSONObject("data")
        val rl = data.getJSONArray("rl")
        val items = mutableListOf<LiveRoomItem>()

        for (i in 0 until rl.length()) {
            val item = rl.getJSONObject(i)
            if (item.optInt("type") != 1) continue
            items.add(
                LiveRoomItem(
                    cover = resolveDouyuRoomCoverUrl(item),
                    online = item.optInt("ol"),
                    roomId = item.optStringValue("rid"),
                    title = item.optStringValue("rn"),
                    userName = item.optStringValue("nn"),
                    faceUrl = resolveDouyuRoomFaceUrl(item)
                )
            )
        }

        val pageCount = data.optInt("pgcnt")
        val hasMore = if (pageCount > 0) {
            page < pageCount
        } else {
            items.isNotEmpty()
        }
        return LiveCategoryResult(hasMore = hasMore, items = items)
    }

    // ── Room detail ─────────────────────────────────────────────────────

    override suspend fun getRoomDetail(roomId: String): LiveRoomDetail {
        val roomInfo = getRoomInfo(roomId)

        val h5RoomInfo = httpClient.getJson(
            "https://www.douyu.com/swf_api/h5room/$roomId",
            queryParameters = emptyMap(),
            header = mapOf(
                "referer" to "https://www.douyu.com/$roomId",
                "user-agent" to DEFAULT_USER_AGENT
            )
        ) as JSONObject
        val showTime = h5RoomInfo.optJSONObject("data")?.optNullableStringValue("show_time")

        getFreshSignArgs(roomId, forceRefresh = true)

        val bizAll = roomInfo.optJSONObject("room_biz_all")
        val hot = bizAll?.optStringValue("hot")?.toIntOrNull() ?: 0

        return LiveRoomDetail(
            cover = roomInfo.optString("room_pic", ""),
            online = hot,
            roomId = roomInfo.optStringValue("room_id"),
            title = roomInfo.optString("room_name", ""),
            userName = roomInfo.optString("owner_name", ""),
            userAvatar = roomInfo.optString("owner_avatar", ""),
            introduction = roomInfo.optString("show_details", ""),
            notice = "",
            status = roomInfo.optInt("show_status") == 1 && roomInfo.optInt("videoLoop") != 1,
            url = "https://www.douyu.com/$roomId",
            isRecord = roomInfo.optInt("videoLoop") == 1,
            showTime = showTime,
            categoryId = roomInfo.optNullableStringValue("cate2Id")
                ?: roomInfo.optNullableStringValue("cate_id")
                ?: roomInfo.optNullableStringValue("cid1"),
            categoryName = roomInfo.optNullableStringValue("cate2Name")
                ?: roomInfo.optNullableStringValue("game_name")
                ?: roomInfo.optNullableStringValue("cate_name"),
            categoryParentId = roomInfo.optNullableStringValue("cate1Id")
                ?: roomInfo.optNullableStringValue("cid2"),
            categoryParentName = roomInfo.optNullableStringValue("cate1Name")
                ?: roomInfo.optNullableStringValue("parent_cate_name"),
            categoryPic = roomInfo.optNullableStringValue("game_icon")
                ?: roomInfo.optNullableStringValue("game_icon_url")
        )
    }

    // ── Play qualities ──────────────────────────────────────────────────

    override suspend fun getPlayQualites(detail: LiveRoomDetail): List<LivePlayQuality> {
        val signArgs = getFreshSignArgs(detail.roomId)

        // Build POST data: signing args + SDK params
        val params = parseQueryString(signArgs).toMutableMap()
        params["cdn"] = ""
        params["rate"] = "-1"
        params["ver"] = "Douyu_223061205"
        params["iar"] = "1"
        params["ive"] = "1"
        params["hevc"] = "0"
        params["fa"] = "0"

        val result = httpClient.postJson(
            "https://www.douyu.com/lapi/live/getH5Play/${detail.roomId}",
            data = params,
            header = mapOf(
                "referer" to "https://www.douyu.com/${detail.roomId}",
                "user-agent" to DEFAULT_USER_AGENT
            ),
            formUrlEncoded = true
        ) as JSONObject

        val data = result.getJSONObject("data")

        // Collect available CDNs
        val cdnsWithName = data.getJSONArray("cdnsWithName")
        val cdns = mutableListOf<String>()
        for (i in 0 until cdnsWithName.length()) {
            cdns.add(cdnsWithName.getJSONObject(i).optString("cdn", ""))
        }

        // Sort: scdn CDNs last
        cdns.sortWith { a, b ->
            when {
                a.startsWith("scdn") && !b.startsWith("scdn") -> 1
                !a.startsWith("scdn") && b.startsWith("scdn") -> -1
                else -> 0
            }
        }

        // Build quality list from multirates
        val multirates = data.getJSONArray("multirates")
        val qualities = mutableListOf<LivePlayQuality>()
        for (i in 0 until multirates.length()) {
            val item = multirates.getJSONObject(i)
            qualities.add(
                LivePlayQuality(
                    quality = item.optString("name", ""),
                    data = PlayQualityData.Douyu(
                        rate = item.optInt("rate"),
                        cdns = cdns
                    )
                )
            )
        }

        return qualities
    }

    // ── Play URLs ───────────────────────────────────────────────────────

    override suspend fun getPlayUrls(
        detail: LiveRoomDetail,
        quality: LivePlayQuality
    ): LivePlayUrl {
        val signArgs = getFreshSignArgs(detail.roomId)
        val playData = quality.data as PlayQualityData.Douyu

        val urls = mutableListOf<String>()
        for (cdn in playData.cdns) {
            try {
                val url = getPlayUrl(detail.roomId, signArgs, playData.rate, cdn)
                if (url.isNotEmpty()) urls.add(url)
            } catch (e: Exception) {
                CoreLog.e("Failed to get play URL for CDN $cdn", e)
            }
        }

        return LivePlayUrl(urls = urls)
    }

    /**
     * Fetch a single play URL for the given CDN and rate.
     */
    private suspend fun getPlayUrl(
        roomId: String,
        signArgs: String,
        rate: Int,
        cdn: String
    ): String {
        val params = parseQueryString(signArgs).toMutableMap()
        params["cdn"] = cdn
        params["rate"] = rate.toString()

        val result = httpClient.postJson(
            "https://www.douyu.com/lapi/live/getH5Play/$roomId",
            data = params,
            header = mapOf(
                "referer" to "https://www.douyu.com/$roomId",
                "user-agent" to DEFAULT_USER_AGENT
            ),
            formUrlEncoded = true
        ) as JSONObject

        val data = result.getJSONObject("data")
        val rtmpUrl = data.optString("rtmp_url", "")
        val rtmpLive = htmlUnescape(data.optString("rtmp_live", ""))
        return "$rtmpUrl/$rtmpLive"
    }

    private suspend fun getFreshSignArgs(
        roomId: String,
        forceRefresh: Boolean = false
    ): String {
        val nowMillis = System.currentTimeMillis()
        val cached = signArgsCache[roomId]
        if (cached != null && !forceRefresh && !shouldRefreshDouyuSignArgs(cached, nowMillis)) {
            return cached.value
        }

        val jsEncResult = httpClient.getText(
            "https://www.douyu.com/swf_api/homeH5Enc?rids=$roomId",
            queryParameters = emptyMap(),
            header = mapOf(
                "referer" to "https://www.douyu.com/$roomId",
                "user-agent" to DEFAULT_USER_AGENT
            )
        )
        val jsEncJson = JSONObject(jsEncResult)
        val crptext = jsEncJson.getJSONObject("data").optStringValue("room$roomId")
        val signArgs = DouyuSign.getSign(crptext, roomId, jsEngineProvider.get())
        signArgsCache[roomId] = DouyuSignArgsCacheEntry(
            value = signArgs,
            createdAtMillis = nowMillis
        )
        return signArgs
    }

    // ── Search rooms ────────────────────────────────────────────────────

    override suspend fun searchRooms(keyword: String, page: Int): LiveSearchRoomResult {
        val did = generateRandomHexString(32)
        val result = httpClient.getJson(
            "https://www.douyu.com/japi/search/api/searchShow",
            queryParameters = mapOf(
                "kw" to keyword,
                "page" to page.toString(),
                "pageSize" to DOUYU_SEARCH_PAGE_SIZE.toString()
            ),
            header = mapOf(
                "User-Agent" to SEARCH_USER_AGENT,
                "referer" to "https://www.douyu.com/search/",
                "Cookie" to "dy_did=$did;acf_did=$did"
            )
        ) as JSONObject

        if (result.optInt("error") != 0) {
            throw Exception(result.optString("msg", "Search failed"))
        }

        val data = result.getJSONObject("data")
        val relateShow = data.getJSONArray("relateShow")
        val items = mutableListOf<LiveRoomItem>()

        for (i in 0 until relateShow.length()) {
            val item = relateShow.getJSONObject(i)
            items.add(
                LiveRoomItem(
                    roomId = item.optStringValue("rid"),
                    title = item.optString("roomName", ""),
                    cover = resolveDouyuRoomCoverUrl(item),
                    userName = item.optString("nickName", ""),
                    faceUrl = resolveDouyuRoomFaceUrl(item),
                    online = parseHotNum(item.optStringValue("hot"))
                )
            )
        }

        val hasMore = douyuSearchHasMore(relateShow.length())
        return LiveSearchRoomResult(hasMore = hasMore, items = items)
    }

    // ── Search anchors ──────────────────────────────────────────────────

    override suspend fun searchAnchors(keyword: String, page: Int): LiveSearchAnchorResult {
        val did = generateRandomHexString(32)
        val result = httpClient.getJson(
            "https://www.douyu.com/japi/search/api/searchUser",
            queryParameters = mapOf(
                "kw" to keyword,
                "page" to page.toString(),
                "pageSize" to DOUYU_SEARCH_PAGE_SIZE.toString(),
                "filterType" to "1"
            ),
            header = mapOf(
                "User-Agent" to SEARCH_USER_AGENT,
                "referer" to "https://www.douyu.com/search/",
                "Cookie" to "dy_did=$did;acf_did=$did"
            )
        ) as JSONObject

        val data = result.getJSONObject("data")
        val relateUser = data.getJSONArray("relateUser")
        val items = mutableListOf<LiveAnchorItem>()

        for (i in 0 until relateUser.length()) {
            val item = relateUser.getJSONObject(i)
            val anchorInfo = item.getJSONObject("anchorInfo")
            val liveStatus =
                (anchorInfo.optStringValue("isLive").toIntOrNull() ?: 0) == 1
            val roomType =
                anchorInfo.optStringValue("roomType").toIntOrNull() ?: 0

            items.add(
                LiveAnchorItem(
                    roomId = anchorInfo.optStringValue("rid"),
                    avatar = anchorInfo.optString("avatar", ""),
                    userName = anchorInfo.optString("nickName", ""),
                    liveStatus = liveStatus && roomType == 0
                )
            )
        }

        val hasMore = douyuSearchHasMore(relateUser.length())
        return LiveSearchAnchorResult(hasMore = hasMore, items = items)
    }

    // ── Live status ─────────────────────────────────────────────────────

    override suspend fun getLiveStatus(roomId: String): Boolean {
        val roomInfo = getRoomInfo(roomId)
        return roomInfo.optInt("show_status") == 1 && roomInfo.optInt("videoLoop") != 1
    }

    // ── Super chat ──────────────────────────────────────────────────────

    override suspend fun getSuperChatMessage(
        roomId: String,
        detail: LiveRoomDetail?
    ): List<LiveSuperChatMessage> {
        // Not supported for Douyu
        return emptyList()
    }

    // ── Contribution rank ───────────────────────────────────────────────

    override suspend fun getContributionRank(
        roomId: String,
        detail: LiveRoomDetail?
    ): List<LiveContributionRankItem> {
        val result = httpClient.getJson(
            "https://www.douyu.com/japi/interact/comm/fanshome/rank/top10",
            queryParameters = mapOf("rid" to roomId),
            header = mapOf(
                "referer" to "https://www.douyu.com/$roomId",
                "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            )
        ) as JSONObject

        val data = result.optJSONObject("data")
        val intimacyRank = data?.optJSONArray("intimacyRank")

        if (intimacyRank == null || intimacyRank.length() == 0) {
            return emptyList()
        }

        return (0 until intimacyRank.length()).map { i ->
            val item = intimacyRank.getJSONObject(i)
            LiveContributionRankItem(
                rank = item.optStringValue("rank").toIntOrNull() ?: 0,
                userName = item.optString("nickname", ""),
                avatar = item.optString("avatar", ""),
                scoreText = item.optStringValue("value", "0"),
                scoreDetail = "亲密度"
            )
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────

    /**
     * Fetch room info from the betard API, with fallback to open API.
     */
    private suspend fun getRoomInfo(roomId: String): JSONObject {
        try {
            val result = httpClient.getJson(
                "https://www.douyu.com/betard/$roomId",
                queryParameters = emptyMap(),
                header = mapOf(
                    "referer" to "https://www.douyu.com/$roomId",
                    "user-agent" to DEFAULT_USER_AGENT
                )
            )

            val jsonResult = when (result) {
                is JSONObject -> result
                is String -> {
                    if (result.trim().startsWith("<!DOCTYPE")) {
                        throw Exception("betard API returned HTML page instead of JSON")
                    }
                    JSONObject(result)
                }
                else -> result as JSONObject
            }
            return jsonResult.getJSONObject("room")
        } catch (e: Exception) {
            CoreLog.w("Failed to get room info from betard API, falling back to open API: ${e.message}")
            try {
                val openResult = httpClient.getJson(
                    "https://open.douyucdn.cn/api/RoomApi/room/$roomId",
                    queryParameters = emptyMap(),
                    header = mapOf(
                        "User-Agent" to DEFAULT_USER_AGENT
                    )
                ) as JSONObject

                if (openResult.optInt("error") == 0) {
                    val data = openResult.getJSONObject("data")
                    val roomObj = JSONObject()
                    roomObj.put("room_id", data.optString("room_id"))
                    roomObj.put("room_pic", data.optString("room_thumb"))
                    roomObj.put("room_name", data.optString("room_name"))
                    roomObj.put("owner_name", data.optString("owner_name"))
                    roomObj.put("owner_avatar", data.optString("avatar"))
                    roomObj.put("show_status", data.optInt("room_status"))
                    roomObj.put("videoLoop", 0)
                    roomObj.put("cate_id", data.optString("cate_id"))
                    roomObj.put("game_name", data.optString("cate_name"))

                    val bizAll = JSONObject()
                    bizAll.put("hot", data.optInt("online"))
                    roomObj.put("room_biz_all", bizAll)

                    return roomObj
                } else {
                    throw Exception("Open API returned error: " + openResult.optString("data"))
                }
            } catch (fallbackEx: Exception) {
                CoreLog.e("Fallback to Open API also failed", fallbackEx)
                throw fallbackEx
            }
        }
    }

    /**
     * Generate a random hexadecimal string of the specified length.
     */
    private fun generateRandomHexString(length: Int): String {
        val random = SecureRandom()
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(Integer.toHexString(random.nextInt(16)))
        }
        return sb.toString()
    }

    /**
     * Parse a hot number string with Chinese unit suffixes into an integer.
     * E.g. "1.5亿" -> 150000000, "1.5万" -> 15000, "1234" -> 1234
     */
    private fun parseHotNum(hn: String): Int {
        return try {
            when {
                hn.contains("亿") -> (hn.replace("亿", "").toDouble() * 100_000_000).toInt()
                hn.contains("万") -> (hn.replace("万", "").toDouble() * 10_000).toInt()
                else -> hn.toDouble().toInt()
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Parse a query string (e.g. "key1=val1&key2=val2") into a Map.
     */
    private fun parseQueryString(query: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val eqIdx = pair.indexOf('=')
            if (eqIdx > 0) {
                params[pair.substring(0, eqIdx)] = pair.substring(eqIdx + 1)
            }
        }
        return params
    }

    /**
     * Basic HTML entity unescaping.
     * Converts common HTML entities back to their character equivalents.
     */
    private fun htmlUnescape(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
    }
}
