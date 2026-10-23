package com.mylive.app.core.site.douyu

import com.mylive.app.core.common.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

internal fun douyuResponseData(response: JSONObject): JSONObject {
    check(response.optInt("error", response.optInt("code", 0)) == 0) {
        "斗鱼取流失败，请检查登录状态或稍后重试"
    }
    return checkNotNull(response.optJSONObject("data")) { "斗鱼播放接口未返回数据" }
}

/** Refreshes descriptors, not signed requests: every request gets its own current timestamp. */
@Singleton
class DouyuPlaybackSigner internal constructor(
    private val httpClient: HttpClient, private val nowMillis: () -> Long
) {
    @Inject constructor(httpClient: HttpClient) : this(httpClient, System::currentTimeMillis)
    private class Descriptor(
        val key: String,
        val random: String,
        val rounds: Int,
        val encryptedData: String,
        val isSpecial: Boolean,
        val cookie: String,
        val deviceId: String,
        val createdAtMillis: Long
    ) {
        fun fresh(now: Long, session: DouyuRequestSession): Boolean =
            now >= createdAtMillis && now - createdAtMillis < 300_000 &&
                cookie == session.cookie && deviceId == session.deviceId
    }
    private val mutex = Mutex()
    private var cached: Descriptor? = null

    internal suspend fun sign(
        roomId: String, rate: Int, cdn: String, session: DouyuRequestSession
    ): Map<String, String> {
        val descriptor = mutex.withLock {
            val now = nowMillis()
            cached?.takeIf { it.fresh(now, session) } ?: run {
                val response = httpClient.getJson(
                    "https://www.douyu.com/wgapi/livenc/liveweb/websec/getEncryption",
                    queryParameters = mapOf("did" to session.deviceId),
                    header = session.headers(roomId)
                ) as JSONObject
                readDescriptor(douyuResponseData(response), session, now)
                    .also { cached = it }
            }
        }
        val key = descriptor.key
        var secret = descriptor.random
        repeat(descriptor.rounds) { secret = md5(secret + key) }
        val timestamp = (nowMillis() / 1000).toString()
        val salt = if (descriptor.isSpecial) "" else roomId + timestamp
        return mapOf(
            "enc_data" to descriptor.encryptedData,
            "tt" to timestamp,
            "did" to session.deviceId,
            "auth" to md5(secret + key + salt),
            "cdn" to cdn,
            "ver" to "Douyu_new",
            "rate" to rate.toString(),
            "hevc" to "0", "fa" to "0", "ive" to "0"
        )
    }

    private fun readDescriptor(data: JSONObject, session: DouyuRequestSession, createdAtMillis: Long): Descriptor {
        val key = data.getString("key")
        val rounds = data.optInt("enc_time", 1)
        check(key.isNotEmpty() && rounds in 1..100) { "斗鱼签名参数无效" }
        // Parse every required field before publishing the descriptor to the shared cache.
        return Descriptor(
            key = key,
            random = data.getString("rand_str"),
            rounds = rounds,
            encryptedData = data.getString("enc_data"),
            isSpecial = data.optInt("is_special", 0) == 1,
            cookie = session.cookie,
            deviceId = session.deviceId,
            createdAtMillis = createdAtMillis
        )
    }

    private fun md5(text: String): String = MessageDigest.getInstance("MD5")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
