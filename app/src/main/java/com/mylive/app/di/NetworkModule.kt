package com.mylive.app.di

import com.mylive.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.ResponseBody.Companion.toResponseBody
import org.brotli.dec.BrotliInputStream
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
                val decompressed = BrotliInputStream(body.byteStream()).readBytes()
                response.newBuilder()
                    .removeHeader("Content-Encoding")
                    .body(decompressed.toResponseBody(body.contentType()))
                    .build()
            } else {
                response
            }
        })

        // Only enable HTTP logging in debug builds
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
        }

        return builder.build()
    }
}
