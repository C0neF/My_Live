package com.mylive.app.core.site.douyu

import com.mylive.app.core.common.CoreLog
import com.mylive.app.core.common.CoreError
import com.mylive.app.core.common.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
import com.mylive.app.core.common.WebSocketUtils
import com.mylive.app.core.site.firstSiteImageUrl
import com.mylive.app.core.site.FollowPresence
import com.mylive.app.core.site.normalizeSiteImageUrl
import com.mylive.app.core.site.LiveDanmakuSession
import com.mylive.app.core.site.LiveSite
import com.mylive.app.core.site.optNullableStringValue
import com.mylive.app.core.site.optStringValue
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.security.SecureRandom
import javax.inject.Inject
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

private const val DOUYU_SEARCH_PAGE_SIZE = 20
private const val DOUYU_PLAY_URL_MAX_CONCURRENCY = 5

internal fun resolveDouyuRoomFaceUrl(item: JSONObject): String {
    return firstJsonImageUrlByKeys(item, DOUYU_AVATAR_KEYS).orEmpty()
}

internal fun resolveDouyuRoomCoverUrl(item: JSONObject): String {
    return firstJsonImageUrlByKeys(item, DOUYU_COVER_KEYS).orEmpty()
}

/**
 * Resolves one playable URL per CDN, issuing the requests together instead of one after another.
 *
 * Douyu has no endpoint that hands back every CDN at once — each costs its own POST. They do not
 * depend on each other, but they used to run in a sequential loop, so a room with five CDNs put
 * five round trips in front of the first video frame rather than one.
 *
 * The burst is explicitly capped at five requests here, independently of OkHttp's dispatcher.
 *
 * Two behaviours are preserved on purpose:
 * - **Order.** The result doubles as the CDN preference order — [getPlayQualites] sorts `scdn`
 *   last — and the player treats earlier entries as better lines.
 * - **Partial failure.** A CDN that throws is skipped rather than failing the room, so one dead
 *   line cannot cost the others.
 */
internal suspend fun <T : Any> resolveDouyuCdnResults(
    cdns: List<String>,
    fetchResult: suspend (cdn: String) -> T?
): List<T> = coroutineScope {
    val permits = Semaphore(DOUYU_PLAY_URL_MAX_CONCURRENCY)
    val results = cdns
        .map { cdn ->
            async {
                permits.withPermit {
                    try {
                        Result.success(fetchResult(cdn))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        CoreLog.e("Failed to get play URL for CDN $cdn", e)
                        Result.failure<T?>(e)
                    }
                }
            }
        }
        .awaitAll()
    val sources = results.mapNotNull { it.getOrNull() }
    // Renew once for the entire failed batch, never independently for each parallel CDN.
    if (sources.isEmpty()) {
        results.firstNotNullOfOrNull { it.exceptionOrNull()?.takeIf { error -> error.isDouyuAuthRejection() } }
            ?.let { throw it }
    }
    sources
}

