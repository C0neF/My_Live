package com.mylive.desktop

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import kotlin.coroutines.resumeWithException

internal suspend fun <T> OkHttpClient.desktopHttp(request: Request, consume: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(IOException("网络请求失败")) }
            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isActive) { response.close(); return }
                try { response.use { continuation.resume(consume(it)) { _, _, _ -> response.close() } } }
                catch (failure: Exception) { if (continuation.isActive) continuation.resumeWithException(failure) }
            }
        })
    }

internal fun Response.boundedText(limit: Int): String {
    val content = body ?: return ""
    require(content.contentLength() <= limit) { "响应内容过大" }
    val bytes = content.byteStream().readNBytes(limit + 1)
    require(bytes.size <= limit) { "响应内容过大" }
    return bytes.toString(Charsets.UTF_8)
}
