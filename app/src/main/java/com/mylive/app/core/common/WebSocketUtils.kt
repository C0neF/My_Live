package com.mylive.app.core.common

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.Timer
import java.util.TimerTask

/**
 * WebSocket connection status.
 */
enum class SocketStatus {
    CONNECTING,
    CONNECTED,
    FAILED,
    CLOSED
}

/**
 * WebSocket utility using OkHttp, providing auto-reconnect, backup URLs,
 * heartbeat, and the same lifecycle callbacks as the Dart WebScoketUtils.
 *
 * Usage:
 * ```
 * val ws = WebSocketUtils(okHttpClient)
 * ws.connect(
 *     url = "wss://example.com/ws",
 *     heartBeatTime = 30_000L,
 *     onMessage = { data -> /* handle message */ },
 *     onClose = { msg -> /* handle close */ },
 *     onReady = { /* connected */ },
 *     onHeartBeat = { ws.sendMessage("ping") }
 * )
 * ```
 */
class WebSocketUtils(
    private val client: OkHttpClient
) {

    @Volatile
    var status: SocketStatus = SocketStatus.CLOSED
        private set

    @Volatile
    private var webSocket: WebSocket? = null
    @Volatile
    private var heartBeatTimer: Timer? = null
    @Volatile
    private var reconnectTimer: Timer? = null
    @Volatile
    private var reconnectTime: Int = 0
    private var maxReconnectTime: Int = 5
    private var currentUrlIndex: Int = 0
    @Volatile
    private var intentionalClose: Boolean = true

    // Cached parameters for reconnect
    private var connectUrl: String = ""
    private var connectBackupUrl: String? = null
    private var connectBackupUrls: List<String> = emptyList()
    private var connectHeartBeatTime: Long = 30_000L
    private var connectIdleTimeoutMillis: Long? = null
    private var connectHeaders: Map<String, String>? = null
    private var callbackMessage: ((Any) -> Unit)? = null
    private var callbackClose: ((String) -> Unit)? = null
    private var callbackReconnect: (() -> Unit)? = null
    private var callbackReady: (() -> Unit)? = null
    private var callbackHeartBeat: (() -> Unit)? = null
    @Volatile
    private var lastMessageAtMillis: Long = 0L

    /**
     * Build the ordered list of URLs to try: primary + backup(s), deduplicated.
     */
    private val connectUrls: List<String>
        get() {
            val urls = mutableListOf(connectUrl)
            if (!connectBackupUrl.isNullOrEmpty()) {
                urls.add(connectBackupUrl!!)
            }
            urls.addAll(connectBackupUrls.filter { it.isNotEmpty() })
            return urls.distinct()
        }

    /**
     * Connect to a WebSocket server with auto-reconnect and backup URL support.
     *
     * @param url Primary WebSocket URL
     * @param backupUrl Optional backup URL
     * @param backupUrls Optional list of additional backup URLs
     * @param heartBeatTime Heartbeat interval in milliseconds
     * @param idleTimeoutMillis Optional timeout for connections that stay open but stop receiving messages
     * @param headers Optional request headers
     * @param onMessage Callback when a message is received
     * @param onClose Callback when connection is closed
     * @param onReconnect Callback when attempting to reconnect
     * @param onReady Callback when connection is established
     * @param onHeartBeat Callback for heartbeat ticks
     */
    fun connect(
        url: String,
        backupUrl: String? = null,
        backupUrls: List<String> = emptyList(),
        heartBeatTime: Long = 30_000L,
        idleTimeoutMillis: Long? = null,
        headers: Map<String, String>? = null,
        onMessage: ((Any) -> Unit)? = null,
        onClose: ((String) -> Unit)? = null,
        onReconnect: (() -> Unit)? = null,
        onReady: (() -> Unit)? = null,
        onHeartBeat: (() -> Unit)? = null
    ) {
        // Cache parameters for reconnect
        this.connectUrl = url
        this.connectBackupUrl = backupUrl
        this.connectBackupUrls = backupUrls
        this.connectHeartBeatTime = heartBeatTime
        this.connectIdleTimeoutMillis = idleTimeoutMillis
        this.connectHeaders = headers
        this.callbackMessage = onMessage
        this.callbackClose = onClose
        this.callbackReconnect = onReconnect
        this.callbackReady = onReady
        this.callbackHeartBeat = onHeartBeat
        this.intentionalClose = false
        this.currentUrlIndex = 0
        this.reconnectTime = 0

        doConnect(retry = false)
    }

    /**
     * Internal connect implementation. Tries each URL in order.
     *
     * @param retry If true, skip the primary URL and try backup(s) only
     */
    private fun doConnect(retry: Boolean) {
        currentUrlIndex = if (retry && connectUrls.size > 1) 1 else 0
        doConnectCurrentUrl()
    }

    private fun doConnectCurrentUrl() {
        closeInternal()

        val wsUrl = connectUrls.getOrNull(currentUrlIndex)
        if (wsUrl == null) {
            onError(RuntimeException("WebSocket connection failed"))
            return
        }

        try {
            status = SocketStatus.CONNECTING
            val requestBuilder = Request.Builder().url(wsUrl)
            connectHeaders?.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            webSocket = client.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (webSocket !== this@WebSocketUtils.webSocket) return
                    onReady()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (webSocket !== this@WebSocketUtils.webSocket) return
                    receiveMessage(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    if (webSocket !== this@WebSocketUtils.webSocket) return
                    receiveMessage(bytes.toByteArray())
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (webSocket !== this@WebSocketUtils.webSocket) return
                    onError(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (webSocket !== this@WebSocketUtils.webSocket) return
                    onDone()
                }
            })
        } catch (e: Throwable) {
            webSocket?.cancel()
            webSocket = null
            onError(e)
        }
    }

    /**
     * Called when the WebSocket connection is established.
     */
    private fun onReady() {
        status = SocketStatus.CONNECTED
        currentUrlIndex = 0
        lastMessageAtMillis = System.currentTimeMillis()
        // Cancel reconnect timer on successful connection to prevent repeated reconnect attempts
        reconnectTimer?.cancel()
        reconnectTimer = null
        reconnectTime = 0
        callbackReady?.invoke()
        initHeartBeat()
    }

    /**
     * Start the periodic heartbeat timer.
     */
    private fun initHeartBeat() {
        heartBeatTimer?.cancel()
        heartBeatTimer = Timer("ws-heartbeat", true).apply {
            schedule(object : TimerTask() {
                override fun run() {
                    val idleTimeout = connectIdleTimeoutMillis
                    if (idleTimeout != null && idleTimeout > 0 && status == SocketStatus.CONNECTED) {
                        val idleFor = System.currentTimeMillis() - lastMessageAtMillis
                        if (idleFor >= idleTimeout) {
                            onIdleTimeout()
                            return
                        }
                    }
                    callbackHeartBeat?.invoke()
                }
            }, connectHeartBeatTime, connectHeartBeatTime)
        }
    }

    /**
     * Called when a message is received. Resets reconnect counter on first message.
     */
    private fun receiveMessage(data: Any) {
        lastMessageAtMillis = System.currentTimeMillis()
        reconnectTime = 0
        callbackMessage?.invoke(data)
    }

    /**
     * A silent-but-open socket is recoverable and should not be surfaced as a
     * terminal close to the UI. Recreate it immediately so streams can resume.
     */
    private fun onIdleTimeout() {
        if (intentionalClose) return
        heartBeatTimer?.cancel()
        heartBeatTimer = null
        status = SocketStatus.FAILED
        webSocket?.cancel()
        webSocket = null
        callbackReconnect?.invoke()
        doConnect(retry = true)
    }

    /**
     * Called on error. Sets status to FAILED and notifies via onClose.
     * Skips if we intentionally closed the socket.
     */
    private fun onError(t: Throwable) {
        if (intentionalClose) return
        heartBeatTimer?.cancel()
        heartBeatTimer = null
        if (currentUrlIndex < connectUrls.lastIndex) {
            currentUrlIndex++
            callbackReconnect?.invoke()
            doConnectCurrentUrl()
            return
        }
        status = SocketStatus.FAILED
        callbackClose?.invoke(t.toString())
        reconnect()
    }

    /**
     * Called when the WebSocket is closed (not by us). Triggers reconnect.
     */
    private fun onDone() {
        if (intentionalClose) return
        reconnect()
    }

    /**
     * Send a text or binary message over the WebSocket.
     *
     * @param message A [String] (sent as text) or [ByteArray] (sent as binary)
     */
    fun sendMessage(message: Any) {
        if (status != SocketStatus.CONNECTED) return
        when (message) {
            is String -> webSocket?.send(message)
            is ByteArray -> webSocket?.send(ByteString.of(*message))
        }
    }

    /**
     * Close the WebSocket connection and clean up all timers.
     * This is a user-initiated close; no reconnect will be attempted.
     */
    fun close() {
        // Cancel timers BEFORE setting status to prevent race conditions
        // where a timer callback fires between status change and timer cancellation
        heartBeatTimer?.cancel()
        heartBeatTimer = null
        reconnectTimer?.cancel()
        reconnectTimer = null
        reconnectTime = 0
        intentionalClose = true
        status = SocketStatus.CLOSED
        webSocket?.close(1000, "Client closed")
        webSocket = null
    }

    /**
     * Internal close that cancels timers but does not set CLOSED status
     * (used before reconnect attempts).
     */
    private fun closeInternal() {
        heartBeatTimer?.cancel()
        heartBeatTimer = null
        webSocket?.cancel()
        webSocket = null
    }

    /**
     * Attempt to reconnect with exponential-ish backoff (fixed 5-second interval).
     * After [maxReconnectTime] attempts, gives up and notifies [callbackClose].
     */
    private fun reconnect() {
        if (intentionalClose) return
        if (reconnectTime >= maxReconnectTime) {
            callbackClose?.invoke("重连超过最大次数，与服务器断开连接")
            reconnectTimer?.cancel()
            reconnectTimer = null
            close()
            return
        }

        reconnectTime++
        reconnectTimer?.cancel()
        reconnectTimer = Timer("ws-reconnect", true).apply {
            schedule(object : TimerTask() {
                override fun run() {
                    if (intentionalClose) return
                    callbackReconnect?.invoke()
                    doConnect(retry = true)
                }
            }, 5_000L)
        }
    }
}
