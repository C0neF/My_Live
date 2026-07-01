package com.mylive.app.core.site.huya.tars

import com.mylive.app.core.common.CoreLog
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * TARS HTTP client.
 *
 * Sends TUP3-encoded binary requests to a TARS server endpoint via HTTP POST.
 * Handles encoding the request packet and decoding the response.
 *
 * Ported from Dart tars_dart BaseTarsHttp.
 *
 * @param baseUrl Base URL of the TARS server (e.g. "http://wup.huya.com")
 * @param servantName The TARS service name (e.g. "liveui")
 * @param headers Additional HTTP headers to include
 * @param okHttpClient OkHttp client to use for requests
 */
class BaseTarsHttp(
    private val baseUrl: String,
    private val servantName: String,
    private val path: String = "",
    private val headers: Map<String, String> = emptyMap(),
    private val okHttpClient: OkHttpClient
) {

    private val requestClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        const val PACKET_TYPE_TARSNORMAL = 0
        const val PACKET_TYPE_TUP3 = 3
        private val CONTENT_TYPE = "application/x-wup".toMediaType()
    }

    /**
     * Send a TARS request and return the decoded response.
     *
     * @param methodName The TARS function name
     * @param tReq The request TarsStruct
     * @param tRsp A prototype response TarsStruct for deserialization
     * @return The decoded response
     */
    fun <REQ : TarsStruct, RSP : TarsStruct> tupRequest(
        methodName: String,
        tReq: REQ,
        tRsp: RSP
    ): RSP {
        val (code, response) = tupRequestWithRspCode(methodName, tReq, tRsp)
        if (code == 0) {
            return response!!
        } else {
            CoreLog.e("tupDecode decode error:$code")
            throw TupResultException(code)
        }
    }

    /**
     * Send a TARS request and return both the result code and decoded response.
     */
    fun <REQ : TarsStruct, RSP : TarsStruct> tupRequestWithRspCode(
        methodName: String,
        tReq: REQ,
        tRsp: RSP
    ): TupResponse<RSP> {
        val data = buildRequest(methodName, tReq)
        val allHeaders = mutableMapOf(
            "Content-Type" to "application/x-wup",
            "Content-Length" to data.size.toString()
        )
        allHeaders.putAll(headers)

        CoreLog.d("send tupRequest, methodName:$methodName")

        val request = Request.Builder()
            .url("$baseUrl$path".toHttpUrl())
            .headers(allHeaders.toHeaders())
            .post(data.toRequestBody(CONTENT_TYPE))
            .build()

        val responseBody = requestClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                response.body?.bytes() ?: throw IOException("Empty response body")
            }

        return tupResponseDecode(methodName, responseBody, tRsp)
    }

    /**
     * Build a TUP3-encoded request packet.
     */
    private fun <REQ : TarsStruct> buildRequest(methodName: String, tReq: REQ): ByteArray {
        val encodePack = TarsUniPacket()
        encodePack.requestId = 0
        encodePack.setTarsVersion(PACKET_TYPE_TUP3)
        encodePack.setTarsPacketType(PACKET_TYPE_TARSNORMAL)
        encodePack.servantName = servantName
        encodePack.funcName = methodName
        encodePack.put("tReq", tReq)
        return encodePack.encode()
    }

    /**
     * Decode a TUP3 response packet.
     */
    private fun <RSP : TarsStruct> tupResponseDecode(
        methodName: String,
        list: ByteArray,
        tRsp: RSP
    ): TupResponse<RSP> {
        val respPack = TarsUniPacket()
        respPack.decode(list)
        val code: Int = respPack.get("", 0)
        CoreLog.d("get tupRequest response, methodName:$methodName, code:$code")
        val rsp: RSP = respPack.get("tRsp", tRsp)
        return TupResponse(code = code, response = rsp)
    }
}

/**
 * Extended UniPacket that sets sensible defaults for TARS RPC.
 */
class TarsUniPacket : UniPacket() {

    init {
        packageData.iVersion = BaseTarsHttp.PACKET_TYPE_TUP3
        packageData.cPacketType = BaseTarsHttp.PACKET_TYPE_TARSNORMAL
        packageData.iMessageType = 0
        packageData.iTimeout = 0
        packageData.sBuffer = byteArrayOf(0)
        packageData.context = mutableMapOf()
        packageData.status = mutableMapOf()
    }

    fun setTarsVersion(version: Int) {
        setPacketVersion(version)
    }

    fun setTarsPacketType(packetType: Int) {
        packageData.cPacketType = packetType
    }

    fun setTarsMessageType(messageType: Int) {
        packageData.iMessageType = messageType
    }

    fun setTarsTimeout(timeout: Int) {
        packageData.iTimeout = timeout
    }

    fun setTarsBuffer(buffer: ByteArray) {
        packageData.sBuffer = buffer
    }

    fun setTarsContext(context: Map<String, String>) {
        packageData.context = context.toMutableMap()
    }

    fun setTarsStatus(status: Map<String, String>) {
        packageData.status = status.toMutableMap()
    }

    fun getTarsVersion(): Int = packageData.iVersion
    fun getTarsPacketType(): Int = packageData.cPacketType
    fun getTarsMessageType(): Int = packageData.iMessageType
    fun getTarsTimeout(): Int = packageData.iTimeout
    fun getTarsBuffer(): ByteArray? = packageData.sBuffer
    fun getTarsContext(): Map<String, String>? = packageData.context
    fun getTarsStatus(): Map<String, String>? = packageData.status

    fun getTarsResultCode(): Int {
        return try {
            val rcode = packageData.status?.get("STATUS_RESULT_CODE")
            rcode?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun getTarsResultDesc(): String {
        return packageData.status?.get("STATUS_RESULT_DESC") ?: ""
    }
}

/**
 * Response wrapper with result code.
 */
data class TupResponse<T>(
    val code: Int = 0,
    val response: T? = null
)

/**
 * Exception thrown when the TARS result code is non-zero.
 */
class TupResultException(val code: Int, message: String? = null) :
    Exception("{code: $code, message: $message}")
