package com.mylive.app.core.common

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class RuntimeNetworkLogInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val safeTarget = safeUrlForLog(request.url.toString())
        val startedAt = System.nanoTime()

        return try {
            val response = chain.proceed(request)
            CoreLog.d(
                "HTTP ${request.method} $safeTarget -> ${response.code} " +
                    "(${elapsedMillis(startedAt)} ms)"
            )
            response
        } catch (error: IOException) {
            CoreLog.d(
                "HTTP ${request.method} $safeTarget failed " +
                    "${error.javaClass.simpleName} (${elapsedMillis(startedAt)} ms)"
            )
            throw error
        }
    }

    private fun elapsedMillis(startedAt: Long): Long {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
    }
}
