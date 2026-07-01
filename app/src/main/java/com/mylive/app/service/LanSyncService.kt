package com.mylive.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.mutableStateListOf
import com.mylive.app.BuildConfig
import com.mylive.app.core.common.CoreLog
import com.mylive.app.core.common.safePathForLog
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.HistoryRepository
import com.mylive.app.data.repository.ProfileBackupManager
import com.mylive.app.data.repository.ShieldRepository
import com.mylive.app.ui.screen.sync.decodeLanSyncShieldKeywords
import com.mylive.app.ui.screen.sync.decodeFollowTagsForLanSync
import dagger.hilt.android.AndroidEntryPoint
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID
import javax.inject.Inject

internal fun newLanSyncToken(): String = UUID.randomUUID().toString().replace("-", "")

internal fun isValidLanSyncToken(
    expectedToken: String,
    providedToken: String
): Boolean {
    if (expectedToken.isEmpty() || providedToken.isEmpty()) return false
    return MessageDigest.isEqual(
        expectedToken.toByteArray(Charsets.UTF_8),
        providedToken.toByteArray(Charsets.UTF_8)
    )
}

internal fun decodeFollowsForLanSync(array: JSONArray): List<FollowUserEntity> = buildList {
    for (index in 0 until array.length()) {
        val item = array.getJSONObject(index)
        val siteId = item.getString("siteId")
        val roomId = item.getString("roomId")
        val now = System.currentTimeMillis()
        add(
            FollowUserEntity(
                id = item.optString("id", "${siteId}_${roomId}"),
                roomId = roomId,
                siteId = siteId,
                userName = item.getString("userName"),
                face = item.optString("face", item.optString("avatar", "")),
                addTime = item.opt("addTime")?.toString()?.toLongOrNull() ?: now,
                tag = item.optString("tag", ""),
                isSpecialFollow = item.optBoolean("isSpecialFollow", false),
                liveStatus = item.optInt("liveStatus", 0),
                liveStartTime = item.opt("liveStartTime")
                    ?.takeUnless { it == JSONObject.NULL }
                    ?.toString()
                    ?.toLongOrNull(),
                showTime = item.opt("showTime")
                    ?.takeUnless { it == JSONObject.NULL }
                    ?.toString()
            )
        )
    }
}

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

    private var httpServer: HttpServer? = null
    private var udpSocket: DatagramSocket? = null
    private var udpListenThread: Thread? = null
    private val syncImportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncImportSemaphore = Semaphore(1)
    private val pendingSyncJobCount = AtomicInteger(0)
    private val syncJobs = ConcurrentHashMap<String, SyncImportJob>()

    companion object {
        const val UDP_PORT = 23235
        const val HTTP_PORT = 23234
        private const val SYNC_JOB_PATH_PREFIX = "/sync/job/"
        private const val MAX_SYNC_BODY_BYTES = 5 * 1024 * 1024
        private const val MAX_SYNC_FOLLOW_ITEMS = 2_000
        private const val MAX_SYNC_TAG_ITEMS = 1_000
        private const val MAX_SYNC_HISTORY_ITEMS = 5_000
        private const val MAX_SYNC_SHIELD_KEYWORDS = 5_000
        private const val MAX_SYNC_PROFILE_PRESETS = 1_000
        private const val MAX_PENDING_SYNC_JOBS = 4
        private const val MAX_RETAINED_SYNC_JOBS = 32
        private const val SYNC_IMPORT_TIMEOUT_MS = 30_000L

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
        syncToken = newLanSyncToken()
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
        syncImportScope.cancel()
        syncJobs.clear()
        pendingSyncJobCount.set(0)
        scanClients.clear()
        isRunning = false
        syncToken = ""
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
            
            CoreLog.d("LanSyncService: HTTP request: $method ${safePathForLog(uri)}")
            
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
                } else if (method == Method.GET && uri.startsWith(SYNC_JOB_PATH_PREFIX)) {
                    rejectIfUnauthorized(session, uri)?.let { return it }
                    val jobId = uri.removePrefix(SYNC_JOB_PATH_PREFIX)
                    val job = syncJobs[jobId]
                    if (job == null) {
                        newJsonResponse(false, "sync job not found", Response.Status.NOT_FOUND)
                    } else {
                        newFixedLengthResponse(Response.Status.OK, "application/json", job.toJson().toString())
                    }
                } else if (method == Method.POST) {
                    // Every write requires the pairing token (shown only in this device's My Info
                    // QR). Reject otherwise so a same-LAN attacker can't push data or overwrite
                    // account cookies without having visually paired.
                    rejectIfUnauthorized(session, uri)?.let { return it }
                    rejectIfBodyTooLarge(session)?.let { return it }

                    val files = mutableMapOf<String, String>()
                    session.parseBody(files)
                    val body = files["postData"] ?: ""
                    if (body.toByteArray(Charsets.UTF_8).size > MAX_SYNC_BODY_BYTES) {
                        return newJsonResponse(false, "payload too large", Response.Status.PAYLOAD_TOO_LARGE)
                    }
                    val params = session.parameters
                    val overlay = params["overlay"]?.firstOrNull() == "1"
                    
                    when (uri) {
                        "/sync/follow" -> {
                            val arr = requireJsonArrayWithinLimit(body, MAX_SYNC_FOLLOW_ITEMS, "follow")
                            val follows = decodeFollowsForLanSync(arr)
                            enqueueSyncImport("follow") {
                                if (overlay) {
                                    followRepository.clearAllFollows()
                                }
                                followRepository.addFollows(follows)
                            }
                        }
                        "/sync/tag" -> {
                            requireJsonArrayWithinLimit(body, MAX_SYNC_TAG_ITEMS, "tag")
                            val tags = decodeFollowTagsForLanSync(body)
                            enqueueSyncImport("tag") {
                                if (overlay) {
                                    followRepository.clearAllTags()
                                }
                                followRepository.addTags(tags)
                            }
                        }
                        "/sync/history" -> {
                            val arr = requireJsonArrayWithinLimit(body, MAX_SYNC_HISTORY_ITEMS, "history")
                            enqueueSyncImport("history") {
                                if (overlay) {
                                    historyRepository.clearAllHistory()
                                }
                                val histories = buildList {
                                    for (i in 0 until arr.length()) {
                                        val item = arr.getJSONObject(i)
                                        val siteId = item.getString("siteId")
                                        val roomId = item.getString("roomId")
                                        val userName = item.getString("userName")
                                        val face = item.optString("face", item.optString("avatar", ""))
                                        val updateTime = item.optLong("updateTime", System.currentTimeMillis())
                                        add(
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
                                historyRepository.addHistories(histories)
                            }
                        }
                        "/sync/blocked_word" -> {
                            requireJsonArrayWithinLimit(body, MAX_SYNC_SHIELD_KEYWORDS, "blocked_word")
                            val keywords = decodeLanSyncShieldKeywords(body)
                            enqueueSyncImport("blocked_word") {
                                if (overlay) {
                                    shieldRepository.clearAllKeywords()
                                }
                                val shields = keywords.map { kw -> ShieldEntity(value = "keyword:$kw") }
                                shieldRepository.addShields(shields)
                            }
                        }
                        "/sync/profile" -> {
                            validateProfilePayloadLimits(body)
                            enqueueSyncImport("profile") {
                                // Untrusted LAN push: must not be able to redirect our sync/proxy endpoints.
                                profileBackupManager.importProfileJson(body, trusted = false)
                            }
                        }
                        else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
                    }
                } else {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
                }
            } catch (e: PayloadTooLargeException) {
                CoreLog.w("LanSyncService: rejected oversized import: ${e.message}")
                newJsonResponse(false, e.message ?: "payload too large", Response.Status.PAYLOAD_TOO_LARGE)
            } catch (e: Exception) {
                CoreLog.e("LanSyncService serve error", e)
                newJsonResponse(false, e.message ?: "Unknown error")
            }
        }

        private fun newJsonResponse(
            status: Boolean,
            message: String,
            responseStatus: Response.Status = Response.Status.OK
        ): Response {
            val res = JSONObject().apply {
                put("status", status)
                put("message", message)
            }
            return newFixedLengthResponse(responseStatus, "application/json", res.toString())
        }

        private fun rejectIfUnauthorized(session: IHTTPSession, uri: String): Response? {
            val providedToken = session.headers["x-sync-token"]
                ?: session.parameters["token"]?.firstOrNull()
                ?: ""
            if (
                isValidLanSyncToken(
                    expectedToken = syncToken,
                    providedToken = providedToken
                )
            ) return null

            CoreLog.w(
                "LanSyncService: rejected unauthorized sync request to ${safePathForLog(uri)}"
            )
            return newJsonResponse(
                false,
                "unauthorized: pairing required",
                Response.Status.UNAUTHORIZED
            )
        }

        private fun rejectIfBodyTooLarge(session: IHTTPSession): Response? {
            val contentLength = session.headers["content-length"]?.toLongOrNull() ?: return null
            if (contentLength <= MAX_SYNC_BODY_BYTES) return null

            return newJsonResponse(false, "payload too large", Response.Status.PAYLOAD_TOO_LARGE)
        }

        private fun requireJsonArrayWithinLimit(body: String, maxItems: Int, label: String): JSONArray {
            val array = JSONArray(body)
            array.requireLengthAtMost(maxItems, label)
            return array
        }

        private fun JSONArray.requireLengthAtMost(maxItems: Int, label: String) {
            if (length() > maxItems) {
                throw PayloadTooLargeException("$label exceeds limit: ${length()} > $maxItems")
            }
        }

        private fun validateProfilePayloadLimits(body: String) {
            val trimmed = body.trim()
            if (trimmed.startsWith("[")) {
                requireJsonArrayWithinLimit(trimmed, MAX_SYNC_FOLLOW_ITEMS, "profile followUsers")
                return
            }

            val root = JSONObject(trimmed)
            root.optJSONArray("danmuShield")?.requireLengthAtMost(MAX_SYNC_SHIELD_KEYWORDS, "profile danmuShield")
            root.optJSONArray("shieldPresets")?.requireLengthAtMost(MAX_SYNC_PROFILE_PRESETS, "profile shieldPresets")
            root.optJSONArray("followUsers")?.requireLengthAtMost(MAX_SYNC_FOLLOW_ITEMS, "profile followUsers")
            root.optJSONArray("followUserTags")?.requireLengthAtMost(MAX_SYNC_TAG_ITEMS, "profile followUserTags")
            root.optJSONArray("histories")?.requireLengthAtMost(MAX_SYNC_HISTORY_ITEMS, "profile histories")

            val boxes = root.optJSONObject("boxes") ?: return
            boxes.optJSONArray("danmuShield")?.requireLengthAtMost(MAX_SYNC_SHIELD_KEYWORDS, "profile boxes.danmuShield")
            boxes.optJSONArray("danmuShieldPreset")
                ?.requireLengthAtMost(MAX_SYNC_PROFILE_PRESETS, "profile boxes.danmuShieldPreset")
            boxes.optJSONArray("followUsers")?.requireLengthAtMost(MAX_SYNC_FOLLOW_ITEMS, "profile boxes.followUsers")
            boxes.optJSONArray("followUserTags")?.requireLengthAtMost(MAX_SYNC_TAG_ITEMS, "profile boxes.followUserTags")
            boxes.optJSONArray("histories")?.requireLengthAtMost(MAX_SYNC_HISTORY_ITEMS, "profile boxes.histories")
        }

        private fun enqueueSyncImport(type: String, importBlock: suspend () -> Unit): Response {
            val pendingJobs = pendingSyncJobCount.incrementAndGet()
            if (pendingJobs > MAX_PENDING_SYNC_JOBS) {
                pendingSyncJobCount.decrementAndGet()
                return newJsonResponse(false, "too many sync imports", Response.Status.TOO_MANY_REQUESTS)
            }

            val job = SyncImportJob(
                id = UUID.randomUUID().toString(),
                type = type,
                createdAt = System.currentTimeMillis()
            )
            syncJobs[job.id] = job
            pruneRetainedSyncJobs()

            syncImportScope.launch {
                try {
                    syncImportSemaphore.withPermit {
                        job.markRunning()
                        withTimeout(SYNC_IMPORT_TIMEOUT_MS) {
                            importBlock()
                        }
                    }
                    job.markSucceeded()
                } catch (e: Exception) {
                    job.markFailed(e.message ?: "sync failed")
                    CoreLog.e("LanSyncService: sync import job failed: $type", e)
                } finally {
                    pendingSyncJobCount.decrementAndGet()
                    pruneRetainedSyncJobs()
                }
            }

            return newAcceptedJobResponse(job)
        }

        private fun newAcceptedJobResponse(job: SyncImportJob): Response {
            val res = JSONObject().apply {
                put("status", true)
                put("message", "accepted")
                put("jobId", job.id)
                put("jobUrl", "$SYNC_JOB_PATH_PREFIX${job.id}")
                put("state", job.state)
            }
            return newFixedLengthResponse(Response.Status.ACCEPTED, "application/json", res.toString())
        }

        private fun pruneRetainedSyncJobs() {
            val overflow = syncJobs.size - MAX_RETAINED_SYNC_JOBS
            if (overflow <= 0) return

            syncJobs.entries
                .filter { it.value.isFinished }
                .sortedBy { it.value.finishedAt ?: it.value.createdAt }
                .take(overflow)
                .forEach { syncJobs.remove(it.key, it.value) }
        }
    }

    private class PayloadTooLargeException(message: String) : IllegalArgumentException(message)

    private class SyncImportJob(
        val id: String,
        val type: String,
        val createdAt: Long
    ) {
        @Volatile
        var state: String = "queued"
            private set

        @Volatile
        var message: String = "queued"
            private set

        @Volatile
        var finishedAt: Long? = null
            private set

        val isFinished: Boolean
            get() = state == "succeeded" || state == "failed"

        fun markRunning() {
            message = "running"
            state = "running"
        }

        fun markSucceeded() {
            message = "success"
            finishedAt = System.currentTimeMillis()
            state = "succeeded"
        }

        fun markFailed(errorMessage: String) {
            message = errorMessage
            finishedAt = System.currentTimeMillis()
            state = "failed"
        }

        fun toJson(): JSONObject {
            val currentState = state
            val currentMessage = message
            val currentFinishedAt = finishedAt
            return JSONObject().apply {
                put("status", currentState != "failed")
                put("message", currentMessage)
                put("jobId", id)
                put("type", type)
                put("state", currentState)
                put("createdAt", createdAt)
                currentFinishedAt?.let { put("finishedAt", it) }
            }
        }
    }
}

data class SyncClient(val id: String, val name: String, val address: String, val port: Int, val type: String)
