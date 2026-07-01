package com.mylive.app.di

import com.mylive.app.core.common.RuntimeNetworkLogInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.brotli.dec.BrotliInputStream
import okio.BufferedSource
import okio.buffer
import okio.source
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)

        // Brotli decompression interceptor
        builder.addInterceptor(Interceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.header("Content-Encoding") == "br") {
                val body = response.body ?: return@Interceptor response
                response.newBuilder()
                    .removeHeader("Content-Encoding")
                    .removeHeader("Content-Length")
                    .body(BrotliResponseBody(body))
                    .build()
            } else {
                response
            }
        })
        builder.addInterceptor(RuntimeNetworkLogInterceptor())

        return builder.build()
    }
}

private class BrotliResponseBody(
    private val compressedBody: ResponseBody
) : ResponseBody() {
    private val decompressedSource: BufferedSource by lazy {
        BrotliInputStream(compressedBody.byteStream()).source().buffer()
    }

    override fun contentType(): MediaType? = compressedBody.contentType()

    override fun contentLength() = -1L

    override fun source(): BufferedSource = decompressedSource
}
