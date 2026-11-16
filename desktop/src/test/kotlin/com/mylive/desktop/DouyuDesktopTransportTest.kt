package com.mylive.desktop

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.conscrypt.Conscrypt
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class DouyuDesktopTransportTest {
    private val certificate = HeldCertificate.Builder().rsa2048().addSubjectAlternativeName("localhost").build()

    @Test fun supportsTheChatEndpointTlsCipherWithNormalTrustValidation() {
        server().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            server.start()
            val trusted = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
            val base = OkHttpClient()
            val client = douyuDesktopWebSocketClient(base, trusted.trustManager)
            try {
                client.newCall(Request.Builder().url(server.url("/").newBuilder().host("localhost").build()).build()).execute().use { response ->
                    assertEquals("ok", response.body!!.string())
                    assertEquals("TLSv1.2", response.handshake!!.tlsVersion.javaName)
                    assertEquals("TLS_RSA_WITH_AES_128_GCM_SHA256", response.handshake!!.cipherSuite.javaName)
                }
            } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
        }
    }

    @Test fun anUntrustedCertificateIsStillRejected() {
        server().use { server ->
            server.enqueue(MockResponse().setBody("must not be accepted"))
            server.start()
            val client = douyuDesktopWebSocketClient(OkHttpClient())
            try {
                assertThrows(IOException::class.java) {
                    client.newCall(Request.Builder().url(server.url("/").newBuilder().host("localhost").build()).build()).execute().close()
                }
            } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
        }
    }

    private fun server(): MockWebServer {
        val certs = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val context = SSLContext.getInstance("TLS", Conscrypt.newProvider())
        context.init(arrayOf(certs.keyManager), null, null)
        return MockWebServer().apply {
            protocols = listOf(Protocol.HTTP_1_1)
            useHttps(RsaTls12SocketFactory(context.socketFactory), false)
        }
    }

    private class RsaTls12SocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
        private fun configure(socket: Socket) = (socket as SSLSocket).apply {
            enabledProtocols = arrayOf("TLSv1.2")
            enabledCipherSuites = arrayOf("TLS_RSA_WITH_AES_128_GCM_SHA256")
        }
        override fun getDefaultCipherSuites() = arrayOf("TLS_RSA_WITH_AES_128_GCM_SHA256")
        override fun getSupportedCipherSuites() = defaultCipherSuites
        override fun createSocket(socket: Socket, host: String, port: Int, close: Boolean) = configure(delegate.createSocket(socket, host, port, close))
        override fun createSocket(host: String, port: Int) = configure(delegate.createSocket(host, port))
        override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int) = configure(delegate.createSocket(host, port, local, localPort))
        override fun createSocket(host: InetAddress, port: Int) = configure(delegate.createSocket(host, port))
        override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int) = configure(delegate.createSocket(host, port, local, localPort))
    }
}
