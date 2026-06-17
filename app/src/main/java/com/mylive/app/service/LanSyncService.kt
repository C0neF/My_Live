package com.mylive.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.mutableStateListOf
import com.mylive.app.BuildConfig
import com.mylive.app.core.common.CoreLog
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.HistoryRepository
import com.mylive.app.data.repository.ProfileBackupManager
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.data.repository.ShieldRepository
import com.mylive.app.ui.screen.sync.decodeLanSyncShieldKeywords
import com.mylive.app.ui.screen.sync.decodeFollowTagsForLanSync
import dagger.hilt.android.AndroidEntryPoint
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class LanSyncService : Service() {

    @Inject
    lateinit var profileBackupManager: ProfileBackupManager

    @Inject
    lateinit var followRepository: FollowRepository

    @Inject
    lateinit var historyRepository: HistoryRepository

    @Inject
    lateinit var shieldRepository: ShieldRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var httpServer: HttpServer? = null
    private var udpSocket: DatagramSocket? = null
    private var udpListenThread: Thread? = null

    // SupervisorJob + a logging handler so one malformed LAN payload can't cancel the whole
    // scope (which would permanently disable all further sync handling until the service restarts).
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> CoreLog.e("LanSyncService: sync task failed", e) }
    )

    companion object {
        const val UDP_PORT = 23235
        const val HTTP_PORT = 23234

        val scanClients = mutableStateListOf<SyncClient>()
        var isRunning = false
        var ipAddress = ""
        var syncDeviceId = ""

        /**
         * Pairing token. Generated per service start and surfaced ONLY in this device's
         * "My Info" QR (a visual, out-of-band channel). Every write endpoint requires it, so a
         * same-LAN attacker who never saw the QR cannot push data / overwrite account cookies.
         * Deliberately NOT returned by /info or broadcast over UDP.
         */
        var syncToken = ""

        fun start(context: Context) {
            if (isRunning) return
            val intent = Intent(context, LanSyncService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LanSyncService::class.java)
            context.stopService(intent)
        }

        fun sendHello() {
            Thread {
                try {
                    val socket = DatagramSocket()
                    socket.broadcast = true
                    val payload = JSONObject().apply {
                        put("id", syncDeviceId)
                        put("type", "hello")
                    }.toString().toByteArray()
                    val packet = DatagramPacket(
                        payload, payload.size,
                        InetAddress.getByName("255.255.255.255"), UDP_PORT
                    )
                    socket.send(packet)
                    socket.close()
                    CoreLog.d("LanSyncService: Broadcast hello sent")
                } catch (e: Exception) {
                    CoreLog.e("LanSyncService: Broadcast hello failed", e)
                }
            }.start()
        }

        fun sendInfo() {
            Thread {
                try {
                    val socket = DatagramSocket()
                    socket.broadcast = true
                    val payload = JSONObject().apply {
                        put("id", syncDeviceId)
                        put("type", "android")
                        put("name", Build.MODEL)
                    }.toString().toByteArray()
                    val packet = DatagramPacket(
                        payload, payload.size,
                        InetAddress.getByName("255.255.255.255"), UDP_PORT
                    )
                    socket.send(packet)
                    socket.close()
                    CoreLog.d("LanSyncService: Broadcast info sent")
                } catch (e: Exception) {
                    CoreLog.e("LanSyncService: Broadcast info failed", e)
                }
            }.start()
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (syncDeviceId.isEmpty()) {
            syncDeviceId = UUID.randomUUID().toString().split("-").first()
        }
        if (syncToken.isEmpty()) {
            syncToken = UUID.randomUUID().toString().replace("-", "").substring(0, 8)
        }
        CoreLog.d("LanSyncService: pair token generated")
        ipAddress = getLocalIpAddress()
        startHttpServer()
        startUdpListener()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        httpServer?.stop()
        udpSocket?.close()
        udpListenThread?.interrupt()
        serviceScope.cancel()
        scanClients.clear()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startHttpServer() {
        try {
            httpServer = HttpServer(HTTP_PORT)
            httpServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            CoreLog.d("LanSyncService: HTTP Server started on port $HTTP_PORT")
        } catch (e: Exception) {
            CoreLog.e("LanSyncService: HTTP Server start failed", e)
        }
    }

    private fun startUdpListener() {
        udpListenThread = Thread {
            try {
                udpSocket = DatagramSocket(UDP_PORT)
                val buffer = ByteArray(2048)
                while (!Thread.currentThread().isInterrupted) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpSocket?.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    handleUdpMessage(message, packet.address.hostAddress ?: "")
                }
            } catch (e: Exception) {
                CoreLog.w("LanSyncService: UDP socket closed or error: ${e.message}")
            }
        }.apply { start() }
    }

    private fun handleUdpMessage(msg: String, senderIp: String) {
        try {
            if (msg.startsWith("{") && msg.endsWith("}")) {
                val data = JSONObject(msg)
                val id = data.optString("id")
                if (id == syncDeviceId) return // Skip own broadcast
                
                val type = data.optString("type")
                if (type == "hello") {
                    sendInfo()
                } else {
                    val name = data.optString("name")
                    val existing = scanClients.find { it.address == senderIp }
                    if (existing == null) {
                        scanClients.add(
                            SyncClient(
                                id = id,
                                name = name.ifEmpty { "Android Device" },
                                address = senderIp,
                                port = HTTP_PORT,
                                type = type
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            CoreLog.e("LanSyncService: Parse UDP message failed", e)
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return "127.0.0.1"
    }

    private inner class HttpServer(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val method = session.method
            val uri = session.uri
            
            CoreLog.d("LanSyncService: HTTP request: $method $uri")
            
            return try {
                if (method == Method.GET && uri == "/") {
                    val res = JSONObject().apply {
                        put("status", true)
                        put("message", "http server is running...")
                        put("app", "My Live")
                        put("type", "android")
                    }
                    newFixedLengthResponse(Response.Status.OK, "application/json", res.toString())
                } else if (method == Method.GET && uri == "/info") {
                    val res = JSONObject().apply {
                        put("id", syncDeviceId)
                        put("type", "android")
                        put("name", Build.MODEL)
                        put("version", BuildConfig.VERSION_NAME)
                        put("address", ipAddress)
                        put("port", HTTP_PORT)
                    }
                    newFixedLengthResponse(Response.Status.OK, "application/json", res.toString())
                } else if (method == Method.POST) {
                    // Every write requires the pairing token (shown only in this device's My Info
                    // QR). Reject otherwise so a same-LAN attacker can't push data or overwrite
                    // account cookies without having visually paired.
                    val providedToken = session.headers["x-sync-token"]
                        ?: session.parameters["token"]?.firstOrNull()
                        ?: ""
                    if (syncToken.isNotEmpty() && providedToken != syncToken) {
                        CoreLog.w("LanSyncService: rejected unauthorized write to $uri")
                        return newFixedLengthResponse(
                            Response.Status.UNAUTHORIZED,
                            "application/json",
                            JSONObject().apply {
                                put("status", false)
                                put("message", "unauthorized: pairing required")
                            }.toString()
                        )
                    }

                    val files = mutableMapOf<String, String>()
                    session.parseBody(files)
                    val body = files["postData"] ?: ""
                    val params = session.parameters
                    val overlay = params["overlay"]?.firstOrNull() == "1"
                    
                    when (uri) {
                        "/sync/follow" -> {
                            val arr = JSONArray(body)
                            serviceScope.launch {
                                for (i in 0 until arr.length()) {
                                    val item = arr.getJSONObject(i)
                                    val siteId = item.getString("siteId")
                                    val roomId = item.getString("roomId")
                                    val userName = item.getString("userName")
                                    val face = item.optString("face", item.optString("avatar", ""))
                                    val isSpecial = item.optBoolean("isSpecialFollow", false)
                                    val tag = item.optString("tag", "")
                                    followRepository.addFollow(
                                        FollowUserEntity(
                                            id = "${siteId}_${roomId}",
                                            roomId = roomId,
                                            siteId = siteId,
                                            userName = userName,
                                            face = face,
                                            addTime = System.currentTimeMillis(),
                                            isSpecialFollow = isSpecial,
                                            tag = tag
                                        )
                                    )
                                }
                            }
                            newJsonResponse(true, "success")
                        }
                        "/sync/tag" -> {
                            val tags = decodeFollowTagsForLanSync(body)
                            serviceScope.launch {
                                for (tag in tags) {
                                    followRepository.addTag(tag)
                                }
                            }
                            newJsonResponse(true, "success")
                        }
                        "/sync/history" -> {
                            val arr = JSONArray(body)
                            serviceScope.launch {
                                for (i in 0 until arr.length()) {
                                    val item = arr.getJSONObject(i)
                                    val siteId = item.getString("siteId")
                                    val roomId = item.getString("roomId")
                                    val userName = item.getString("userName")
                                    val face = item.optString("face", item.optString("avatar", ""))
                                    val updateTime = item.optLong("updateTime", System.currentTimeMillis())
                                    historyRepository.addHistory(
                                        HistoryEntity(
                                            id = "${siteId}_${roomId}",
                                            roomId = roomId,
                                            siteId = siteId,
                                            userName = userName,
                                            face = face,
                                            updateTime = updateTime
                                        )
                                    )
                                }
                            }
                            newJsonResponse(true, "success")
                        }
                        "/sync/blocked_word" -> {
                            val keywords = decodeLanSyncShieldKeywords(body)
                            serviceScope.launch {
                                if (overlay) {
                                    shieldRepository.clearAllKeywords()
                                }
                                for (kw in keywords) {
                                    shieldRepository.addShield(ShieldEntity(value = "keyword:$kw"))
                                }
                            }
                            newJsonResponse(true, "success")
                        }
                        "/sync/profile" -> {
                            serviceScope.launch {
                                // Untrusted LAN push: must not be able to redirect our sync/proxy endpoints.
                                profileBackupManager.importProfileJson(body, trusted = false)
                            }
                            newJsonResponse(true, "success")
                        }
                        "/sync/account/bilibili" -> {
                            val obj = JSONObject(body)
                            val cookie = obj.optString("cookie")
                            serviceScope.launch {
                                settingsRepository.setBilibiliCookie(cookie)
                            }
                            newJsonResponse(true, "success")
                        }
                        "/sync/account/douyin" -> {
                            val obj = JSONObject(body)
                            val cookie = obj.optString("cookie")
                            serviceScope.launch {
                                settingsRepository.setDouyinCookie(cookie)
                            }
                            newJsonResponse(true, "success")
                        }
                        else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
                    }
                } else {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
                }
            } catch (e: Exception) {
                CoreLog.e("LanSyncService serve error", e)
                newJsonResponse(false, e.message ?: "Unknown error")
            }
        }

        private fun newJsonResponse(status: Boolean, message: String): Response {
            val res = JSONObject().apply {
                put("status", status)
                put("message", message)
            }
            return newFixedLengthResponse(Response.Status.OK, "application/json", res.toString())
        }
    }
}

data class SyncClient(val id: String, val name: String, val address: String, val port: Int, val type: String)

