package com.mylive.desktop

import com.mylive.app.core.common.*
import com.mylive.app.core.sync.*
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.net.*
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

data class DesktopLanState(val running: Boolean = false, val address: String = "", val port: Int = 23234, val received: Int = 0)

fun desktopLanAddresses(): List<String> = NetworkInterface.getNetworkInterfaces().toList()
    .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
    .flatMap { it.inetAddresses.toList().filterIsInstance<Inet4Address>() }
    .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress && !it.isAnyLocalAddress }
    .sortedBy { !it.isSiteLocalAddress }.map { it.hostAddress }.distinct()

/** Explicitly started, interface-bound receiver. The pairing token is fresh for each session. */
class DesktopLanServer(private val library: DesktopLibrary) : AutoCloseable {
    private var server: HttpServer? = null
    private var executor: ThreadPoolExecutor? = null
    private var discovery: DatagramSocket? = null
    private var discoveryThread: Thread? = null
    private val deviceId = UUID.randomUUID().toString()
    private val guard = LanSyncReplayGuard()
    private val _state = MutableStateFlow(DesktopLanState())
    val state = _state.asStateFlow()
    var token: String = ""
        private set

    @Synchronized fun start(address: String, port: Int = 23234, discover: Boolean = true) {
        check(server == null) { "同步接收已开启" }
        val bind = InetAddress.getByName(address)
        require(!bind.isAnyLocalAddress && (bind.isLoopbackAddress || NetworkInterface.getByInetAddress(bind) != null)) { "请选择本机网络地址" }
        val listener = HttpServer.create(InetSocketAddress(bind, port), 8)
        val pool = ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, ArrayBlockingQueue(16), { task -> Thread(task, "mylive-sync-http").apply { isDaemon = true } })
        try {
            token = UUID.randomUUID().toString(); guard.clear()
            listener.executor = pool
            listener.createContext("/") { handle(it) }
            server = listener; executor = pool
            _state.value = DesktopLanState(true, address, listener.address.port)
            listener.start()
            if (discover && !bind.isLoopbackAddress && port == 23234) startDiscovery()
        } catch (failure: Exception) { listener.stop(0); pool.shutdownNow(); server = null; token = ""; throw failure }
    }

    private fun handle(exchange: HttpExchange) = exchange.use { request ->
        fun respond(code: Int, obj: JSONObject) {
            val bytes = obj.toString().toByteArray(Charsets.UTF_8)
            request.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
            request.responseHeaders.set("Cache-Control", "no-store")
            request.sendResponseHeaders(code, bytes.size.toLong()); request.responseBody.write(bytes)
        }
        fun message(code: Int, ok: Boolean, text: String) = respond(code, JSONObject().put("status", ok).put("message", text))
        try {
            val path = request.requestURI.rawPath
            val method = request.requestMethod
            if (method == "GET" && path == LanSyncProtocol.INFO_PATH) {
                val snap = state.value
                val paired = runCatching { authenticate(request, path, false, ""); true }.getOrDefault(false)
                val info = JSONObject(
                    LanSyncProtocol.encodePeerInfo(
                        paired = paired,
                        port = snap.port,
                        deviceId = deviceId,
                        name = "Windows",
                        appVersion = DESKTOP_VERSION,
                        address = snap.address,
                        supportsFollowBundle = true,
                        platform = "windows"
                    )
                )
                respond(200, info); return@use
            }
            val type = LanSyncImportType.fromPath(path)
            if (method != "POST" || type == null) { message(404, false, "not found"); return@use }
            val maxWire = LanSyncCrypto.maxEnvelopeBytes(MAX_IMPORTED_JSON_BYTES).toInt()
            val length = request.requestHeaders.getFirst("Content-Length")?.toLongOrNull()
            if (length != null && length > maxWire) { message(413, false, "payload too large"); return@use }
            val bytes = request.requestBody.readNBytes(maxWire + 1)
            if (bytes.size > maxWire) { message(413, false, "payload too large"); return@use }
            val body = bytes.toString(Charsets.UTF_8)
            val overlay = request.requestURI.rawQuery?.split('&')?.contains("overlay=1") == true
            val context = authenticate(request, path, overlay, body)
            val text = LanSyncCrypto.decrypt(token, body, context)
            require(text.toByteArray().size <= MAX_IMPORTED_JSON_BYTES)
            runBlocking { library.importSyncPayload(type, text, overlay) }
            _state.update { current -> if (current.running) current.copy(received = current.received + 1) else current }
            message(200, true, "import complete")
        } catch (failure: LanSyncAuthException) { message(401, false, failure.message.orEmpty()) }
        catch (_: LanSyncCryptoException) { message(400, false, "encrypted payload required") }
        catch (_: IllegalArgumentException) { message(400, false, "invalid sync payload") }
        catch (_: org.json.JSONException) { message(400, false, "invalid sync payload") }
        catch (_: Exception) { message(500, false, "sync import failed") }
    }

    private fun authenticate(request: HttpExchange, path: String, overlay: Boolean, body: String) = LanSyncAuth.verifyProof(
        token, request.requestHeaders.getFirst(LanSyncAuth.HEADER_NAME), request.requestMethod, path, overlay, body, replayGuard = guard)

    private fun startDiscovery() {
        // UDP discovery carries no credentials. Reception is scoped to private/link-local peers.
        val socket = runCatching { DatagramSocket(23235).apply { broadcast = true; soTimeout = 1000 } }.getOrNull() ?: return
        discovery = socket
        discoveryThread = Thread({
            var lastReply = 0L
            val bytes = LanSyncProtocol.encodeDiscoveryPeer(deviceId, DESKTOP_APP_NAME, "windows").toByteArray()
            while (!socket.isClosed) try {
                val packet = DatagramPacket(ByteArray(2048), 2048); socket.receive(packet)
                if (!packet.address.isSiteLocalAddress && !packet.address.isLinkLocalAddress) continue
                val hello = LanSyncProtocol.decodeDiscoveryMessage(String(packet.data, 0, packet.length))
                if (hello is LanSyncDiscoveryMessage.Hello && hello.deviceId != deviceId && System.currentTimeMillis() - lastReply > 2000) {
                    lastReply = System.currentTimeMillis()
                    socket.send(DatagramPacket(bytes, bytes.size, packet.address, 23235))
                }
            } catch (_: SocketTimeoutException) {} catch (_: Exception) { if (!socket.isClosed) break }
        }, "mylive-sync-discovery").apply { isDaemon = true; start() }
    }

    @Synchronized override fun close() {
        discovery?.close(); discovery = null; discoveryThread?.interrupt(); discoveryThread = null
        server?.stop(0); server = null; executor?.shutdownNow(); executor = null
        token = ""; guard.clear(); _state.value = DesktopLanState()
    }
}
