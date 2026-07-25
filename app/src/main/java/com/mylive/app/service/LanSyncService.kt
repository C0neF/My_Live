package com.mylive.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.mutableStateListOf
import com.mylive.app.BuildConfig
import com.mylive.app.core.common.CoreLog
import com.mylive.app.core.common.LanSyncAuth
import com.mylive.app.core.common.LanSyncAuthException
import com.mylive.app.core.common.LanSyncCrypto
import com.mylive.app.core.common.LanSyncCryptoException
import com.mylive.app.core.common.LanSyncReplayGuard
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID
import javax.inject.Inject

internal fun newLanSyncToken(): String = UUID.randomUUID().toString().replace("-", "")

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

    /**
     * The pairing code lives for the whole service lifetime, so a signature on its own would stay
     * valid for as long as the server runs. Remembering the nonce of every accepted request makes
     * a captured request single-use.
     */
    private val replayGuard = LanSyncReplayGuard()

    /**
     * A `hello` broadcast makes this device answer with its own broadcast. Since the packet is
     * unauthenticated and trivially spoofed, an attacker could otherwise use the phone as a UDP
     * amplifier by replaying hellos as fast as it can send them.
     */
    @Volatile
    private var lastHelloResponseAt = 0L

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

        /**
         * Discovery is driven by unauthenticated UDP broadcasts, so anyone on the LAN can spoof
         * them. Bound both the number of remembered peers and the length of the name we render,
         * otherwise a broadcast flood grows this list (and the scan UI) without limit.
         */
        private const val MAX_SCAN_CLIENTS = 32
        private const val MAX_SCAN_DEVICE_NAME_CHARS = 48

        /** Answer at most one spoofable `hello` per second so we cannot be used as an amplifier. */
        private const val MIN_HELLO_RESPONSE_INTERVAL_MS = 1_000L

        val scanClients = mutableStateListOf<SyncClient>()
        var isRunning = false
        var ipAddress = ""
        var syncDeviceId = ""

        /**
         * Pairing code. Generated per service start and surfaced ONLY in this device's "My Info"
         * QR (a visual, out-of-band channel).
         *
         * It is the root secret for both the payload key and the request signing key, and it is
         * therefore NEVER transmitted: peers prove possession with a per-request HMAC (see
         * [LanSyncAuth]). Deliberately not returned by /info and not broadcast over UDP either.
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
        replayGuard.clear()
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
        replayGuard.clear()
        // Drop the stretched keys too: the code they came from is gone.
        LanSyncCrypto.clearCachedKeys()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startHttpServer() {
        try {
            // Bind to this device's LAN address instead of every interface. Listening on 0.0.0.0
            // also published the sync endpoints over VPN tunnels and USB/Wi-Fi tethering links the
            // user never intended to share; we only fall back to a wildcard bind when the LAN
            // address could not be determined at all.
            val bindAddress = ipAddress.ifBlank { "0.0.0.0" }
            httpServer = HttpServer(bindAddress, HTTP_PORT)
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
                    val now = System.currentTimeMillis()
                    if (now - lastHelloResponseAt < MIN_HELLO_RESPONSE_INTERVAL_MS) {
                        CoreLog.w("LanSyncService: ignoring hello flood, already announced recently")
                        return
                    }
                    lastHelloResponseAt = now
                    sendInfo()
                } else {
                    val existing = scanClients.find { it.address == senderIp }
                    if (existing == null) {
                        if (scanClients.size >= MAX_SCAN_CLIENTS) {
                            CoreLog.w("LanSyncService: discovery list full, ignoring extra broadcast peer")
                            return
                        }
                        scanClients.add(
                            SyncClient(
                                id = sanitizeBroadcastText(id, "unknown"),
                                name = sanitizeBroadcastText(data.optString("name"), "Android Device"),
                                address = senderIp,
                                port = HTTP_PORT,
                                type = sanitizeBroadcastText(type, "unknown")
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            CoreLog.e("LanSyncService: Parse UDP message failed", e)
        }
    }

    /**
     * Broadcast fields come from an unauthenticated UDP packet, so they are attacker controlled.
     * Strip control characters and clamp the length before we ever store or render them.
     */
    private fun sanitizeBroadcastText(raw: String, fallback: String): String {
        val cleaned = raw.filter { !it.isISOControl() }.trim()
        if (cleaned.isEmpty()) return fallback
        return cleaned.take(MAX_SCAN_DEVICE_NAME_CHARS)
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

    private inner class HttpServer(hostname: String, port: Int) : NanoHTTPD(hostname, port) {
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
                    // Any device on the Wi-Fi can reach this endpoint without pairing, so the
                    // unauthenticated answer carries no identity: the phone model, the install id
                    // and the app version are fingerprinting material that used to be handed to
                    // every port scanner on the network. A peer that can already prove it holds the
                    // pairing code gets the details it needs to render the device.
                    val identified = try {
                        authorizeRequest(session, uri, overlay = false, rawBody = "")
                        true
                    } catch (e: LanSyncAuthException) {
                        false
                    }
                    val res = JSONObject().apply {
                        put("type", "android")
                        put("app", "My Live")
                        put("protocol", "MLSYNC2")
                        put("port", HTTP_PORT)
                        put("paired", identified)
                        if (identified) {
                            put("id", syncDeviceId)
                            put("name", Build.MODEL)
                            put("version", BuildConfig.VERSION_NAME)
                            put("address", ipAddress)
                        }
                    }
                    newFixedLengthResponse(Response.Status.OK, "application/json", res.toString())
                } else if (method == Method.GET && uri.startsWith(SYNC_JOB_PATH_PREFIX)) {
                    // Job state can reveal what was just imported, so polling is signed too. A GET
                    // has no body, which the canonical request models as the digest of "".
                    authorizeRequest(session, uri, overlay = false, rawBody = "")
                    val jobId = uri.removePrefix(SYNC_JOB_PATH_PREFIX)
                    val job = syncJobs[jobId]
                    if (job == null) {
                        newJsonResponse(false, "sync job not found", Response.Status.NOT_FOUND)
                    } else {
                        newFixedLengthResponse(Response.Status.OK, "application/json", job.toJson().toString())
                    }
                } else if (method == Method.POST) {
                    rejectIfBodyTooLarge(session)?.let { return it }

                    val files = mutableMapOf<String, String>()
                    session.parseBody(files)
                    val rawBody = files["postData"] ?: ""
                    if (rawBody.toByteArray(Charsets.UTF_8).size > MAX_SYNC_BODY_BYTES) {
                        return newJsonResponse(false, "payload too large", Response.Status.PAYLOAD_TOO_LARGE)
                    }

                    // `overlay=1` wipes the local collection before importing, so it is far too
                    // destructive to trust an unauthenticated query parameter with. It is read
                    // here but immediately folded into the signed request context below: a
                    // man-in-the-middle who flips it invalidates both the signature and the AEAD
                    // tag, and the request is refused.
                    val overlay = session.parameters["overlay"]?.firstOrNull() == "1"

                    // Every write requires proof that the peer holds the pairing code shown in
                    // this device's My Info QR. The proof covers the method, the path, the overlay
                    // flag and the exact body bytes, and each nonce is accepted once.
                    val requestContext = authorizeRequest(session, uri, overlay, rawBody)

                    // The LAN transport is plain HTTP, so the body itself is sealed with a key
                    // derived from the pairing code, bound to that same request context. Anything
                    // that is not a valid envelope is rejected: personal data must never cross the
                    // Wi-Fi in the clear.
                    val body = openSealedSyncBody(rawBody, requestContext)
                    if (body.toByteArray(Charsets.UTF_8).size > MAX_SYNC_BODY_BYTES) {
                        return newJsonResponse(false, "payload too large", Response.Status.PAYLOAD_TOO_LARGE)
                    }
                    
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
            } catch (e: LanSyncAuthException) {
                CoreLog.w(
                    "LanSyncService: rejected unauthorized sync request to ${safePathForLog(uri)}"
                )
                newJsonResponse(
                    false,
                    e.message ?: "unauthorized: pairing required",
                    Response.Status.UNAUTHORIZED
                )
            } catch (e: LanSyncCryptoException) {
                CoreLog.w("LanSyncService: rejected unsealed or unreadable sync payload")
                newJsonResponse(
                    false,
                    e.message ?: "encrypted payload required",
                    Response.Status.BAD_REQUEST
                )
            } catch (e: org.json.JSONException) {
                // Malformed input is the caller's fault, not ours: answer 400 rather than letting
                // it fall through to the catch-all below, which used to report it as a 200.
                CoreLog.w("LanSyncService: rejected malformed sync payload")
                newJsonResponse(false, "malformed sync payload", Response.Status.BAD_REQUEST)
            } catch (e: Exception) {
                // The exception text is produced by parsers and storage code reacting to attacker
                // input, so it is never echoed back; it only goes to the local log. And a failure
                // must not be reported as HTTP 200 — clients read that as "import accepted".
                CoreLog.e("LanSyncService serve error", e)
                newJsonResponse(false, "sync request failed", Response.Status.INTERNAL_ERROR)
            }
        }

        /**
         * Verifies the caller's proof of possession and returns the canonical request context that
         * the body must have been sealed against.
         *
         * The pairing code is never accepted from the wire in any form. It used to be sent in an
         * `X-Sync-Token` header while also being the key the body was sealed with, which meant a
         * single captured plain-HTTP request handed a sniffer both the ciphertext and its key.
         * Now only a signature travels.
         *
         * @throws LanSyncAuthException on any failure, answered as 401 by [serve].
         */
        private fun authorizeRequest(
            session: IHTTPSession,
            uri: String,
            overlay: Boolean,
            rawBody: String
        ): String = LanSyncAuth.verifyProof(
            token = syncToken,
            headerValue = session.headers[LanSyncAuth.HEADER_KEY],
            method = session.method.name,
            path = uri,
            overlay = overlay,
            body = rawBody,
            replayGuard = replayGuard
        )

        /**
         * Opens a sealed sync body. Plain JSON is refused outright: an unsealed body either came
         * from an attacker who does not hold the pairing key, or from a peer that would have put
         * personal data on the wire in the clear. Either way we do not want it.
         *
         * [requestContext] is the authenticated request context, so a body sealed for another
         * endpoint, another overlay flag or another nonce fails to open.
         */
        private fun openSealedSyncBody(rawBody: String, requestContext: String): String {
            if (!LanSyncCrypto.isEncryptedEnvelope(rawBody)) {
                throw LanSyncCryptoException("sync payload must be encrypted with the pairing key")
            }
            return LanSyncCrypto.decrypt(syncToken, rawBody, requestContext)
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

        /**
         * NanoHTTPD buffers the whole request body while parsing it, so the size has to be settled
         * before [IHTTPSession.parseBody] is ever reached.
         *
         * A request that declares no length, or streams a chunked body, has nothing to check here:
         * that is precisely how an unauthenticated peer would push an unbounded upload into memory
         * and take the app down without ever holding the pairing code. Both are refused up front.
         */
        private fun rejectIfBodyTooLarge(session: IHTTPSession): Response? {
            val transferEncoding = session.headers["transfer-encoding"]?.lowercase()
            if (transferEncoding?.contains("chunked") == true) {
                return newJsonResponse(
                    false,
                    "chunked sync uploads are not accepted",
                    Response.Status.LENGTH_REQUIRED
                )
            }

            val contentLength = session.headers["content-length"]?.toLongOrNull()
                ?: return newJsonResponse(
                    false,
                    "content-length required",
                    Response.Status.LENGTH_REQUIRED
                )
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
