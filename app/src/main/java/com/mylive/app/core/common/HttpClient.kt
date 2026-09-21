package com.mylive.app.core.common

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.FormBody
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException

/**
 * HTTP client wrapping OkHttp, providing suspend functions for GET/POST/HEAD requests.
 * Designed for Hilt injection -- receives an [OkHttpClient] via constructor.
 *
 * Network calls and body parsing run on OkHttp's dispatcher. Cancelling a caller cancels its call.
 */
@Singleton
class HttpClient @Inject constructor(
    private val client: OkHttpClient
) {

    /**
     * GET request returning the response body as a plain [String].
     *
     * @param url Request URL
     * @param queryParameters Optional query parameters
     * @param header Optional request headers
     * @return Response body as String
     * @throws CoreError on HTTP errors or network failures
     */
    suspend fun getText(
        url: String,
        queryParameters: Map<String, String>? = null,
        header: Map<String, String>? = null
    ): String {
        val request = buildGetRequest(url, queryParameters, header)
        return executeStringRequest(request)
    }

    /**
     * GET request returning the response body parsed as JSON.
     *
     * @param url Request URL
     * @param queryParameters Optional query parameters
     * @param header Optional request headers
     * @return Parsed JSON as [Any] (JSONObject or JSONArray)
     * @throws CoreError on HTTP errors or network failures
     */
    suspend fun getJson(
        url: String,
        queryParameters: Map<String, String>? = null,
        header: Map<String, String>? = null
    ): Any {
        val request = buildGetRequest(url, queryParameters, header)
        return executeJsonRequest(request)
    }

    /**
     * POST request returning the response body parsed as JSON.
     *
     * @param url Request URL
     * @param queryParameters Optional query parameters
     * @param data Optional POST body (Map for form-encoded, String/ByteArray for raw body)
     * @param header Optional request headers
     * @param formUrlEncoded Whether to use form URL-encoded content type
     * @return Parsed JSON as [Any] (JSONObject or JSONArray)
     * @throws CoreError on HTTP errors or network failures
     */
    suspend fun postJson(
        url: String,
        queryParameters: Map<String, String>? = null,
        data: Any? = null,
        header: Map<String, String>? = null,
        formUrlEncoded: Boolean = false
    ): Any {
        val request = buildPostRequest(url, queryParameters, data, header, formUrlEncoded)
        return executeJsonRequest(request)
    }

    /**
     * HEAD request returning the [okhttp3.Response] (caller must close the body).
     *
     * On non-success status codes, the response is still returned (does not throw).
     *
     * @param url Request URL
     * @param queryParameters Optional query parameters
     * @param header Optional request headers
     * @return The OkHttp response
     * @throws CoreError on network failures (not on HTTP error status codes)
     */
    suspend fun head(
        url: String,
        queryParameters: Map<String, String>? = null,
        header: Map<String, String>? = null
    ): okhttp3.Response {
        val urlBuilder = url.toHttpUrl().newBuilder()
        queryParameters?.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .headers((header ?: emptyMap()).toHeaders())
            .head()
            .build()
        return executeRequest(request, "发送HEAD请求失败") { it }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private fun buildGetRequest(
        url: String,
        queryParameters: Map<String, String>?,
        header: Map<String, String>?
    ): Request {
        val urlBuilder = url.toHttpUrl().newBuilder()
        queryParameters?.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }
        return Request.Builder()
            .url(urlBuilder.build())
            .headers((header ?: emptyMap()).toHeaders())
            .get()
            .build()
    }

    private fun buildPostRequest(
        url: String,
        queryParameters: Map<String, String>?,
        data: Any?,
        header: Map<String, String>?,
        formUrlEncoded: Boolean
    ): Request {
        val urlBuilder = url.toHttpUrl().newBuilder()
        queryParameters?.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }

        val requestBody = when {
            formUrlEncoded && data is Map<*, *> -> {
                val formBuilder = FormBody.Builder()
                @Suppress("UNCHECKED_CAST")
                (data as? Map<String, String>)?.forEach { (key, value) ->
                    formBuilder.add(key, value)
                }
                formBuilder.build()
            }
            data is String -> {
                data.toRequestBody(null)
            }
            data is ByteArray -> {
                data.toRequestBody(null)
            }
            data is Map<*, *> -> {
                val json = try {
                    JSONObject().also { obj ->
                        @Suppress("UNCHECKED_CAST")
                        (data as? Map<String, Any?>)?.forEach { (key, value) ->
                            obj.put(key, value)
                        }
                    }
                } catch (e: org.json.JSONException) {
                    throw CoreError("Invalid JSON body: ${e.message}")
                }
                json.toString().toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )
            }
            else -> {
                "{}".toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )
            }
        }

        return Request.Builder()
            .url(urlBuilder.build())
            .headers((header ?: emptyMap()).toHeaders())
            .post(requestBody)
            .build()
    }

    private suspend fun executeStringRequest(request: Request): String =
        executeRequest(request) { response ->
            response.use {
                if (!response.isSuccessful) {
                    throw CoreError(
                        message = response.message,
                        statusCode = response.code
                    )
                }
                response.body?.string() ?: ""
            }
        }

    private suspend fun executeJsonRequest(request: Request): Any =
        executeRequest(request) { response ->
            response.use {
                if (!response.isSuccessful) {
                    throw CoreError(
                        message = response.message,
                        statusCode = response.code
                    )
                }
                val body = response.body?.string() ?: "{}"
                if (body.isBlank()) {
                    throw CoreError("接口返回为空，请稍后再试")
                }
                try {
                    org.json.JSONTokener(body).nextValue()
                } catch (e: org.json.JSONException) {
                    throw CoreError("Invalid JSON response: ${e.message}")
                }
            }
        }

    private suspend fun <T> executeRequest(
        request: Request,
        ioErrorMessage: String = "发送请求失败",
        consume: (Response) -> T
    ): T = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(CoreError(ioErrorMessage))
            }

            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isActive) {
                    response.close()
                    return
                }
                try {
                    val value = consume(response)
                    // HEAD transfers response ownership to the caller. Close it if cancellation
                    // wins before the caller resumes; text/JSON consumers already close with use.
                    continuation.resume(value) { _, _, _ -> response.close() }
                } catch (error: Throwable) {
                    response.close()
                    continuation.resumeWithException(
                        if (error is IOException) CoreError(ioErrorMessage) else error
                    )
                }
            }
        })
    }
}
