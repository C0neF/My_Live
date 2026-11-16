package com.mylive.desktop

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import org.conscrypt.Conscrypt
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Douyu's chat endpoint negotiates TLS 1.2 with an RSA cipher rejected by current SunJSSE defaults.
 * Use the Android-compatible TLS provider for this connection only, with normal certificate checks.
 */
internal fun douyuDesktopWebSocketClient(
    base: OkHttpClient,
    trustManager: X509TrustManager = defaultTrustManager()
): OkHttpClient {
    val context = SSLContext.getInstance("TLS", Conscrypt.newProvider())
    context.init(null, arrayOf<TrustManager>(trustManager), null)
    return base.newBuilder()
        .sslSocketFactory(context.socketFactory, trustManager)
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
        .build()
}

private fun defaultTrustManager(): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().single()
}
