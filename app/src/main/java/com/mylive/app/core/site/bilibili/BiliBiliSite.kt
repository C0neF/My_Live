package com.mylive.app.core.site.bilibili

import com.mylive.app.core.common.CoreLog
import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.common.WebSocketUtils
import com.mylive.app.core.model.DanmakuArgs
import com.mylive.app.core.model.LiveAnchorItem
import com.mylive.app.core.model.LiveCategory
import com.mylive.app.core.model.LiveCategoryResult
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.core.model.LivePlayUrl
import com.mylive.app.core.model.LiveRoomDetail
import com.mylive.app.core.model.LiveRoomItem
import com.mylive.app.core.model.LiveSearchAnchorResult
import com.mylive.app.core.model.LiveSearchRoomResult
import com.mylive.app.core.model.LiveSubCategory
import com.mylive.app.core.model.LiveSuperChatMessage
import com.mylive.app.core.model.PlayQualityData
import com.mylive.app.core.site.LiveDanmaku
import com.mylive.app.core.site.LiveSite
import com.mylive.app.core.site.optNullableStringValue
import com.mylive.app.core.site.optStringValue
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiliBiliSite @Inject constructor(
    private val httpClient: HttpClient,
    private val okHttpClient: OkHttpClient
) : LiveSite {

    override fun getDanmaku(): LiveDanmaku = BiliBiliDanmaku(WebSocketUtils(okHttpClient))

    override val id: String = "bilibili"
    override val name: String = "哔哩哔哩直播"

    var cookie: String = ""
        set(value) {
            val nextCookie = value.trim()
            if (field != nextCookie) {
                buvid3 = ""
                buvid4 = ""
                accessId = ""
                lastDanmakuArgs = null
            }
            field = nextCookie
            userId = parseBilibiliUserIdFromCookie(nextCookie)
        }
    var userId: Int = 0
        private set

    private var buvid3: String = ""
    private var buvid4: String = ""
    private var accessId: String = ""

    companion object {
        private const val kDefaultUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0"
        private const val kDefaultReferer = "https://live.bilibili.com/"

        @Volatile private var kImgKey: String = ""
        @Volatile private var kSubKey: String = ""
        @Volatile private var kKeysFetchedAt: Long = 0L
        private const val WBI_KEYS_EXPIRY_MS = 3_600_000L // 1 hour

        private val mixinKeyEncTab = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
        )
    }

    private suspend fun getHeader(): Map<String, String> {
        if (buvid3.isEmpty()) {
            val buvidInfo = getBuvid()
            buvid3 = buvidInfo.optString("b_3", "")
            buvid4 = buvidInfo.optString("b_4", "")
        }
        return if (cookie.isEmpty()) {
            mapOf(
                "user-agent" to kDefaultUserAgent,
                "referer" to kDefaultReferer,
                "cookie" to "buvid3=$buvid3;buvid4=$buvid4;"
            )
        } else {
            mapOf(
                "cookie" to if (cookie.contains("buvid3")) cookie else "$cookie;buvid3=$buvid3;buvid4=$buvid4;",
                "user-agent" to kDefaultUserAgent,
                "referer" to kDefaultReferer
            )
        }
    }

    override suspend fun getCategories(): List<LiveCategory> {
        val categories = mutableListOf<LiveCategory>()
        val result = httpClient.getJson(
            "https://api.live.bilibili.com/room/v1/Area/getList",
            queryParameters = mapOf("need_entrance" to "1", "parent_id" to "0"),
            header = getHeader()
        ) as JSONObject
        val dataArray = result.getJSONArray("data")
        for (i in 0 until dataArray.length()) {
            val item = dataArray.getJSONObject(i)
            val subs = mutableListOf<LiveSubCategory>()
            val subList = item.getJSONArray("list")
            for (j in 0 until subList.length()) {
                val subItem = subList.getJSONObject(j)
                val subCategory = LiveSubCategory(
                    id = subItem.optStringValue("id"),
                    name = subItem.optString("name", ""),
                    parentId = subItem.optString("parent_id", ""),
                    pic = "${subItem.optString("pic", "")}@100w.png"
                )
                subs.add(subCategory)
            }
            val category = LiveCategory(
                children = subs,
                id = item.optStringValue("id"),
                name = item.optString("name", "")
            )
            categories.add(category)
        }
        return categories
    }

    override suspend fun getCategoryRooms(
        category: LiveSubCategory,
        page: Int
    ): LiveCategoryResult {
        val result = httpClient.getJson(
            "https://api.live.bilibili.com/room/v1/Area/getRoomList",
            queryParameters = mapOf(
                "platform" to "web",
                "parent_area_id" to category.parentId,
                "area_id" to category.id,
                "page" to page.toString(),
                "page_size" to "30"
            ),
            header = getHeader()
        ) as JSONObject
        val data = result.optJSONArray("data")
        val hasMore = data != null && data.length() >= 30
        val items = mutableListOf<LiveRoomItem>()
        if (data != null) {
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val cover = item.optNullableStringValue("cover")
                    ?: item.optNullableStringValue("user_cover")
                    ?: item.optNullableStringValue("system_cover")
                    ?: ""
                val roomItem = LiveRoomItem(
                    roomId = item.optStringValue("roomid"),
                    title = item.optString("title", ""),
                    cover = if (cover.isEmpty()) "" else "$cover@400w.jpg",
                    userName = item.optString("uname", ""),
                    faceUrl = item.optString("face", ""),
                    online = item.optStringValue("online").toIntOrNull() ?: 0
                )
                items.add(roomItem)
            }
        }
        return LiveCategoryResult(hasMore = hasMore, items = items)
    }

    override suspend fun getPlayQualites(detail: LiveRoomDetail): List<LivePlayQuality> {
        val qualities = mutableListOf<LivePlayQuality>()
        try {
            val result = httpClient.getJson(
                "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo",
                queryParameters = mapOf(
                    "room_id" to detail.roomId,
                    "protocol" to "0,1",
                    "format" to "0,1,2",
                    "codec" to "0,1",
                    "platform" to "web"
                ),
                header = getHeader()
            ) as JSONObject
            val qualitiesMap = mutableMapOf<Int, String>()
            val playurlData = result.optJSONObject("data")
                ?.optJSONObject("playurl_info")
                ?.optJSONObject("playurl") ?: return qualities

            val gQnDesc = playurlData.optJSONArray("g_qn_desc") ?: return qualities
            for (i in 0 until gQnDesc.length()) {
                val item = gQnDesc.optJSONObject(i) ?: continue
                qualitiesMap[item.optStringValue("qn").toIntOrNull() ?: 0] = item.optString("desc", "")
            }

            val acceptQn = playurlData.optJSONArray("stream")
                ?.optJSONObject(0)
                ?.optJSONArray("format")
                ?.optJSONObject(0)
                ?.optJSONArray("codec")
                ?.optJSONObject(0)
                ?.optJSONArray("accept_qn") ?: return qualities
            for (i in 0 until acceptQn.length()) {
                val item = acceptQn.optInt(i)
                val qualityItem = LivePlayQuality(
                    quality = qualitiesMap[item] ?: "未知清晰度",
                    data = PlayQualityData.BiliBili(qualityId = item)
                )
                qualities.add(qualityItem)
            }
        } catch (e: Exception) {
            CoreLog.e("BiliBili getPlayQualites failed", e)
        }
        return qualities
    }

    override suspend fun getPlayUrls(
        detail: LiveRoomDetail,
        quality: LivePlayQuality
    ): LivePlayUrl {
        val qualityId = (quality.data as PlayQualityData.BiliBili).qualityId
        val headers = mapOf(
            "referer" to "https://live.bilibili.com",
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36 Edg/115.0.1901.188"
        )
        return try {
            val result = httpClient.getJson(
                "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo",
                queryParameters = mapOf(
                    "room_id" to detail.roomId,
                    "protocol" to "0,1",
                    "format" to "0,2",
                    "codec" to "0",
                    "platform" to "web",
                    "qn" to qualityId.toString()
                ),
                header = getHeader()
            ) as JSONObject
            val playurlData = result.optJSONObject("data")
                ?.optJSONObject("playurl_info")
                ?.optJSONObject("playurl")
                ?: return LivePlayUrl(urls = emptyList(), headers = headers)
            parseBiliBiliPlayback(
                playurlData = playurlData,
                requestedQuality = quality
            ).copy(headers = headers)
        } catch (e: Exception) {
            CoreLog.e("BiliBili getPlayUrls failed", e)
            LivePlayUrl(urls = emptyList(), headers = headers)
        }
    }

    override suspend fun getRecommendRooms(page: Int): LiveCategoryResult {
        val result = httpClient.getJson(
            "https://api.live.bilibili.com/room/v1/Area/getRoomList",
            queryParameters = mapOf(
                "platform" to "web",
                "page" to page.toString(),
                "page_size" to "30"
            ),
            header = getHeader()
        ) as JSONObject

        val list = result.optJSONArray("data")
        val hasMore = list != null && list.length() >= 30
        val items = mutableListOf<LiveRoomItem>()
        if (list != null) {
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val cover = item.optNullableStringValue("cover")
                    ?: item.optNullableStringValue("user_cover")
                    ?: item.optNullableStringValue("system_cover")
                    ?: ""
                val roomItem = LiveRoomItem(
                    roomId = item.optStringValue("roomid"),
                    title = item.optString("title", ""),
                    cover = if (cover.isEmpty()) "" else "$cover@400w.jpg",
                    userName = item.optString("uname", ""),
                    faceUrl = item.optString("face", ""),
                    online = item.optStringValue("online").toIntOrNull() ?: 0
                )
                items.add(roomItem)
            }
        }
        return LiveCategoryResult(hasMore = hasMore, items = items)
    }

    override suspend fun getRoomDetail(roomId: String): LiveRoomDetail {
        val roomInfo = getRoomInfo(roomId)
        val roomInfoData = roomInfo.getJSONObject("room_info")
        val realRoomId = roomInfoData.optStringValue("room_id")

        val danmuInfoBaseUrl =
            "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo"
        val danmuInfoUrl = "$danmuInfoBaseUrl?id=$realRoomId"
        val queryParams = getWbiSign(danmuInfoUrl)
        val roomDanmakuResult = httpClient.getJson(
            danmuInfoBaseUrl,
            queryParameters = queryParams,
            header = getHeader()
        ) as JSONObject

        val hostList = roomDanmakuResult.getJSONObject("data").getJSONArray("host_list")
        val serverHosts = mutableListOf<String>()
        for (i in 0 until hostList.length()) {
            serverHosts.add(hostList.getJSONObject(i).getString("host"))
        }

        // Store danmaku args for later retrieval by LiveRoomViewModel
        lastDanmakuArgs = DanmakuArgs.BiliBili(
            roomId = realRoomId.toIntOrNull() ?: 0,
            uid = userId,
            token = roomDanmakuResult.getJSONObject("data").optString("token", ""),
            serverHost = serverHosts.firstOrNull() ?: "broadcastlv.chat.bilibili.com",
            buvid = buvid3,
            cookie = cookie
        )

        val liveStartTime = roomInfoData.optNullableStringValue("live_start_time")

        val anchorInfo = roomInfo.getJSONObject("anchor_info")
        val baseInfo = anchorInfo.getJSONObject("base_info")

        return LiveRoomDetail(
            roomId = realRoomId,
            title = roomInfoData.optString("title", ""),
            cover = roomInfoData.optString("cover", ""),
            userName = baseInfo.optString("uname", ""),
            userAvatar = "${baseInfo.optString("face", "")}@100w.jpg",
            online = roomInfoData.optStringValue("online").toIntOrNull() ?: 0,
            status = (roomInfoData.optStringValue("live_status").toIntOrNull() ?: 0) == 1,
            url = "https://live.bilibili.com/$roomId",
            introduction = roomInfoData.optString("description", ""),
            notice = "",
            showTime = liveStartTime,
            categoryId = roomInfoData.optNullableStringValue("area_id"),
            categoryName = roomInfoData.optNullableStringValue("area_name"),
            categoryParentId = roomInfoData.optNullableStringValue("parent_area_id"),
            categoryParentName = roomInfoData.optNullableStringValue("parent_area_name")
        )
    }

    /**
     * Returns the DanmakuArgs for the most recently fetched room detail.
     * Call this after [getRoomDetail] to obtain the args needed by [LiveDanmaku.start].
     */
    var lastDanmakuArgs: DanmakuArgs.BiliBili? = null
        private set

    /**
     * Fetches room detail and also stores the danmaku args for later retrieval.
     */
    suspend fun getRoomDetailWithDanmakuArgs(roomId: String): Pair<LiveRoomDetail, DanmakuArgs.BiliBili> {
        val roomInfo = getRoomInfo(roomId)
        val roomInfoData = roomInfo.getJSONObject("room_info")
        val realRoomId = roomInfoData.optStringValue("room_id")

        val danmuInfoBaseUrl =
            "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo"
        val danmuInfoUrl = "$danmuInfoBaseUrl?id=$realRoomId"
        val queryParams = getWbiSign(danmuInfoUrl)
        val roomDanmakuResult = httpClient.getJson(
            danmuInfoBaseUrl,
            queryParameters = queryParams,
            header = getHeader()
        ) as JSONObject

        val hostList = roomDanmakuResult.getJSONObject("data").getJSONArray("host_list")
        val serverHosts = mutableListOf<String>()
        for (i in 0 until hostList.length()) {
            serverHosts.add(hostList.getJSONObject(i).getString("host"))
        }

        val liveStartTime = roomInfoData.optNullableStringValue("live_start_time")

        val anchorInfo = roomInfo.getJSONObject("anchor_info")
        val baseInfo = anchorInfo.getJSONObject("base_info")

        val detail = LiveRoomDetail(
            roomId = realRoomId,
            title = roomInfoData.optString("title", ""),
            cover = roomInfoData.optString("cover", ""),
            userName = baseInfo.optString("uname", ""),
            userAvatar = "${baseInfo.optString("face", "")}@100w.jpg",
            online = roomInfoData.optStringValue("online").toIntOrNull() ?: 0,
            status = (roomInfoData.optStringValue("live_status").toIntOrNull() ?: 0) == 1,
            url = "https://live.bilibili.com/$roomId",
            introduction = roomInfoData.optString("description", ""),
            notice = "",
            showTime = liveStartTime,
            categoryId = roomInfoData.optNullableStringValue("area_id"),
            categoryName = roomInfoData.optNullableStringValue("area_name"),
            categoryParentId = roomInfoData.optNullableStringValue("parent_area_id"),
            categoryParentName = roomInfoData.optNullableStringValue("parent_area_name")
        )

        val danmakuArgs = DanmakuArgs.BiliBili(
            roomId = realRoomId.toIntOrNull() ?: 0,
            uid = userId,
            token = roomDanmakuResult.getJSONObject("data").optString("token", ""),
            serverHost = serverHosts.firstOrNull() ?: "broadcastlv.chat.bilibili.com",
            buvid = buvid3,
            cookie = cookie
        )
        lastDanmakuArgs = danmakuArgs
        return Pair(detail, danmakuArgs)
    }

    private suspend fun getRoomInfo(roomId: String): JSONObject {
        val url =
            "https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom?room_id=$roomId"
        val queryParams = getWbiSign(url)
        val result = httpClient.getJson(
            "https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom",
            queryParameters = queryParams,
            header = getHeader()
        ) as JSONObject
        return result.getJSONObject("data")
    }

    override suspend fun searchRooms(keyword: String, page: Int): LiveSearchRoomResult {
        val result = httpClient.getJson(
            "https://api.bilibili.com/x/web-interface/search/type?context=&search_type=live&cover_type=user_cover",
            queryParameters = mapOf(
                "order" to "",
                "keyword" to keyword,
                "category_id" to "",
                "__refresh__" to "",
                "_extra" to "",
                "highlight" to "0",
                "single_column" to "0",
                "page" to page.toString()
            ),
            header = getHeader()
        ) as JSONObject

        val items = mutableListOf<LiveRoomItem>()
        val liveRoomArray = result.optJSONObject("data")
            ?.optJSONObject("result")
            ?.optJSONArray("live_room")
        if (liveRoomArray != null) {
            for (i in 0 until liveRoomArray.length()) {
                val item = liveRoomArray.getJSONObject(i)
                var title = item.optString("title", "")
                // Remove <em></em> tags from title
                title = title.replace(Regex("</?em[^>]*>"), "")
                val roomItem = LiveRoomItem(
                    roomId = item.optStringValue("roomid"),
                    title = title,
                    cover = "https:${item.optString("cover", "")}@400w.jpg",
                    userName = item.optString("uname", ""),
                    faceUrl = if (item.optString("uface", "").startsWith("//")) "https:${item.optString("uface")}" else item.optString("uface"),
                    online = item.optStringValue("online").toIntOrNull() ?: 0
                )
                items.add(roomItem)
            }
        }
        return LiveSearchRoomResult(hasMore = items.size >= 40, items = items)
    }

    override suspend fun searchAnchors(keyword: String, page: Int): LiveSearchAnchorResult {
        val result = httpClient.getJson(
            "https://api.bilibili.com/x/web-interface/search/type?context=&search_type=live_user&cover_type=user_cover",
            queryParameters = mapOf(
                "order" to "",
                "keyword" to keyword,
                "category_id" to "",
                "__refresh__" to "",
                "_extra" to "",
                "highlight" to "0",
                "single_column" to "0",
                "page" to page.toString()
            ),
            header = getHeader()
        ) as JSONObject

        val items = mutableListOf<LiveAnchorItem>()
        val resultArray = result.optJSONObject("data")?.optJSONArray("result")
        if (resultArray != null) {
            for (i in 0 until resultArray.length()) {
                val item = resultArray.getJSONObject(i)
                var uname = item.optString("uname", "")
                // Remove <em></em> tags from uname
                uname = uname.replace(Regex("</?em[^>]*>"), "")
                val anchorItem = LiveAnchorItem(
                    roomId = item.optStringValue("roomid"),
                    avatar = "https:${item.optString("uface", "")}@400w.jpg",
                    userName = uname,
                    liveStatus = item.optInt("is_live") == 1
                )
                items.add(anchorItem)
            }
        }
        return LiveSearchAnchorResult(hasMore = items.size >= 40, items = items)
    }

    override suspend fun getLiveStatus(roomId: String): Boolean {
        val result = httpClient.getJson(
            "https://api.live.bilibili.com/room/v1/Room/get_info",
            queryParameters = mapOf("room_id" to roomId),
            header = getHeader()
        ) as JSONObject
        return (result.optJSONObject("data")?.optStringValue("live_status")?.toIntOrNull() ?: 0) == 1
    }

    override suspend fun getSuperChatMessage(
        roomId: String,
        detail: LiveRoomDetail?
    ): List<LiveSuperChatMessage> {
        val result = httpClient.getJson(
            "https://api.live.bilibili.com/av/v1/SuperChat/getMessageList",
            queryParameters = mapOf("room_id" to roomId),
            header = getHeader()
        ) as JSONObject
        val ls = mutableListOf<LiveSuperChatMessage>()
        val list = result.optJSONObject("data")?.optJSONArray("list")
        if (list != null) {
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val userInfo = item.optJSONObject("user_info")
                val message = LiveSuperChatMessage(
                    backgroundBottomColor = item.optString("background_bottom_color", ""),
                    backgroundColor = item.optString("background_color", ""),
                    endTime = item.optLong("end_time") * 1000,
                    face = "${userInfo?.optString("face", "")}@200w.jpg",
                    message = item.optString("message", ""),
                    price = item.optInt("price"),
                    startTime = item.optLong("start_time") * 1000,
                    userName = userInfo?.optString("uname", "") ?: ""
                )
                ls.add(message)
            }
        }
        return ls
    }

    /**
     * Get buvid3 and buvid4 tokens from Bilibili fingerprint API.
     */
    private suspend fun getBuvid(): JSONObject {
        return try {
            if (cookie.contains("buvid3")) {
                val b3Match = Regex("buvid3=(.*?);").find(cookie)
                val b4Match = Regex("buvid4=(.*?);").find(cookie)
                return JSONObject().apply {
                    put("b_3", b3Match?.groupValues?.get(1) ?: "")
                    put("b_4", b4Match?.groupValues?.get(1) ?: "")
                }
            }
            val result = httpClient.getJson(
                "https://api.bilibili.com/x/frontend/finger/spi",
                queryParameters = emptyMap(),
                header = mapOf(
                    "user-agent" to kDefaultUserAgent,
                    "referer" to kDefaultReferer,
                    "cookie" to cookie
                )
            ) as JSONObject
            result.getJSONObject("data")
        } catch (e: Exception) {
            CoreLog.e("getBuvid failed", e)
            JSONObject().apply {
                put("b_3", "")
                put("b_4", "")
            }
        }
    }

    /**
     * Fetch the latest WBI img_key and sub_key from the nav API.
     */
    private suspend fun getWbiKeys(): Pair<String, String> {
        if (kImgKey.isNotEmpty() && kSubKey.isNotEmpty()
            && (System.currentTimeMillis() - kKeysFetchedAt) < WBI_KEYS_EXPIRY_MS) {
            return Pair(kImgKey, kSubKey)
        }
        val resp = httpClient.getJson(
            "https://api.bilibili.com/x/web-interface/nav",
            header = getHeader()
        ) as JSONObject

        val imgUrl = resp.getJSONObject("data").getJSONObject("wbi_img").getString("img_url")
        val subUrl = resp.getJSONObject("data").getJSONObject("wbi_img").getString("sub_url")
        val imgKey = imgUrl.substring(imgUrl.lastIndexOf('/') + 1).split('.')[0]
        val subKey = subUrl.substring(subUrl.lastIndexOf('/') + 1).split('.')[0]

        kImgKey = imgKey
        kSubKey = subKey
        kKeysFetchedAt = System.currentTimeMillis()

        return Pair(imgKey, subKey)
    }

    /**
     * Shuffle the origin key using the mixinKeyEncTab to produce the mixin key.
     */
    private fun getMixinKey(origin: String): String {
        return mixinKeyEncTab.fold("") { s, i -> s + origin[i] }.substring(0, 32)
    }

    /**
     * Compute WBI signature for the given URL.
     * Parses query parameters from the URL, adds wts, sorts, filters, signs, and returns
     * the complete query parameter map including w_rid.
     */
    private suspend fun getWbiSign(url: String): Map<String, String> {
        val (imgKey, subKey) = getWbiKeys()

        val mixinKey = getMixinKey(imgKey + subKey)
        val currentTime = System.currentTimeMillis() / 1000

        // Parse query parameters from the URL
        val queryParams = parseQueryParams(url).toMutableMap()

        queryParams["wts"] = currentTime.toString()

        // Sort parameters by key
        val sortedKeys = queryParams.keys.sorted()
        val filteredMap = mutableMapOf<String, String>()
        for (key in sortedKeys) {
            val value = queryParams[key] ?: ""
            // Filter out characters: !'()* (matches the canonical WBI algorithm, which
            // strips these from each value before signing). The previous check
            // `"!'()*" !in c.toString()` was a no-op — it tested substring containment of
            // the 5-char literal inside a 1-char string, which is always false.
            filteredMap[key] = value.filter { c -> c !in "!'()*" }
        }

        // Build the query string with URL-encoded values
        val query = filteredMap.entries.joinToString("&") { (key, value) ->
            "$key=${encodeQueryParam(value)}"
        }
        val wbiSign = md5("$query$mixinKey")
        queryParams["w_rid"] = wbiSign
        return queryParams
    }

    /**
     * Fetch the access_id from the Bilibili live page.
     */
    suspend fun getAccessId(): String {
        if (accessId.isNotEmpty()) {
            return accessId
        }
        val resp = httpClient.getText(
            "https://live.bilibili.com/lol",
            queryParameters = emptyMap(),
            header = getHeader()
        )
        val match = Regex("\"access_id\":\"(.*?)\"").find(resp)
        val id = match?.groupValues?.get(1)?.replace("\\", "")
        accessId = id ?: ""
        return accessId
    }

    // ── Private utility functions ──────────────────────────────────────

    /**
     * Parse query parameters from a URL string.
     */
    private fun parseQueryParams(url: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        val uri = URI(url)
        val query = uri.query ?: return params
        for (param in query.split("&")) {
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                params[java.net.URLDecoder.decode(parts[0], "UTF-8")] =
                    java.net.URLDecoder.decode(parts[1], "UTF-8")
            }
        }
        return params
    }

    /**
     * URL-encode a query parameter value, replacing + with %20 to match Dart's
     * Uri.encodeQueryComponent behavior.
     */
    private fun encodeQueryParam(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
    }

    /**
     * Compute the MD5 hash of a string and return it as a lowercase hex string.
     */
    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
