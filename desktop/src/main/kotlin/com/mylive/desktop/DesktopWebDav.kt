package com.mylive.desktop

import com.mylive.app.core.common.MAX_IMPORTED_JSON_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path

// Deliberately not a data class: default diagnostics must never print the password.
class DesktopWebDavConfig(val server: String = "", val user: String = "", val password: String = "") {
    override fun toString() = "DesktopWebDavConfig([redacted])"
}

internal fun desktopWebDavUrl(server: String): HttpUrl {
    val url = server.trim().toHttpUrl()
    require(url.isHttps || url.host in setOf("127.0.0.1", "localhost", "::1")) { "WebDAV 地址需要使用 HTTPS" }
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "请单独填写账号和密码，地址不要包含查询参数" }
    val directory = url.encodedPath.trimEnd('/')
    return url.newBuilder().apply {
        if (url.host == "dav.jianguoyun.com" && directory in setOf("", "/dav")) encodedPath("/dav/MyLive/").addPathSegment("mylive_profile_backup.json")
        else if (!url.encodedPath.endsWith(".json", true)) encodedPath(directory.ifEmpty { "/" }).addPathSegment("mylive_profile_backup.json")
    }.build()
}

class DesktopWebDav(
    client: OkHttpClient,
    private val file: Path = defaultDesktopLibraryPath().toAbsolutePath().parent.resolve("credentials/webdav.bin"),
    private val protector: DesktopCredentialProtector = WindowsCredentialProtector()
) {
    // Never redirect an authenticated PUT/GET to another endpoint or silently downgrade TLS.
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val mutex = Mutex()
    suspend fun load(): DesktopWebDavConfig = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!Files.exists(file)) return@withLock DesktopWebDavConfig()
            require(Files.size(file) <= 64 * 1024) { "同步配置读取失败" }
            val bytes = protector.unprotect(Files.readAllBytes(file))
            try {
                val obj = JSONObject(bytes.toString(Charsets.UTF_8))
                DesktopWebDavConfig(obj.getString("server"), obj.getString("user"), obj.getString("password"))
            } finally { bytes.fill(0) }
        }
    }
    suspend fun save(config: DesktopWebDavConfig) = withContext(Dispatchers.IO) {
        desktopWebDavUrl(config.server)
        require(config.user.length <= 1000 && config.password.length <= 10000 && ':' !in config.user)
        mutex.withLock { withDesktopFileLock(file) {
            val bytes = JSONObject().put("server", config.server.trim()).put("user", config.user).put("password", config.password).toString().toByteArray()
            try { writeDesktopFileAtomically(file, protector.protect(bytes)) } finally { bytes.fill(0) }
        } }
    }
    suspend fun forget() = withContext(Dispatchers.IO) { mutex.withLock { withDesktopFileLock(file) { Files.deleteIfExists(file) } } }
    suspend fun backup(config: DesktopWebDavConfig, state: DesktopLibraryState) {
        val url = desktopWebDavUrl(config.server)
        val payload = withContext(Dispatchers.IO) { RoomProfileCodec.export(state) }
        suspend fun put() = http.desktopHttp(request(url, config).put(payload.toRequestBody("application/json; charset=utf-8".toMediaType())).build()) { it.code }
        val first = put()
        if (first in 200..299) return
        if (first !in setOf(403, 404, 409) || url.pathSegments.size <= 1) error("上传失败（HTTP $first）")
        val parent = url.newBuilder().removePathSegment(url.pathSegments.lastIndex).addPathSegment("").build()
        val created = http.desktopHttp(request(parent, config).method("MKCOL", ByteArray(0).toRequestBody(null)).build()) { it.code }
        check(created in 200..299 || created == 405) { "创建备份目录失败（HTTP $created）" }
        val retry = put()
        check(retry in 200..299) { "上传失败（HTTP $retry）" }
    }
    suspend fun preview(config: DesktopWebDavConfig): RoomProfileImport {
        val text = http.desktopHttp(request(desktopWebDavUrl(config.server), config).get().build()) { response ->
            check(response.isSuccessful) { "下载失败（HTTP ${response.code}）" }
            response.boundedText(MAX_IMPORTED_JSON_BYTES)
        }
        return withContext(Dispatchers.IO) { RoomProfileCodec.parse(text) }
    }
    private fun request(url: HttpUrl, config: DesktopWebDavConfig) = Request.Builder().url(url).apply {
        if (config.user.isNotBlank()) header("Authorization", Credentials.basic(config.user, config.password))
    }
}
