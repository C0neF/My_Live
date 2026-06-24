package com.mylive.app.service

import com.mylive.app.BuildConfig
import com.mylive.app.core.common.CoreLog
import com.mylive.app.core.common.safeUrlForLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

enum class RemoteSyncConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}

@Singleton
class RemoteSyncService @Inject constructor() {

    companion object {
        const val K_ROOM_ID_LENGTH = 6
        const val K_DEFAULT_URL = "wss://mylive.conef.hidns.co"
        const val K_DEFAULT_LOCAL_PROXY = "127.0.0.1:51888"
        const val K_ANDROID_EMULATOR_PROXY = "10.0.2.2:51888"
        const val K_GENYMOTION_PROXY = "10.0.3.2:51888"
        const val K_DIRECT_PROXY_VALUE = "direct"
        const val K_LOCAL_DEBUG_SERVER_PORT = 51999
        const val K_LOCAL_DEBUG_SERVER_URL = "ws://127.0.0.1:$K_LOCAL_DEBUG_SERVER_PORT"
        const val K_ANDROID_EMULATOR_DEBUG_SERVER_URL = "ws://10.0.2.2:$K_LOCAL_DEBUG_SERVER_PORT"
        const val K_GENYMOTION_DEBUG_SERVER_URL = "ws://10.0.3.2:$K_LOCAL_DEBUG_SERVER_PORT"

        private val DEFAULT_DEBUG_SYNC_SERVER_URLS = listOf(
            K_LOCAL_DEBUG_SERVER_URL,
            K_ANDROID_EMULATOR_DEBUG_SERVER_URL,
            K_GENYMOTION_DEBUG_SERVER_URL
        )
    }

    private val _connectionState = MutableStateFlow(RemoteSyncConnectionState.DISCONNECTED)
    val connectionState: StateFlow<RemoteSyncConnectionState> = _connectionState.asStateFlow()

    @Volatile private var client: OkHttpClient? = null
    @Volatile private var webSocket: WebSocket? = null

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val requestIdCounter = AtomicLong(0)
    private val connectionIdCounter = AtomicLong(0)
    @Volatile private var activeConnectionId: Long = 0
    @Volatile internal var defaultSyncServerUrl: String = K_DEFAULT_URL
    @Volatile internal var debugBuild: Boolean = BuildConfig.DEBUG
    @Volatile internal var debugSyncServerUrls: List<String> = DEFAULT_DEBUG_SYNC_SERVER_URLS

    var currentRoomId: String = ""
        private set

    var selfConnectionId: String? = null
        private set

    // Callbacks/Listeners for incoming data sync events
    var onFavoriteReceived: ((overlay: Boolean, content: String) -> Unit)? = null
    var onHistoryReceived: ((overlay: Boolean, content: String) -> Unit)? = null
    var onShieldWordReceived: ((overlay: Boolean, content: String) -> Unit)? = null
    var onBiliAccountReceived: ((overlay: Boolean, content: String) -> Unit)? = null
    var onDouyinAccountReceived: ((overlay: Boolean, content: String) -> Unit)? = null
    var onRoomDestroyed: ((reason: String) -> Unit)? = null
    var onRoomUserUpdated: ((List<RemoteRoomUser>) -> Unit)? = null