private fun Throwable.isDouyuAuthRejection(): Boolean = this is CoreError && statusCode in listOf(401, 403)

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
    private val okHttpClient: OkHttpClient,
    private val accountSession: DouyuAccountSession,
    private val playbackSigner: DouyuPlaybackSigner
) : LiveSite {

    override val id: String = "douyu"
    override val name: String = "斗鱼直播"

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36 Edg/114.0.1823.43"
        private const val SEARCH_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36 Edg/114.0.1823.51"
    }

    override fun createDanmakuSession(
        requestedRoomId: String,
        detail: LiveRoomDetail
    ): LiveDanmakuSession {
        return LiveDanmakuSession(
            danmaku = DouyuDanmaku(WebSocketUtils(okHttpClient)),
            args = DanmakuArgs.Douyu(detail.roomId.ifBlank { requestedRoomId })
        )
    }

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

        val showTime = try {
            getRoomShowTime(roomId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

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
            status = isRoomLive(roomInfo),
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

    private suspend fun getRoomShowTime(roomId: String): String? {
        val h5RoomInfo = httpClient.getJson(
            "https://www.douyu.com/swf_api/h5room/$roomId",
            queryParameters = emptyMap(),
            header = mapOf(
                "referer" to "https://www.douyu.com/$roomId",
                "user-agent" to DEFAULT_USER_AGENT
            )
        ) as JSONObject
        return h5RoomInfo.optJSONObject("data")?.optNullableStringValue("show_time")
    }

    // ── Play qualities ──────────────────────────────────────────────────

    override suspend fun getPlayQualites(detail: LiveRoomDetail): List<LivePlayQuality> {
        val data = withPlaybackSession { session -> requestPlayData(detail.roomId, session, -1, "") }

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

    private suspend fun requestPlayData(
        roomId: String, session: DouyuRequestSession, rate: Int, cdn: String
    ): JSONObject {
        val params = playbackSigner.sign(roomId, rate, cdn, session)

        val result = httpClient.postJson(
            "https://www.douyu.com/lapi/live/getH5PlayV1/$roomId",
            data = params,
            header = session.headers(roomId),
            formUrlEncoded = true
        ) as JSONObject

        return douyuResponseData(result)
    }

    // ── Play URLs ───────────────────────────────────────────────────────

    override suspend fun getPlayUrls(
        detail: LiveRoomDetail,
        quality: LivePlayQuality
    ): LivePlayUrl = withPlaybackSession { session ->
        val playData = quality.data as PlayQualityData.Douyu

        val sources = resolveDouyuCdnResults(playData.cdns) { cdn ->
            getPlaybackSource(detail.roomId, session, playData.rate, cdn)
        }
        val result = selectDouyuPlaybackSources(quality, sources)
        val actualRate = (result.actualQuality?.data as? PlayQualityData.Douyu)?.rate
        CoreLog.i("斗鱼取流画质: 请求 rate=${playData.rate}, 实际 rate=$actualRate, 匹配线路=${result.urls.size}/${sources.size}")
        result.copy(headers = session.mediaHeaders(detail.roomId))
    }

    /**
     * Keep the returned rate: a successful response may silently downgrade the requested quality.
     */
    private suspend fun getPlaybackSource(
        roomId: String,
        session: DouyuRequestSession,
        rate: Int,
        cdn: String
    ): DouyuPlaybackSource {
        val data = requestPlayData(roomId, session, rate, cdn)
        val rtmpUrl = data.optString("rtmp_url", "")
        val rtmpLive = htmlUnescape(data.optString("rtmp_live", ""))
        check(rtmpUrl.isNotBlank() && rtmpLive.isNotBlank()) { "斗鱼未返回播放地址" }
        val url = "$rtmpUrl/$rtmpLive"
        check(url.toHttpUrlOrNull() != null) { "斗鱼播放地址无效" }
        val actualRate = data.optNullableStringValue("rate")?.toIntOrNull()?.takeIf { it >= 0 }
        val multirates = data.optJSONArray("multirates")
        val qualityIndex = (0 until (multirates?.length() ?: 0)).firstOrNull { index ->
            actualRate != null && multirates?.optJSONObject(index)?.optInt("rate", -1) == actualRate
        }
        return DouyuPlaybackSource(
            url = url,
            rate = actualRate,
            qualityName = qualityIndex?.let { multirates?.optJSONObject(it)?.optNullableStringValue("name") },
            qualityIndex = qualityIndex ?: Int.MAX_VALUE
        )
    }

    private suspend fun <T> withPlaybackSession(request: suspend (DouyuRequestSession) -> T): T {
        val session = accountSession.forRequest()
        val rejection = try {
            return request(session)
        } catch (error: CoreError) {
            if (!error.isDouyuAuthRejection() || !session.authenticated) throw error
            error
        }
        CoreLog.w("斗鱼取流鉴权被拒绝（${rejection.statusCode}），尝试更新登录态")
        val renewed = accountSession.renewAfterRejection(session)
            ?: throw playbackRejection(rejection)
        CoreLog.i("斗鱼取流已更新登录态，重试一次")
        try {
            return request(renewed)
        } catch (error: CoreError) {
            if (error.isDouyuAuthRejection()) throw playbackRejection(error)
            throw error
        }
    }

    private fun playbackRejection(error: CoreError): IllegalStateException = IllegalStateException(
        "斗鱼取流请求被拒绝（${error.statusCode}），请续期或重新登录", error
    )

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
        return isRoomLive(roomInfo)
    }

    override suspend fun getFollowPresence(roomId: String): FollowPresence {
        val roomInfo = getRoomInfo(roomId)
        val isLive = isRoomLive(roomInfo)
        val showTime = if (isLive) {
            try {
                getRoomShowTime(roomId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        return FollowPresence(isLive = isLive, showTime = showTime)
    }

    private fun isRoomLive(roomInfo: JSONObject): Boolean {
        val status = roomInfo.opt("show_status")?.toString()?.toIntOrNull()
            ?: throw CoreError("斗鱼直播状态响应异常：无法确认开播状态")
        return status == 1 && roomInfo.optInt("videoLoop") != 1
    }

    // ── Super chat ──────────────────────────────────────────────────────

    override suspend fun getSuperChatMessage(
        roomId: String,
        detail: LiveRoomDetail?
    ): List<LiveSuperChatMessage> {
        // Not supported for Douyu
        return emptyList()
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
                    // Keep malformed/missing status visible to the same validation as betard.
                    roomObj.put("show_status", data.opt("room_status"))
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
