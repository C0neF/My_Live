package com.mylive.app.data.sync

import com.mylive.app.core.common.LanSyncAuth
import com.mylive.app.core.common.LanSyncCrypto
import com.mylive.app.core.sync.LanSyncImportType
import com.mylive.app.core.sync.LanSyncJobState
import com.mylive.app.core.sync.LanSyncPeerInfo
import com.mylive.app.core.sync.LanSyncProtocol
import com.mylive.app.core.sync.LanSyncProtocolException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

private const val LAN_SYNC_JOB_TIMEOUT_MS = 30_000L
private const val LAN_SYNC_JOB_POLL_INTERVAL_MS = 300L

@Singleton
class LanSyncClient @Inject constructor(
    private val httpClient: OkHttpClient
) {
    /**
     * Authenticates the peer and negotiates the wire protocol before any import
     * can be sent.
     */
    suspend fun connect(address: String, port: Int, token: String): LanSyncPeer {
        if (token.isBlank()) {
            throw LanSyncProtocolException("未配对，请先扫描对方二维码或填写配对码")
        }
        return withContext(Dispatchers.IO) {
            val baseUrl = HttpUrl.Builder()
                .scheme("http")
                .host(address)
                .port(port)
                .build()
            val info = requestPeerInfo(baseUrl, token)
            if (!info.paired) {
                throw LanSyncProtocolException(LanSyncProtocol.PAIRING_REQUIRED_MESSAGE)
            }
            LanSyncPeer(
                baseUrl = baseUrl,
                token = token,
                peerInfo = info,
                httpClient = httpClient
            )
        }
    }

    private fun requestPeerInfo(baseUrl: HttpUrl, token: String): LanSyncPeerInfo {
        val proof = LanSyncAuth.createProof(
            token = token,
            method = "GET",
            path = LanSyncProtocol.INFO_PATH,
            overlay = false,
            body = ""
        )
        val request = Request.Builder()
            .url(baseUrl.resolve(LanSyncProtocol.INFO_PATH)!!)
            .get()
            .addHeader(LanSyncAuth.HEADER_NAME, proof.headerValue)
            .build()
        return httpClient.newCall(request).execute().use { response ->
            // Read the body once, up front: it carries the peer's own rejection reason, which is
            // the only way to tell a wrong pairing code apart from a clock skew.
            val body = response.body?.string().orEmpty()
            if (response.code == 401) {
                throw LanSyncProtocol.unauthorizedException(body)
            }
            if (!response.isSuccessful) {
                throw LanSyncProtocolException("HTTP ${response.code}")
            }
            LanSyncProtocol.decodePeerInfo(body)
        }
    }
}

class LanSyncPeer internal constructor(
    private val baseUrl: HttpUrl,
    private val token: String,
    val peerInfo: LanSyncPeerInfo,
    private val httpClient: OkHttpClient
) {
    suspend fun import(
        type: LanSyncImportType,
        payload: String,
        overlay: Boolean
    ) = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val nonce = LanSyncAuth.newNonce()
        val requestContext = LanSyncAuth.requestContext(
            method = "POST",
            path = type.path,
            overlay = overlay,
            timestampMillis = timestamp,
            nonce = nonce
        )
        val sealedPayload = LanSyncCrypto.encrypt(token, payload, requestContext)
        val proof = LanSyncAuth.createProof(
            token = token,
            method = "POST",
            path = type.path,
            overlay = overlay,
            body = sealedPayload,
            timestampMillis = timestamp,
            nonce = nonce
        )
        val requestUrl = baseUrl.newBuilder()
            .encodedPath(type.path)
            .apply {
                if (overlay) addQueryParameter("overlay", "1")
            }
            .build()
        val request = Request.Builder()
            .url(requestUrl)
            .post(sealedPayload.toRequestBody("application/json".toMediaType()))
            .addHeader(LanSyncAuth.HEADER_NAME, proof.headerValue)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val acceptedJob = LanSyncProtocol.decodeImportResponse(
                statusCode = response.code,
                isSuccessful = response.isSuccessful,
                body = response.body?.string()
            )
            if (acceptedJob != null) {
                val jobUrl = response.request.url.resolve(acceptedJob.jobUrl)
                    ?: throw LanSyncProtocolException("invalid sync job url")
                pollJob(jobUrl)
            }
        }
    }

    private suspend fun pollJob(jobUrl: HttpUrl) {
        val deadline = System.currentTimeMillis() + LAN_SYNC_JOB_TIMEOUT_MS
        while (true) {
            val proof = LanSyncAuth.createProof(
                token = token,
                method = "GET",
                path = jobUrl.encodedPath,
                overlay = false,
                body = ""
            )
            val request = Request.Builder()
                .url(jobUrl)
                .get()
                .addHeader(LanSyncAuth.HEADER_NAME, proof.headerValue)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 401) {
                    throw LanSyncProtocol.unauthorizedException(body)
                }
                if (!response.isSuccessful) {
                    throw LanSyncProtocolException("HTTP ${response.code}")
                }
                val status = LanSyncProtocol.decodeJobStatus(body)
                when (status.state) {
                    LanSyncJobState.SUCCEEDED -> return
                    LanSyncJobState.QUEUED,
                    LanSyncJobState.RUNNING -> Unit
                    LanSyncJobState.FAILED -> throw LanSyncProtocolException(status.message)
                }
            }

            if (System.currentTimeMillis() >= deadline) {
                throw LanSyncProtocolException("等待对方导入完成超时")
            }
            delay(LAN_SYNC_JOB_POLL_INTERVAL_MS)
        }
    }
}