    private var pingThread: Thread? = null
    private val ipv4FirstDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            val ipv4Addresses = addresses.filterIsInstance<Inet4Address>()
            return ipv4Addresses.ifEmpty { addresses }
        }
    }

    suspend fun connect(
        configuredUrl: String,
        configuredProxyUrl: String,
        pathSuffix: String = ""
    ) {
        val targets = withContext(Dispatchers.IO) {
            resolveConnectionTargets(configuredUrl, pathSuffix)
        }
        var lastError: Throwable? = null

        for (target in targets) {
            val connectionId = prepareForNewConnection()
            try {
                connectToTarget(connectionId, target, configuredProxyUrl)
                return
            } catch (e: Exception) {
                lastError = e
                Timber.e(
                    e,
                    "RemoteSyncService: Connection failed for %s",
                    safeUrlForLog(target.url)
                )
                cleanupConnection(connectionId)
            }
        }
        val error = lastError ?: Exception("同步服务地址不可用")
        throw Exception(formatConnectionError(error), error)
    }

    private suspend fun connectToTarget(
        connectionId: Long,
        target: ConnectionTarget,
        configuredProxyUrl: String
    ) {
        val wsUrl = target.url
        _connectionState.value = RemoteSyncConnectionState.CONNECTING
        Timber.d(
            "RemoteSyncService: Connecting to %s with proxy configured=%s",
            safeUrlForLog(wsUrl),
            configuredProxyUrl.isNotBlank()
        )

        val connectionReady = CompletableDeferred<Unit>()
        val clientBuilder = OkHttpClient.Builder()
            .pingInterval(java.time.Duration.ofSeconds(20))
            .dns(ipv4FirstDns)

        val proxy = if (target.useProxy) {
            withContext(Dispatchers.IO) {
                resolveProxy(configuredProxyUrl)
            }
        } else {
            null
        }
        if (proxy != null) {
            clientBuilder.proxy(proxy)
            Timber.d("RemoteSyncService: Using configured proxy")
        }

        val okHttpClient = clientBuilder.build()
        this.client = okHttpClient

        val request = Request.Builder().url(wsUrl).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isActiveConnection(connectionId)) {
                    webSocket.close(1000, "Stale connection")
                    return
                }
                _connectionState.value = RemoteSyncConnectionState.CONNECTED
                Timber.d("RemoteSyncService: WebSocket connection established")
                startHeartbeat(connectionId)
                connectionReady.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isActiveConnection(connectionId)) return
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (!isActiveConnection(connectionId)) return
                CoreLog.d("RemoteSyncService: WebSocket closing ($code): $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isActiveConnection(connectionId)) return
                Timber.d("RemoteSyncService: WebSocket closed (%s): %s", code, reason)
                connectionReady.completeExceptionally(Exception("同步服务连接已关闭: $reason"))
                cleanupConnection(connectionId)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isActiveConnection(connectionId)) return
                Timber.e(t, "RemoteSyncService: WebSocket error response=%s", response?.code)
                connectionReady.completeExceptionally(t)
                cleanupConnection(connectionId)
            }
        }

        webSocket = okHttpClient.newWebSocket(request, listener)
        withTimeout(15000) {
            connectionReady.await()
        }
    }

    @Synchronized
    fun disconnect() {
        activeConnectionId = connectionIdCounter.incrementAndGet()
        cleanupCurrentConnection(setDisconnected = true)
    }

    @Synchronized
    private fun prepareForNewConnection(): Long {
        val connectionId = connectionIdCounter.incrementAndGet()
        activeConnectionId = connectionId
        cleanupCurrentConnection(setDisconnected = false)
        currentRoomId = ""
        selfConnectionId = null
        return connectionId
    }

    private fun isActiveConnection(connectionId: Long): Boolean {
        return activeConnectionId == connectionId
    }

    @Synchronized
    private fun cleanupConnection(connectionId: Long) {
        if (activeConnectionId != connectionId) return
        activeConnectionId = connectionIdCounter.incrementAndGet()
        cleanupCurrentConnection(setDisconnected = true)
    }

    private fun cleanupCurrentConnection(setDisconnected: Boolean) {
        stopHeartbeat()
        webSocket?.close(1000, "Disconnect")
        webSocket = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
        if (setDisconnected) {
            _connectionState.value = RemoteSyncConnectionState.DISCONNECTED
        }

        // Fail all pending requests
        for (deferred in pendingRequests.values) {
            deferred.complete(JSONObject().apply {
                put("type", "error")
                put("error", JSONObject().apply {
                    put("message", "Connection disconnected")
                })
            })
        }
        pendingRequests.clear()
    }

    private fun startHeartbeat(connectionId: Long) {
        stopHeartbeat()
        pingThread = Thread {
            try {
                while (
                    !Thread.currentThread().isInterrupted &&
                    isActiveConnection(connectionId) &&
                    _connectionState.value == RemoteSyncConnectionState.CONNECTED
                ) {
                    Thread.sleep(20000)
                    if (!isActiveConnection(connectionId)) break
                    val ws = webSocket ?: break
                    val pingId = "ping_${System.currentTimeMillis()}"
                    val pingMsg = JSONObject().apply {
                        put("type", "ping")
                        put("requestId", pingId)
                    }
                    ws.send(pingMsg.toString())
                }
            } catch (e: InterruptedException) {
                // Ignore
            } catch (e: Exception) {
                CoreLog.e("RemoteSyncService: Heartbeat error", e)
            }
        }.apply { start() }
    }

    private fun stopHeartbeat() {
        pingThread?.interrupt()
        pingThread = null
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")
            val requestId = json.optString("requestId")

            if (requestId.isNotEmpty()) {
                val deferred = pendingRequests.remove(requestId)
                if (deferred != null) {
                    deferred.complete(json)
                    return
                }
            }

            when (type) {
                "favoriteReceived" -> emitSyncPayload(json, onFavoriteReceived)
                "historyReceived" -> emitSyncPayload(json, onHistoryReceived)
                "shieldWordReceived" -> emitSyncPayload(json, onShieldWordReceived)
                "biliAccountReceived" -> emitSyncPayload(json, onBiliAccountReceived)
                "douyinAccountReceived" -> emitSyncPayload(json, onDouyinAccountReceived)
                "roomDestroyed" -> {
                    val reason = json.optString("reason", "unknown")
                    onRoomDestroyed?.invoke(reason)
                }
                "userUpdated" -> {
                    val usersArr = json.optJSONArray("users") ?: JSONArray()
                    val userList = mutableListOf<RemoteRoomUser>()
                    for (i in 0 until usersArr.length()) {
                        val uObj = usersArr.optJSONObject(i) ?: continue
                        val isSelf = uObj.optBoolean("isSelf", false)
                        val connId = uObj.optString("connectionId")
                        if (isSelf) {
                            selfConnectionId = connId
                        }
                        userList.add(
                            RemoteRoomUser(
                                connectionId = connId,
                                shortId = uObj.optString("shortId"),
                                platform = uObj.optString("platform"),
                                version = uObj.optString("version"),
                                app = uObj.optString("app"),
                                isCreator = uObj.optBoolean("isCreator", false),
                                isSelf = isSelf
                            )
                        )
                    }
                    onRoomUserUpdated?.invoke(userList)
                }
            }
        } catch (e: Exception) {
            CoreLog.e("RemoteSyncService: Error handling incoming websocket message", e)
        }
    }

    private fun emitSyncPayload(json: JSONObject, callback: ((Boolean, String) -> Unit)?) {
        val payload = json.optJSONObject("payload") ?: return
        val overlay = payload.optBoolean("overlay", false)
        val content = payload.optString("content", "")
        callback?.invoke(overlay, content)
    }

    private suspend fun sendRequest(
        type: String,
        roomId: String? = null,
        payload: Any? = null
    ): RemoteSyncResp {
        val ws = webSocket
        if (ws == null || _connectionState.value != RemoteSyncConnectionState.CONNECTED) {
            return RemoteSyncResp(false, "未连接到同步服务", null)
        }

        val reqId = requestIdCounter.incrementAndGet().toString()
        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[reqId] = deferred

        val msg = JSONObject().apply {
            put("type", type)
            put("requestId", reqId)
            if (!roomId.isNullOrBlank()) {
                put("roomId", roomId)
            }
            if (payload != null) {
                put("payload", payload)
            }
        }

        val sent = ws.send(msg.toString())
        if (!sent) {
            pendingRequests.remove(reqId)
            return RemoteSyncResp(false, "发送请求失败", null)
        }

        return try {
            withTimeout(15000) {
                val resp = deferred.await()
                val respType = resp.optString("type")
                if (respType == "error") {
                    val err = resp.optJSONObject("error")
                    val msgStr = err?.optString("message") ?: err?.optString("code") ?: "同步服务返回异常"
                    RemoteSyncResp(false, msgStr, null)
                } else if (resp.has("status") && !resp.optBoolean("status", true)) {
                    val msgStr = resp.optString(
                        "message",
                        resp.optString("reason", "同步服务返回失败")
                    )
                    RemoteSyncResp(false, msgStr, resp)
                } else {
                    RemoteSyncResp(true, "", resp)
                }
            }
        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(reqId)
            RemoteSyncResp(false, "同步服务响应超时", null)
        } catch (e: Exception) {
            pendingRequests.remove(reqId)
            RemoteSyncResp(false, e.message ?: "未知请求错误", null)
        }
    }

    /**
     * 创建房间：连接到 /sync/create，服务器生成房间 ID
     */
    suspend fun createRoom(configuredUrl: String, configuredProxyUrl: String, clientInfo: JSONObject): RemoteSyncResp {
        val baseUrl = configuredUrl.trim().trimEnd('/')
        val createUrl = appendWebSocketPath(baseUrl, "/sync/create")
        try {
            connect(createUrl, configuredProxyUrl, "/sync/create")
        } catch (e: Exception) {
            return RemoteSyncResp(false, e.message ?: "创建房间连接失败", null)
        }
        val resp = sendRequest("createRoom", payload = clientInfo)
        if (resp.isSuccess && resp.data != null) {
            val roomId = resp.data.optString("roomId")
            if (roomId.isNotEmpty()) {
                currentRoomId = roomId.trim().uppercase()
                return RemoteSyncResp(true, "", JSONObject().put("roomId", roomId))
            }
        }
        return RemoteSyncResp(false, resp.message.ifBlank { "创建房间失败" }, null)
    }

    /**
     * 加入房间：连接到 /sync/{roomId}
     */
    suspend fun joinRoom(configuredUrl: String, configuredProxyUrl: String, roomId: String, clientInfo: JSONObject): RemoteSyncResp {
        val cleanRoomId = roomId.trim().uppercase()
        val baseUrl = configuredUrl.trim().trimEnd('/')
        val joinPath = "/sync/$cleanRoomId"
        val joinUrl = appendWebSocketPath(baseUrl, joinPath)
        try {
            connect(joinUrl, configuredProxyUrl, joinPath)
        } catch (e: Exception) {
            return RemoteSyncResp(false, e.message ?: "加入房间连接失败", null)
        }
        val resp = sendRequest("joinRoom", payload = clientInfo)
        if (resp.isSuccess) {
            currentRoomId = cleanRoomId
        }
        return resp
    }

    /**
     * 发送同步内容（收藏/历史/屏蔽词/账号等），通过当前 WebSocket 连接
     */
    suspend fun sendContent(
        action: String,
        overlay: Boolean,
        content: String
    ): RemoteSyncResp {
        val mappedAction = when (action) {
            "SendFavorite" -> "sendFavorite"
            "SendHistory" -> "sendHistory"
            "SendShieldWord" -> "sendShieldWord"
            "SendBiliAccount" -> "sendBiliAccount"
            "SendDouyinAccount" -> "sendDouyinAccount"
            else -> action
        }
        val payload = JSONObject().apply {
            put("overlay", overlay)
            put("content", content)
        }
        return sendRequest(mappedAction, payload = payload)
    }

    private fun resolveProxy(configuredProxy: String): Proxy? {
        val address = resolveProxyAddress(configuredProxy) ?: return null
        val (host, port) = parseProxyAddress(address) ?: return null
        return Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
    }

    fun isValidProxyConfig(value: String): Boolean {
        val text = value.trim()
        if (text.isEmpty() || text.lowercase() == K_DIRECT_PROXY_VALUE) {
            return true
        }
        return normalizeProxyAddress(text) != null
    }

    private fun resolveProxyAddress(configuredProxy: String): String? {
        val text = configuredProxy.trim()
        if (text.lowercase() == K_DIRECT_PROXY_VALUE) {
            return null
        }
        if (text.isNotEmpty()) {
            return normalizeProxyAddress(text)
        }
        return detectLocalProxy()
    }

    private fun detectLocalProxy(): String? {
        val candidates = listOf(
            K_ANDROID_EMULATOR_PROXY,
            K_GENYMOTION_PROXY,
            K_DEFAULT_LOCAL_PROXY
        )
        return candidates.firstOrNull { address ->
            val (host, port) = parseProxyAddress(address) ?: return@firstOrNull false
            isTcpPortOpen(host, port)
        }
    }

    private fun resolveConnectionTargets(configuredUrl: String, pathSuffix: String): List<ConnectionTarget> {
        val configured = configuredUrl.trim()
        if (configured.isNotEmpty()) {
            return listOf(ConnectionTarget(configured))
        }

        val defaultTarget = ConnectionTarget(appendWebSocketPath(defaultSyncServerUrl, pathSuffix))
        if (!debugBuild) {
            return listOf(defaultTarget)
        }

        val debugTargets = debugSyncServerUrls
            .map { ConnectionTarget(appendWebSocketPath(it, pathSuffix), useProxy = false) }
        val reachableDebugTargets = debugTargets.filter { isWebSocketTcpPortOpen(it.url) }
        return if (reachableDebugTargets.isNotEmpty()) {
            reachableDebugTargets + defaultTarget
        } else {
            listOf(defaultTarget)
        }
    }

    private fun appendWebSocketPath(baseUrl: String, pathSuffix: String): String {
        val trimmedBase = baseUrl.trim().trimEnd('/')
        if (trimmedBase.isEmpty()) return ""
        val suffix = pathSuffix.trim()
        if (suffix.isEmpty()) return trimmedBase
        return "$trimmedBase/${suffix.trimStart('/')}"
    }

    private fun isWebSocketTcpPortOpen(url: String): Boolean {
        val hostAndPort = parseWebSocketHostAndPort(url) ?: return false
        return isTcpPortOpen(hostAndPort.first, hostAndPort.second)
    }

    private fun parseWebSocketHostAndPort(url: String): Pair<String, Int>? {
        return try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "ws" && scheme != "wss") return null
            val host = uri.host ?: return null
            val port = when {
                uri.port > 0 -> uri.port
                scheme == "wss" -> 443
                else -> 80
            }
            host to port
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeProxyAddress(value: String): String? {
        var text = value.trim()
        if (text.isEmpty()) return null
        if (!text.contains("://")) {
            val parts = text.split(":")
            if (parts.size == 2 && parts[1].toIntOrNull() != null) {
                return text
            }
            return null
        }
        return try {
            val url = java.net.URL(text)
            if ((url.protocol == "http" || url.protocol == "https") && url.host.isNotEmpty() && url.port != -1) {
                "${url.host}:${url.port}"
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseProxyAddress(address: String): Pair<String, Int>? {
        val parts = address.split(":")
        if (parts.size != 2) return null
        val port = parts[1].toIntOrNull() ?: return null
        return parts[0] to port
    }

    private fun isTcpPortOpen(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 250)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun formatConnectionError(error: Throwable): String {
        val text = error.message ?: error.toString()
        if (error is TimeoutCancellationException || text.contains("timeout", ignoreCase = true)) {
            return "同步服务连接超时，请检查网络或同步服务地址。当前默认 workers.dev 域名在部分网络下可能无法访问。"
        }
        if (
            text.contains("Unable to resolve host", ignoreCase = true) ||
            text.contains("UnknownHost", ignoreCase = true)
        ) {
            return "无法解析同步服务地址，请检查网络或同步服务地址"
        }
        if (
            text.contains("Socket", ignoreCase = true) ||
            text.contains("SSL", ignoreCase = true) ||
            text.contains("Failed to connect", ignoreCase = true)
        ) {
            return "无法连接同步服务，请检查网络或同步服务地址"
        }
        return text
            .replaceFirst(Regex("^Exception:\\s*"), "")
            .trim()
            .ifBlank { "未知连接错误" }
    }

    private data class ConnectionTarget(
        val url: String,
        val useProxy: Boolean = true
    )
}

data class RemoteRoomUser(
    val connectionId: String,
    val shortId: String,
    val platform: String,
    val version: String,
    val app: String,
    val isCreator: Boolean,
    val isSelf: Boolean
)

data class RemoteSyncResp(
    val isSuccess: Boolean,
    val message: String,
    val data: JSONObject?
) {
    // Overloaded property to directly get string data when appropriate
    val dataString: String?
        get() = data as? String ?: data?.toString()
}
