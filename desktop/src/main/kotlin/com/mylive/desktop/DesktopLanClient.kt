package com.mylive.desktop

import com.mylive.app.core.common.*
import com.mylive.app.core.sync.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class DesktopLanClient(client: OkHttpClient) {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    suspend fun send(address: String, port: Int, token: String, profile: String) {
        require(token.length in 16..256) { "请填写对方的完整配对码" }
        require(profile.toByteArray().size <= MAX_IMPORTED_JSON_BYTES)
        val base = HttpUrl.Builder().scheme("http").host(address.trim()).port(port).build()
        suspend fun get(url: HttpUrl): String {
            val proof = LanSyncAuth.createProof(token, "GET", url.encodedPath, false, "")
            return http.desktopHttp(Request.Builder().url(url).header(LanSyncAuth.HEADER_NAME, proof.headerValue).build()) {
                val text = it.boundedText(64 * 1024)
                if (it.code == 401) throw LanSyncProtocol.unauthorizedException(text)
                check(it.isSuccessful) { "同步请求失败（HTTP ${it.code}）" }; text
            }
        }
        val info = LanSyncProtocol.decodePeerInfo(get(base.resolve(LanSyncProtocol.INFO_PATH)!!))
        check(info.paired) { LanSyncProtocol.PAIRING_REQUIRED_MESSAGE }
        val path = LanSyncImportType.PROFILE.path
        val timestamp = System.currentTimeMillis(); val nonce = LanSyncAuth.newNonce()
        val context = LanSyncAuth.requestContext("POST", path, false, timestamp, nonce)
        val sealed = LanSyncCrypto.encrypt(token, profile, context)
        val proof = LanSyncAuth.createProof(token, "POST", path, false, sealed, timestamp, nonce)
        val accepted = http.desktopHttp(Request.Builder().url(base.resolve(path)!!)
            .post(sealed.toRequestBody("application/json".toMediaType())).header(LanSyncAuth.HEADER_NAME, proof.headerValue).build()) {
            LanSyncProtocol.decodeImportResponse(it.code, it.isSuccessful, it.boundedText(64 * 1024))
        } ?: return
        val job = base.resolve(accepted.jobUrl) ?: error("同步任务地址无效")
        check(job.scheme == base.scheme && job.host == base.host && job.port == base.port && job.encodedPath.startsWith(LanSyncProtocol.JOB_PATH_PREFIX)) { "同步任务地址无效" }
        withTimeout(30_000) {
            while (true) {
                val result = LanSyncProtocol.decodeJobStatus(get(job))
                if (result.state == LanSyncJobState.SUCCEEDED) return@withTimeout
                check(result.state != LanSyncJobState.FAILED) { result.message }
                delay(300)
            }
        }
    }
}
