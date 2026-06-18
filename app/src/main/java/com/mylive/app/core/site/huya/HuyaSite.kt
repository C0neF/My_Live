package com.mylive.app.core.site.huya

import android.util.LruCache
import com.mylive.app.core.common.CoreError
import com.mylive.app.core.common.CoreLog
import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.common.WebSocketUtils
import com.mylive.app.core.model.*
import com.mylive.app.core.model.PlayQualityData
import com.mylive.app.core.site.LiveDanmaku
import com.mylive.app.core.site.LiveSite
import com.mylive.app.core.site.huya.tars.BaseTarsHttp
import com.mylive.app.core.site.huya.tars.model.GetCdnTokenExReq
import com.mylive.app.core.site.huya.tars.model.GetCdnTokenExResp
import com.mylive.app.core.site.huya.tars.model.GetGameEventMessageBoardReq
import com.mylive.app.core.site.huya.tars.model.GetGameEventMessageBoardRsp
import com.mylive.app.core.site.huya.tars.model.HuyaUserId
import com.mylive.app.core.site.optStringValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import kotlin.math.max
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HuyaSite @Inject constructor(
    private val httpClient: HttpClient,
    private val okHttpClient: OkHttpClient
) : LiveSite {

    companion object {
        private const val BASE_URL = "https://m.huya.com/"

        private val BUSS_TYPE_NAMES = mapOf(
            1 to "网游",
            2 to "单机",
            3 to "手游",
            8 to "娱乐"
        )

        private const val K_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36 Edg/117.0.0.0"

        private const val HYSDK_UA =
            "HYSDK(Windows, 30000002)_APP(pc_exe&7060000&official)_SDK(trans&2.32.3.5646)"

        private val REQUEST_HEADERS = mapOf(
            "Origin" to BASE_URL,
            "Referer" to BASE_URL,
            "User-Agent" to HYSDK_UA
        )
    }

    override val id: String = "huya"
    override val name: String = "虎牙直播"

    private val tupClient = BaseTarsHttp(
        baseUrl = "https://wup.huya.com",
        servantName = "liveui",
        headers = REQUEST_HEADERS,
        okHttpClient = okHttpClient
    )

    private val messageBoardClient = BaseTarsHttp(
        baseUrl = "https://wup.huya.com",
        servantName = "wupui",
        headers = REQUEST_HEADERS,
        okHttpClient = okHttpClient
    )

    private var playUserAgent: String? = null
    private var lastHeadlineEmptyLogAt: Long = 0L

    /**
     * Cached room play data, populated after [getRoomDetail] is called.
     * Used by [getPlayQualites] and [getPlayUrls].
     * Keyed by roomId. Limited to 20 entries to prevent unbounded memory growth.
     */
    private val roomPlayDataCache = LruCache<String, HuyaRoomPlayData>(20)

    /**
     * Cached danmaku args, populated after [getRoomDetail] is called.
     * Keyed by roomId. Limited to 20 entries.
     */
    private val danmakuArgsCache = LruCache<String, DanmakuArgs.Huya>(20)

    // ── Danmaku ────────────────────────────────────────────────────────────

    override fun getDanmaku(): LiveDanmaku = HuyaDanmaku(WebSocketUtils(okHttpClient))

    /**
     * Get cached danmaku args for the given roomId.
     * Must be called after [getRoomDetail] which populates [danmakuArgsCache].
     */
    fun getDanmakuArgs(roomId: String): DanmakuArgs.Huya? = danmakuArgsCache.get(roomId)

    // ── Categories ─────────────────────────────────────────────────────────

    override suspend fun getCategories(): List<LiveCategory> = withContext(Dispatchers.IO) {
        val templates = listOf(
            LiveCategory(id = "1", name = "网游"),
            LiveCategory(id = "2", name = "单机"),
            LiveCategory(id = "8", name = "娱乐"),
            LiveCategory(id = "3", name = "手游")
        )
        templates.map { cat ->
            val subs = getSubCategories(cat.id)
            cat.copy(children = subs)
        }
    }

    private suspend fun getSubCategories(id: String): List<LiveSubCategory> {
        val result = httpClient.getJson(
            "https://live.cdn.huya.com/liveconfig/game/bussLive",
            queryParameters = mapOf("bussType" to id)
        )
        val resultObj = result as JSONObject
        val data = resultObj.getJSONArray("data")
        val subs = mutableListOf<LiveSubCategory>()
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val gid = parseGid(item.get("gid"))
            subs.add(
                LiveSubCategory(
                    id = gid,
                    name = item.getString("gameFullName"),
                    parentId = id,
                    pic = "https://huyaimg.msstatic.com/cdnimage/game/$gid-MS.jpg"
                )
            )
        }
        return subs
    }

    private fun parseGid(gid: Any?): String {
        return when (gid) {
            is JSONObject -> gid.optStringValue("value").split(",").first()
            is Double -> gid.toInt().toString()
            is Int -> gid.toString()
            is Long -> gid.toInt().toString()
            else -> gid.toString()
        }
    }

    // ── Category rooms ─────────────────────────────────────────────────────

    override suspend fun getCategoryRooms(
        category: LiveSubCategory,
        page: Int
    ): LiveCategoryResult = withContext(Dispatchers.IO) {
        val resultText = httpClient.getText(
            "https://www.huya.com/cache.php",
            queryParameters = mapOf(
                "m" to "LiveList",
                "do" to "getLiveListByPage",
                "tagAll" to "0",
                "gameId" to category.id,
                "page" to page.toString()
            )
        )
        val result = JSONObject(resultText)
        val data = result.getJSONObject("data")
        val datas = data.getJSONArray("data".let {
            // The key in the JSON is "datas"
            "datas"
        })
        val items = parseRoomItems(datas)
        val hasMore = data.getInt("page") < data.getInt("totalPage")
        LiveCategoryResult(hasMore = hasMore, items = items)
    }

    // ── Recommend rooms ────────────────────────────────────────────────────

    override suspend fun getRecommendRooms(page: Int): LiveCategoryResult =
        withContext(Dispatchers.IO) {
            val resultText = httpClient.getText(
                "https://www.huya.com/cache.php",
                queryParameters = mapOf(
                    "m" to "LiveList",
                    "do" to "getLiveListByPage",
                    "tagAll" to "0",
                    "page" to page.toString()
                )
            )
            val result = JSONObject(resultText)
            val data = result.getJSONObject("data")
            val datas = data.getJSONArray("datas")
            val items = parseRoomItems(datas)
            val hasMore = data.getInt("page") < data.getInt("totalPage")
            LiveCategoryResult(hasMore = hasMore, items = items)
        }

    private fun parseRoomItems(datas: org.json.JSONArray): List<LiveRoomItem> {
        val items = mutableListOf<LiveRoomItem>()
        for (i in 0 until datas.length()) {
            val item = datas.getJSONObject(i)
            var cover = item.getString("screenshot")
            if (!cover.contains("?")) {
                cover += "?x-oss-process=style/w338_h190&"
            }
            var title = item.optString("introduction", "")
            if (title.isEmpty()) {
                title = item.optString("roomName", "")
            }
            items.add(
                LiveRoomItem(
                    roomId = item.getString("profileRoom"),
                    title = title,
                    cover = cover,
                    userName = item.getString("nick"),
                    faceUrl = item.optString("avatar180", ""),
                    online = item.optString("totalCount", "0").toIntOrNull() ?: 0
                )
            )
        }
        return items
    }

    // ── Room detail ────────────────────────────────────────────────────────

    override suspend fun getRoomDetail(roomId: String): LiveRoomDetail =
        withContext(Dispatchers.IO) {
            val roomInfo = getRoomInfo(roomId)
            val tLiveInfo = roomInfo.getJSONObject("roomInfo").getJSONObject("tLiveInfo")
            val tProfileInfo = roomInfo.getJSONObject("roomInfo").getJSONObject("tProfileInfo")
            val topSid = asPositiveLong(roomInfo.opt("topSid"))
            val subSid = asPositiveLong(roomInfo.opt("subSid"))

            var title = tLiveInfo.optString("sIntroduction", "")
            if (title.isEmpty()) {
                title = tLiveInfo.optString("sRoomName", "")
            }

            val huyaLines = mutableListOf<HuyaLineModel>()
            val huyaBitrates = mutableListOf<HuyaBitRateModel>()

            // Read available stream lines
            val streamInfoArray = tLiveInfo
                .getJSONObject("tLiveStreamInfo")
                .getJSONObject("vStreamInfo")
                .getJSONArray("value")
            for (i in 0 until streamInfoArray.length()) {
                val item = streamInfoArray.getJSONObject(i)
                val flvUrl = item.optString("sFlvUrl", "")
                if (flvUrl.isNotEmpty()) {
                    huyaLines.add(
                        HuyaLineModel(
                            line = flvUrl,
                            lineType = HuyaLineType.FLV,
                            flvAntiCode = item.optString("sFlvAntiCode", ""),
                            hlsAntiCode = item.optString("sHlsAntiCode", ""),
                            streamName = item.optString("sStreamName", ""),
                            cdnType = item.optString("sCdnType", ""),
                            presenterUid = if (topSid > 0) topSid else subSid
                        )
                    )
                }
            }

            // Read bitrates
            val bitrateArray = tLiveInfo
                .getJSONObject("tLiveStreamInfo")
                .getJSONObject("vBitRateInfo")
                .getJSONArray("value")
            for (i in 0 until bitrateArray.length()) {
                val item = bitrateArray.getJSONObject(i)
                val name = item.optString("sDisplayName", "")
                if (name.contains("HDR")) continue
                huyaBitrates.add(
                    HuyaBitRateModel(
                        name = name,
                        bitRate = item.optInt("iBitRate", 0)
                    )
                )
            }

            val categoryId = resolveHuyaCategoryId(tLiveInfo)
            val categoryName = tLiveInfo.optString("sGameFullName", "").trim()
            val categoryParentId = resolveHuyaParentCategoryId(tLiveInfo)
            val categoryParentName = resolveHuyaParentCategoryName(tLiveInfo)

            // Generate a UID
            val uid = getUid(13, 10)

            // Decode liveLineUrl
            val liveLineUrl = try {
                val encoded = roomInfo.optJSONObject("roomProfile")
                    ?.optString("liveLineUrl", "") ?: ""
                "https:${String(Base64.getDecoder().decode(encoded))}"
            } catch (e: Exception) {
                ""
            }

            // Cache site-specific play data for getPlayQualites/getPlayUrls
            val playData = HuyaRoomPlayData(
                url = liveLineUrl,
                uid = uid,
                lines = huyaLines,
                bitRates = if (huyaBitrates.isEmpty()) listOf(
                    HuyaBitRateModel(name = "原画", bitRate = 0),
                    HuyaBitRateModel(name = "高清", bitRate = 2000)
                ) else huyaBitrates
            )
            roomPlayDataCache.put(roomId, playData)

            // Cache danmaku args for the danmaku connection
            val ayyuid = tLiveInfo.optInt("lYyid", 0)
            danmakuArgsCache.put(roomId, DanmakuArgs.Huya(
                ayyuid = ayyuid,
                topSid = topSid,
                subSid = subSid
            ))

            LiveRoomDetail(
                cover = tLiveInfo.optString("sScreenshot", ""),
                online = tLiveInfo.optInt("lTotalCount", 0),
                roomId = tLiveInfo.optStringValue("lProfileRoom"),
                title = title,
                userName = tProfileInfo.optString("sNick", ""),
                userAvatar = tProfileInfo.optString("sAvatar180", ""),
                introduction = tLiveInfo.optString("sIntroduction", ""),
                notice = roomInfo.optString("welcomeText", ""),
                status = roomInfo.getJSONObject("roomInfo").optInt("eLiveStatus") == 2,
                url = "https://www.huya.com/$roomId",
                categoryId = categoryId,
                categoryName = categoryName.ifEmpty { null },
                categoryParentId = categoryParentId,
                categoryParentName = categoryParentName,
                categoryPic = resolveHuyaCategoryPic(categoryId)
            )
        }

    /**
     * Get the raw room info from the mobile page and extract HNF_GLOBAL_INIT JSON.
     * Also extracts topSid/subSid (lChannelId/lSubChannelId).
     */
    private suspend fun getRoomInfo(roomId: String): JSONObject {
        val resultText = httpClient.getText(
            "https://m.huya.com/$roomId",
            queryParameters = emptyMap(),
            header = mapOf("user-agent" to K_USER_AGENT)
        )

        // Extract window.HNF_GLOBAL_INIT = {...}
        val regex = Regex("""window\.HNF_GLOBAL_INIT.=.\{[\s\S]*?\}[\s\S]*?</script>""")
        val match = regex.find(resultText) ?: throw CoreError("无法获取房间信息")
        var jsonText = match.value
            .replace(Regex("""window\.HNF_GLOBAL_INIT.=."""), "")
            .replace("</script>", "")
            .replace(Regex("""function.*?\(.*?\).\{[\s\S]*?\}"""), "\"\"")

        val jsonObj = JSONObject(jsonText)

        // Extract topSid/subSid from the raw HTML
        var topSid = Regex("""lChannelId":([0-9]+)""").find(resultText)
            ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        var subSid = Regex("""lSubChannelId":([0-9]+)""").find(resultText)
            ?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        if (topSid <= 0L) {
            topSid = firstPositiveLongByKeys(jsonObj, setOf("lchannelid", "channelid"))
        }
        if (subSid <= 0L) {
            subSid = firstPositiveLongByKeys(jsonObj, setOf("lsubchannelid", "subchannelid"))
        }

        jsonObj.put("topSid", topSid)
        jsonObj.put("subSid", subSid)
        return jsonObj
    }

    // ── Search ─────────────────────────────────────────────────────────────

    override suspend fun searchRooms(keyword: String, page: Int): LiveSearchRoomResult =
        withContext(Dispatchers.IO) {
            val resultText = httpClient.getText(
                "https://search.cdn.huya.com/",
                queryParameters = mapOf(
                    "m" to "Search",
                    "do" to "getSearchContent",
                    "q" to keyword,
                    "uid" to "0",
                    "v" to "4",
                    "typ" to "-5",
                    "livestate" to "0",
                    "rows" to "20",
                    "start" to ((page - 1) * 20).toString()
                )
            )
            val result = JSONObject(resultText)
            val docs = result.getJSONObject("response").getJSONObject("3").getJSONArray("docs")
            val items = mutableListOf<LiveRoomItem>()
            for (i in 0 until docs.length()) {
                val item = docs.getJSONObject(i)
                var cover = item.getString("game_screenshot")
                if (!cover.contains("?")) {
                    cover += "?x-oss-process=style/w338_h190&"
                }
                var title = item.optString("game_introduction", "")
                if (title.isEmpty()) {
                    title = item.optString("game_roomName", "")
                }
                items.add(
                    LiveRoomItem(
                        roomId = item.getString("room_id"),
                        title = title,
                        cover = cover,
                        userName = item.getString("game_nick"),
                        faceUrl = item.optString("game_avatarUrl180", ""),
                        online = item.optString("game_total_count", "0").toIntOrNull() ?: 0
                    )
                )
            }
            val numFound = result.getJSONObject("response").getJSONObject("3").getInt("numFound")
            LiveSearchRoomResult(hasMore = numFound > page * 20, items = items)
        }

    override suspend fun searchAnchors(keyword: String, page: Int): LiveSearchAnchorResult =
        withContext(Dispatchers.IO) {
            val resultText = httpClient.getText(
                "https://search.cdn.huya.com/",
                queryParameters = mapOf(
                    "m" to "Search",
                    "do" to "getSearchContent",
                    "q" to keyword,
                    "uid" to "0",
                    "v" to "1",
                    "typ" to "-5",
                    "livestate" to "0",
                    "rows" to "20",
                    "start" to ((page - 1) * 20).toString()
                )
            )
            val result = JSONObject(resultText)
            val docs = result.getJSONObject("response").getJSONObject("1").getJSONArray("docs")
            val items = mutableListOf<LiveAnchorItem>()
            for (i in 0 until docs.length()) {
                val item = docs.getJSONObject(i)
                items.add(
                    LiveAnchorItem(
                        roomId = item.getString("room_id"),
                        avatar = item.optString("game_avatarUrl180", ""),
                        userName = item.getString("game_nick"),
                        liveStatus = item.optBoolean("gameLiveOn", false)
                    )
                )
            }
            val numFound = result.getJSONObject("response").getJSONObject("1").getInt("numFound")
            LiveSearchAnchorResult(hasMore = numFound > page * 20, items = items)
        }

    // ── Play quality & URLs ────────────────────────────────────────────────

    override suspend fun getPlayQualites(detail: LiveRoomDetail): List<LivePlayQuality> {
        val playData = roomPlayDataCache.get(detail.roomId)
            ?: return emptyList()

        val qualities = mutableListOf<LivePlayQuality>()
        for ((index, bitrate) in playData.bitRates.withIndex()) {
            qualities.add(
                LivePlayQuality(
                    quality = bitrate.name,
                    data = PlayQualityData.Huya(
                        lineIndex = 0, // Use first available line
                        bitRateIndex = index
                    )
                )
            )
        }
        return qualities
    }

    override suspend fun getPlayUrls(
        detail: LiveRoomDetail,
        quality: LivePlayQuality
    ): LivePlayUrl = withContext(Dispatchers.IO) {
        val playData = roomPlayDataCache.get(detail.roomId)
            ?: return@withContext LivePlayUrl(urls = emptyList(), headers = REQUEST_HEADERS)

        val huyaQuality = quality.data as? PlayQualityData.Huya
            ?: return@withContext LivePlayUrl(urls = emptyList(), headers = REQUEST_HEADERS)

        val bitRate = playData.bitRates.getOrNull(huyaQuality.bitRateIndex)?.bitRate ?: 0
        val urls = mutableListOf<String>()
        for (line in playData.lines) {
            try {
                val url = resolvePlayUrl(line, bitRate)
                urls.add(url)
            } catch (e: Exception) {
                CoreLog.e("Failed to get play URL for line ${line.cdnType}: $e")
            }
        }
        LivePlayUrl(
            urls = urls,
            headers = mapOf(
                "User-Agent" to HYSDK_UA,
                "Referer" to BASE_URL,
                "Origin" to BASE_URL
            )
        )
    }

    /**
     * Resolve a single play URL for a stream line with the given bitrate.
     * Gets the CDN token via TARS RPC, then builds the anti-code.
     */
    private suspend fun resolvePlayUrl(line: HuyaLineModel, bitRate: Int): String =
        withContext(Dispatchers.IO) {
            val antiCode = getCdnTokenInfoEx(line.streamName)
            val builtAntiCode = buildAntiCode(line.streamName, line.presenterUid, antiCode)
            var url = "${line.line}/${line.streamName}.flv?$builtAntiCode&codec=264"
            if (bitRate > 0) {
                url += "&ratio=$bitRate"
            }
            url
        }

    // ── Anti-code generation ───────────────────────────────────────────────

    /**
     * Build the anti-code query string for a stream URL.
     *
     * This uses MD5 hashing and a rotate-left-64-bit operation on the presenter UID
     * to generate a wsSecret token that authorizes playback.
     *
     * @param stream The stream name
     * @param presenterUid The presenter's user ID (topSid or subSid)
     * @param antiCode The raw anti-code string from the CDN token response
     * @return The complete anti-code query string
     */
    fun buildAntiCode(stream: String, presenterUid: Long, antiCode: String): String {
        val mapAnti = parseQueryString(antiCode)
        if (!mapAnti.containsKey("fm")) {
            return antiCode
        }

        val ctype = mapAnti["ctype"]?.firstOrNull() ?: "huya_pc_exe"
        val platformId = mapAnti["t"]?.firstOrNull()?.toIntOrNull() ?: 0
        val isWap = platformId == 103
        val clacStartTime = System.currentTimeMillis()

        CoreLog.i("using $presenterUid | ctype-{$ctype} | platformId - {$platformId} | isWap - {$isWap} | $clacStartTime")

        val seqId = presenterUid + clacStartTime
        val secretHash = md5("$seqId|$ctype|$platformId")

        val convertUid = rotl64(presenterUid)
        val calcUid = if (isWap) presenterUid else convertUid
        // `fm` is base64 that is percent-encoded TWICE in sFlvToken. parseQueryString
        // already peeled the first (form) layer; decodeComponent peels the second
        // (component) layer so Base64 sees valid characters. Matches Dart:
        //   Uri.decodeComponent(queryParametersAll['fm'].first)
        val fm = decodeComponent(mapAnti["fm"]!!.first())
        val secretPrefix = String(Base64.getDecoder().decode(fm)).split("_").first()
        val wsTime = mapAnti["wsTime"]!!.first()
        val secretStr = "${secretPrefix}_${calcUid}_${stream}_${secretHash}_$wsTime"
        val wsSecret = md5(secretStr)

        val rnd = Random.nextDouble()
        val ct = ((wsTime.toLong(16) + rnd) * 1000).toLong()
        val uuid = (((ct % 10_000_000_000L) + rnd) * 1000 % 0xFFFFFFFF).toInt().toString()

        val antiCodeRes = mutableMapOf<String, Any>(
            "wsSecret" to wsSecret,
            "wsTime" to wsTime,
            "seqid" to seqId.toString(),
            "ctype" to ctype,
            "ver" to "1",
            "fs" to mapAnti["fs"]!!.first(),
            "fm" to encodeComponent(mapAnti["fm"]!!.first()),
            "t" to platformId
        )
        if (isWap) {
            antiCodeRes["uid"] = presenterUid.toString()
            antiCodeRes["uuid"] = uuid
        } else {
            antiCodeRes["u"] = convertUid.toString()
        }

        return antiCodeRes.entries.joinToString("&") { "${it.key}=${it.value}" }
    }

    /**
     * Get the CDN token via TARS RPC (getCdnTokenInfoEx).
     *
     * @param stream The stream name
     * @return The sFlvToken string used as anti-code input
     */
    private suspend fun getCdnTokenInfoEx(stream: String): String =
        withContext(Dispatchers.IO) {
            val tid = HuyaUserId().apply {
                sHuYaUA = "pc_exe&7060000&official"
            }
            val tReq = GetCdnTokenExReq().apply {
                this.tId = tid
                sStreamName = stream
            }
            val resp = tupClient.tupRequest("getCdnTokenInfoEx", tReq, GetCdnTokenExResp())
            resp.sFlvToken
        }

    // ── Live status ────────────────────────────────────────────────────────

    override suspend fun getLiveStatus(roomId: String): Boolean = withContext(Dispatchers.IO) {
        val roomInfo = getRoomInfo(roomId)
        roomInfo.getJSONObject("roomInfo").optInt("eLiveStatus") == 2
    }

    // ── Super chat (headline messages) ─────────────────────────────────────

    override suspend fun getSuperChatMessage(
        roomId: String,
        detail: LiveRoomDetail?
    ): List<LiveSuperChatMessage> = withContext(Dispatchers.IO) {
        val pidCandidates = mutableSetOf<Long>()

        // Try to extract pids from cached danmaku args
        val danmakuArgs = danmakuArgsCache.get(roomId)
        if (danmakuArgs != null) {
            if (danmakuArgs.topSid > 0) pidCandidates.add(danmakuArgs.topSid)
            if (danmakuArgs.subSid > 0) pidCandidates.add(danmakuArgs.subSid)
        }

        // Try to extract pids from cached play data lines
        val playData = roomPlayDataCache.get(roomId)
        if (playData != null) {
            for (line in playData.lines) {
                if (line.presenterUid > 0) pidCandidates.add(line.presenterUid)
            }
        }

        val profileRoomId = roomId.toLongOrNull() ?: 0L
        if (profileRoomId > 0L) {
            pidCandidates.add(profileRoomId)
        }

        // If we only have the profileRoomId, try to get more from room info
        if (pidCandidates.size <= 1 && pidCandidates.contains(profileRoomId)) {
            try {
                val roomInfo = getRoomInfo(roomId)
                val topSid = asPositiveLong(roomInfo.opt("topSid"))
                val subSid = asPositiveLong(roomInfo.opt("subSid"))
                if (topSid > 0L) pidCandidates.add(topSid)
                if (subSid > 0L) pidCandidates.add(subSid)
            } catch (e: Exception) {
                CoreLog.e("Huya headline room info fetch failed: $e")
            }
        }

        if (pidCandidates.isEmpty()) {
            logHeadlineEmpty(roomId, pidCandidates, "no pid candidate")
            return@withContext emptyList()
        }

        for (pid in pidCandidates) {
            if (pid <= 0L) continue
            try {
                val messages = fetchHeadlineMessages(pid)
                if (messages.isNotEmpty()) {
                    return@withContext messages
                }
            } catch (e: Exception) {
                CoreLog.e("Huya headline fetch failed for pid=$pid: $e")
            }
        }
        logHeadlineEmpty(roomId, pidCandidates, "empty response")
        emptyList()
    }

    /**
     * Fetch headline (super-chat) messages via TARS RPC.
     */
    private fun fetchHeadlineMessages(pid: Long): List<LiveSuperChatMessage> {
        val userId = HuyaUserId().apply { sHuYaUA = HYSDK_UA }
        val req = GetGameEventMessageBoardReq().apply {
            lPid = pid
            tId = userId
            iMessageBoardScope = 0
            iPageSize = 10
        }
        val rsp = messageBoardClient.tupRequest(
            "getHeadLineMessageBoard",
            req,
            GetGameEventMessageBoardRsp()
        )

        val now = System.currentTimeMillis()
        return rsp.tMessageBoardPanel.vGameEventMessageBoardInfo.mapNotNull { item ->
            val content = item.sContent.trim()
            if (content.isEmpty()) return@mapNotNull null

            val remainingSeconds = if (item.iCountDown > 0) item.iCountDown else item.iTotalSec
            if (remainingSeconds <= 0) return@mapNotNull null

            val totalSeconds = if (item.iTotalSec > 0) item.iTotalSec else remainingSeconds
            val price = when {
                item.iCost > 0 -> item.iCost
                item.iCostPay > 0 -> max(1, (item.iCostPay / 100.0).toInt())
                else -> 0
            }
            val endTimeMs = now + max(1, remainingSeconds) * 1000L
            val startTimeMs = endTimeMs - max(1, totalSeconds) * 1000L

            LiveSuperChatMessage(
                id = if (item.lMessageId > 0) item.lMessageId.toString() else null,
                backgroundBottomColor = "#F97316",
                backgroundColor = "#FED7AA",
                endTime = endTimeMs,
                face = item.tMessageUser.sAvatar,
                message = content,
                price = price,
                startTime = startTimeMs,
                userName = item.tMessageUser.sNick.trim()
            )
        }
    }

    // ── Contribution rank ──────────────────────────────────────────────────

    override suspend fun getContributionRank(
        roomId: String,
        detail: LiveRoomDetail?
    ): List<LiveContributionRankItem> = emptyList()

    // ── Helper: Category resolution ────────────────────────────────────────

    private fun resolveHuyaCategoryId(liveInfo: JSONObject): String? {
        val gid = liveInfo.optString("iGid", "").toIntOrNull()
        if (gid != null && gid > 0) return gid.toString()
        val gameId = liveInfo.optString("iGameId", "").toIntOrNull()
        if (gameId != null && gameId > 0) return gameId.toString()
        return null
    }

    private fun resolveHuyaCategoryPic(categoryId: String?): String? {
        if (categoryId.isNullOrEmpty()) return null
        return "https://huyaimg.msstatic.com/cdnimage/game/$categoryId-MS.jpg"
    }

    private fun resolveHuyaParentCategoryId(liveInfo: JSONObject): String? {
        val bussType = liveInfo.optString("iBussType", "").toIntOrNull()
        if (bussType == null || bussType <= 0) return null
        return bussType.toString()
    }

    private fun resolveHuyaParentCategoryName(liveInfo: JSONObject): String? {
        val bussType = liveInfo.optString("iBussType", "").toIntOrNull() ?: return null
        return BUSS_TYPE_NAMES[bussType]
    }

    // ── Helper: Numeric utilities ──────────────────────────────────────────

    private fun asPositiveLong(value: Any?): Long {
        if (value is Int) return if (value > 0) value.toLong() else 0L
        if (value is Long) return if (value > 0L) value else 0L
        val parsed = value?.toString()?.trim()?.toLongOrNull()
        return if (parsed != null && parsed > 0L) parsed else 0L
    }

    private fun firstPositiveLongByKeys(source: Any?, keys: Set<String>, depth: Int = 0): Long {
        if (source == null || depth > 8) return 0L
        if (source is JSONObject) {
            val iter = source.keys()
            while (iter.hasNext()) {
                val key = iter.next()
                if (key.lowercase() in keys) {
                    val value = asPositiveLong(source.opt(key))
                    if (value > 0L) return value
                }
            }
            val iter2 = source.keys()
            while (iter2.hasNext()) {
                val result = firstPositiveLongByKeys(source.opt(iter2.next()), keys, depth + 1)
                if (result > 0L) return result
            }
        } else if (source is org.json.JSONArray) {
            for (i in 0 until source.length()) {
                val result = firstPositiveLongByKeys(source.opt(i), keys, depth + 1)
                if (result > 0L) return result
            }
        }
        return 0L
    }

    /**
     * Rotate-left 64-bit operation. Used in anti-code generation.
     * Operates on the low 32 bits only (matching the Dart implementation).
     */
    private fun rotl64(t: Long): Long {
        val low = t and 0xFFFFFFFFL
        val rotatedLow = ((low shl 8) or (low shr 24)) and 0xFFFFFFFFL
        val high = t and -0x100000000L
        return high or rotatedLow
    }

    /**
     * Generate a random UID string.
     */
    private fun getUid(t: Int? = null, e: Int? = null): String {
        val n = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toList()
        val o = CharArray(36)
        if (t != null) {
            for (i in 0 until t) {
                o[i] = n[Random.nextInt(e ?: n.size)]
            }
            return o.take(t).joinToString("")
        } else {
            o[8] = '-'; o[13] = '-'; o[18] = '-'; o[23] = '-'
            o[14] = '4'
            for (i in 0 until 36) {
                if (o[i] == ' ') {
                    val r = Random.nextInt(16)
                    o[i] = n[if (19 == i) (3 and r) or 8 else r]
                }
            }
            return o.joinToString("")
        }
    }

    /**
     * Generate a UUID-like value for anti-code.
     */
    private fun getUUid(): String {
        val currentTime = System.currentTimeMillis()
        val randomValue = Random.nextInt(0, Int.MAX_VALUE)
        val result = ((currentTime % 10_000_000_000L * 1000 + randomValue) % 4_294_967_295L).toInt()
        return result.toString()
    }

    // ── Helper: Query string parsing ───────────────────────────────────────

    /**
     * Parse a query string into a map of key -> list of values.
     * Matches Dart's Uri.queryParametersAll behavior.
     */
    private fun parseQueryString(query: String): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        if (query.isEmpty()) return result
        for (pair in query.split("&")) {
            val eqIdx = pair.indexOf('=')
            if (eqIdx < 0) {
                result.getOrPut(formDecode(pair)) { mutableListOf() }
            } else {
                val key = formDecode(pair.substring(0, eqIdx))
                val value = formDecode(pair.substring(eqIdx + 1))
                result.getOrPut(key) { mutableListOf() }.add(value)
            }
        }
        return result
    }

    // ── Helper: URL component codecs (mirror Dart Uri semantics) ───────────

    /**
     * Form-decode a query component, matching Dart's `Uri.queryParametersAll`
     * (resolves `%XX` and converts `+` to a space). This is the FIRST decode
     * layer the Dart reference applies to every anti-code field. Falls back to
     * the raw string if the input is not valid percent-encoding.
     */
    private fun formDecode(s: String): String =
        try { URLDecoder.decode(s, "UTF-8") } catch (e: Exception) { s }

    /**
     * Decode a percent-encoded component WITHOUT turning `+` into a space,
     * matching Dart's `Uri.decodeComponent`. Used as the SECOND decode layer
     * for `fm` (the base64 anti-code seed): a literal `+` is a base64 character
     * that must be preserved, so [URLDecoder] (which maps `+`→space) cannot be
     * used directly here.
     */
    private fun decodeComponent(s: String): String =
        try { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") } catch (e: Exception) { s }

    /**
     * Encode a component matching Dart's `Uri.encodeComponent` (space becomes
     * `%20`, not `+`).
     */
    private fun encodeComponent(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    // ── Helper: MD5 ────────────────────────────────────────────────────────

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    // ── Helper: Logging ────────────────────────────────────────────────────

    private fun logHeadlineEmpty(roomId: String, pidCandidates: Set<Long>, reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastHeadlineEmptyLogAt < 60_000) return
        lastHeadlineEmptyLogAt = now
        CoreLog.d("Huya headline $reason, roomId=$roomId, pidCandidates=${pidCandidates.joinToString(",")}")
    }

    // ── Site-specific data holder ──────────────────────────────────────────

    /**
     * Holds the URL data model retrieved during getRoomDetail, used by getPlayQualites/getPlayUrls.
     * Callers should store this after getRoomDetail and pass it when resolving play URLs.
     */
    data class HuyaRoomPlayData(
        val url: String,
        val uid: String,
        val lines: List<HuyaLineModel>,
        val bitRates: List<HuyaBitRateModel>
    )
}
