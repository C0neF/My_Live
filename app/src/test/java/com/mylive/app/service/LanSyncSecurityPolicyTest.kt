package com.mylive.app.service

import com.mylive.app.core.common.LanSyncAuth
import com.mylive.app.core.common.LanSyncAuthException
import com.mylive.app.core.common.LanSyncReplayGuard
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanSyncSecurityPolicyTest {

    @Test
    fun lanPairingTokenIsNotInterpolatedIntoLogs() {
        val source = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()

        assertFalse(
            "LAN pairing token authorizes write endpoints and must not be written to debug logs",
            source.contains("${'$'}syncToken")
        )
    }

    /**
     * The pairing code is the key the body is sealed with. Sending it alongside the ciphertext over
     * plain HTTP handed a Wi-Fi sniffer both halves at once, so it must not leave either device in
     * any form: header, query string or body.
     */
    @Test
    fun thePairingCodeIsNeverPutOnTheWireByEitherSide() {
        val receiveSource = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()
        val sendSource = File("src/main/java/com/mylive/app/ui/screen/sync/SyncDeviceScreen.kt").readText()
        val authBlock = receiveSource.substringAfter("private fun authorizeRequest(")
            .substringBefore("private fun openSealedSyncBody(")

        assertTrue(authBlock.contains("LanSyncAuth.verifyProof("))
        assertTrue(authBlock.contains("session.headers[LanSyncAuth.HEADER_KEY]"))
        assertTrue("每个请求都必须一次性核销 nonce", authBlock.contains("replayGuard = replayGuard"))

        assertFalse(
            "the server must no longer accept the raw pairing code from a header",
            receiveSource.contains("session.headers[\"x-sync-token\"]")
        )
        assertFalse(
            "a pairing code read from the query string leaks into request logs and screenshots",
            receiveSource.contains("parameters[\"token\"]")
        )
        assertFalse(
            "the client must send a proof of possession, never the pairing code itself",
            sendSource.contains("X-Sync-Token")
        )
        assertTrue(sendSource.contains("LanSyncAuth.HEADER_NAME, proof.headerValue"))
    }

    /** The destructive `?overlay=1` flag must be authenticated before it can wipe anything. */
    @Test
    fun theOverlayFlagIsAuthenticatedBeforeAnyDataIsCleared() {
        val receiveSource = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()
        val postBlock = receiveSource.substringAfter("} else if (method == Method.POST) {")
            .substringBefore("} else {")

        val overlayRead = postBlock.indexOf("val overlay = session.parameters[\"overlay\"]")
        val authorize = postBlock.indexOf("authorizeRequest(session, uri, overlay, rawBody)")
        val decrypt = postBlock.indexOf("openSealedSyncBody(rawBody, requestContext)")
        val routing = postBlock.indexOf("\"/sync/follow\" -> {")

        assertTrue("overlay must be parsed before it is authenticated", overlayRead in 0 until authorize)
        assertTrue("the request must be verified before the body is opened", authorize < decrypt)
        assertTrue("nothing may be imported before verification", decrypt < routing)
    }

    @Test
    fun broadcastDiscoveryIsBoundedAndSanitized() {
        val source = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()
        val udpBlock = source.substringAfter("private fun handleUdpMessage(")
            .substringBefore("private fun sanitizeBroadcastText(")

        assertTrue(source.contains("MAX_SCAN_CLIENTS"))
        assertTrue(source.contains("MAX_SCAN_DEVICE_NAME_CHARS"))
        assertTrue(
            "Spoofed UDP broadcasts must not grow the discovery list without limit",
            udpBlock.contains("scanClients.size >= MAX_SCAN_CLIENTS")
        )
        assertTrue(udpBlock.contains("sanitizeBroadcastText(data.optString(\"name\")"))
        assertFalse(
            "Broadcast-supplied names must be sanitized before they reach the scan UI",
            udpBlock.contains("name.ifEmpty")
        )
    }

    @Test
    fun broadcastTextSanitizerStripsControlCharsAndClampsLength() {
        val source = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()
        val sanitizer = source.substringAfter("private fun sanitizeBroadcastText(")
            .substringBefore("private fun getLocalIpAddress(")

        assertTrue(sanitizer.contains("isISOControl()"))
        assertTrue(sanitizer.contains("take(MAX_SCAN_DEVICE_NAME_CHARS)"))
        assertTrue(sanitizer.contains("return fallback"))
    }

    @Test
    fun lanSyncWriteEndpointsRunAsBoundedBackgroundImports() {
        val source = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()
        val postBlock = source.substringAfter("} else if (method == Method.POST) {")
            .substringBefore("} else {")

        assertFalse(
            "LAN sync write endpoints must not block NanoHTTPD request threads with runBlocking",
            source.contains("import kotlinx.coroutines.runBlocking") || postBlock.contains("runBlocking")
        )
        assertTrue(source.contains("CoroutineScope(SupervisorJob() + Dispatchers.IO)"))
        assertTrue(source.contains("syncImportSemaphore.withPermit"))
        assertTrue(source.contains("pendingSyncJobCount"))
        assertTrue(source.contains("Response.Status.ACCEPTED"))
        assertTrue(source.contains("\"/sync/job/\""))
    }

    @Test
    fun lanSyncPersistsCollectionsWithBatchDaoOperations() {
        val source = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()

        assertTrue(source.contains("followRepository.addFollows(follows)"))
        assertTrue(source.contains("followRepository.addTags(tags)"))
        assertTrue(source.contains("historyRepository.addHistories(histories)"))
        assertTrue(source.contains("shieldRepository.addShields(shields)"))
    }

    @Test
    fun lanSyncWriteEndpointsRejectOversizedImportsBeforeScheduling() {
        val source = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()

        assertTrue(source.contains("MAX_SYNC_BODY_BYTES"))
        assertTrue(source.contains("MAX_SYNC_FOLLOW_ITEMS"))
        assertTrue(source.contains("MAX_SYNC_TAG_ITEMS"))
        assertTrue(source.contains("MAX_SYNC_HISTORY_ITEMS"))
        assertTrue(source.contains("MAX_SYNC_SHIELD_KEYWORDS"))
        assertTrue(source.contains("rejectIfBodyTooLarge(session)"))
        assertTrue(source.contains("requireJsonArrayWithinLimit(body, MAX_SYNC_FOLLOW_ITEMS, \"follow\")"))
        assertTrue(source.contains("requireJsonArrayWithinLimit(body, MAX_SYNC_TAG_ITEMS, \"tag\")"))
        assertTrue(source.contains("requireJsonArrayWithinLimit(body, MAX_SYNC_HISTORY_ITEMS, \"history\")"))
        assertTrue(source.contains("requireJsonArrayWithinLimit(body, MAX_SYNC_SHIELD_KEYWORDS, \"blocked_word\")"))
        assertTrue(source.contains("validateProfilePayloadLimits(body)"))
    }

    @Test
    fun lanSyncOverlayClearsExistingCollectionsBeforeImporting() {
        val source = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()
        val followBlock = source.substringAfter("\"/sync/follow\" -> {")
            .substringBefore("\"/sync/tag\" -> {")
        val tagBlock = source.substringAfter("\"/sync/tag\" -> {")
            .substringBefore("\"/sync/history\" -> {")
        val historyBlock = source.substringAfter("\"/sync/history\" -> {")
            .substringBefore("\"/sync/blocked_word\" -> {")

        assertTrue(followBlock.contains("if (overlay)"))
        assertTrue(followBlock.contains("followRepository.clearAllFollows()"))
        assertTrue(tagBlock.contains("if (overlay)"))
        assertTrue(tagBlock.contains("followRepository.clearAllTags()"))
        assertTrue(historyBlock.contains("if (overlay)"))
        assertTrue(historyBlock.contains("historyRepository.clearAllHistory()"))
    }

    @Test
    fun lanSyncDoesNotSendOrReceiveAccountCookiesOverCleartextHttp() {
        val sendSource = File(
            "src/main/java/com/mylive/app/ui/screen/sync/SyncDeviceScreen.kt"
        ).readText()
        val receiveSource = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()

        assertFalse(sendSource.contains("/sync/account/bilibili"))
        assertFalse(sendSource.contains("/sync/account/douyin"))
        assertFalse(sendSource.contains("put(\"cookie\", cookie)"))
        assertFalse(receiveSource.contains("/sync/account/bilibili"))
        assertFalse(receiveSource.contains("/sync/account/douyin"))
    }

    /**
     * Fails closed in the states the service actually reaches: before pairing, after the service
     * stops (the code is cleared), and for any caller that simply omits the proof.
     */
    @Test
    fun lanSyncAuthorizationFailsClosedWithoutAValidProof() {
        val pairingCode = "7f4c1b2d9a0e4f5b8c3d2e1f0a9b8c7d"
        val proof = LanSyncAuth.createProof(
            token = pairingCode,
            method = "POST",
            path = "/sync/follow",
            overlay = false,
            body = "sealed"
        )

        assertRejected("a request without a proof must be refused") {
            LanSyncAuth.verifyProof(
                token = pairingCode,
                headerValue = null,
                method = "POST",
                path = "/sync/follow",
                overlay = false,
                body = "sealed"
            )
        }
        assertRejected("a stopped service holds no pairing code and must refuse everything") {
            LanSyncAuth.verifyProof(
                token = "",
                headerValue = proof.headerValue,
                method = "POST",
                path = "/sync/follow",
                overlay = false,
                body = "sealed"
            )
        }

        // The happy path still works, so the endpoint is not simply bricked.
        assertEquals(
            proof.requestContext,
            LanSyncAuth.verifyProof(
                token = pairingCode,
                headerValue = proof.headerValue,
                method = "POST",
                path = "/sync/follow",
                overlay = false,
                body = "sealed",
                replayGuard = LanSyncReplayGuard()
            )
        )
    }

    @Test
    fun lanSyncRotatesAndClearsItsPairingTokenWithTheServiceLifecycle() {
        val source = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()
        val createBlock = source.substringAfter("override fun onCreate()")
            .substringBefore("override fun onStartCommand")
        val destroyBlock = source.substringAfter("override fun onDestroy()")
            .substringBefore("override fun onBind")

        assertTrue(createBlock.contains("syncToken = newLanSyncToken()"))
        assertFalse(createBlock.contains("if (syncToken.isEmpty())"))
        assertTrue(
            "a new pairing code must not inherit the previous run's accepted nonces",
            createBlock.contains("replayGuard.clear()")
        )
        assertTrue(destroyBlock.contains("syncToken = \"\""))
        assertTrue(destroyBlock.contains("replayGuard.clear()"))
        assertTrue(
            "keys stretched from the old pairing code must not outlive it",
            destroyBlock.contains("LanSyncCrypto.clearCachedKeys()")
        )
    }

    @Test
    fun lanSyncBodiesAreSealedBeforeTheyLeaveTheDeviceAndUnsealedBodiesAreRejected() {
        val sendSource = File("src/main/java/com/mylive/app/ui/screen/sync/SyncDeviceScreen.kt").readText()
        val receiveSource = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()

        // Sender seals every payload against the signed request context and refuses to send while
        // unpaired.
        assertTrue(sendSource.contains("LanSyncCrypto.encrypt(token, json, requestContext)"))
        assertTrue(sendSource.contains("sealed.toRequestBody"))
        assertFalse(sendSource.contains("json.toRequestBody"))

        // Receiver only accepts sealed envelopes bound to the verified context, and reports a 400
        // for anything else.
        assertTrue(receiveSource.contains("val body = openSealedSyncBody(rawBody, requestContext)"))
        assertTrue(receiveSource.contains("LanSyncCrypto.isEncryptedEnvelope(rawBody)"))
        assertTrue(receiveSource.contains("LanSyncCrypto.decrypt(syncToken, rawBody, requestContext)"))
        assertTrue(receiveSource.contains("catch (e: LanSyncCryptoException)"))
        assertTrue(receiveSource.contains("catch (e: LanSyncAuthException)"))
        assertFalse(receiveSource.contains("val body = files[\"postData\"]"))
    }

    @Test
    fun lanFollowImportPreservesExportedMetadata() {
        val follows = decodeFollowsForLanSync(
            JSONArray(
                """[{"id":"bilibili_300","siteId":"bilibili","roomId":"300","userName":"streamer","face":"avatar","addTime":"123","tag":"favorite","isSpecialFollow":true,"liveStatus":1,"liveStartTime":456,"showTime":"789"}]"""
            )
        )

        val follow = follows.single()
        assertEquals("bilibili_300", follow.id)
        assertEquals(123L, follow.addTime)
        assertEquals(1, follow.liveStatus)
        assertEquals(456L, follow.liveStartTime)
        assertEquals("789", follow.showTime)
    }

    /**
     * `/info` answers every device on the Wi-Fi, paired or not. The unauthenticated reply must be
     * enough to recognise a My Live sync endpoint and nothing more: the phone model, install id and
     * app version are fingerprinting material.
     */
    @Test
    fun theUnauthenticatedInfoEndpointDoesNotFingerprintTheDevice() {
        val source = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()
        val infoBlock = source.substringAfter("uri == \"/info\"")
            .substringBefore("uri.startsWith(SYNC_JOB_PATH_PREFIX)")

        val gate = infoBlock.indexOf("if (identified) {")
        assertTrue("identity must sit behind a proof of possession", gate > 0)
        assertTrue(infoBlock.contains("authorizeRequest(session, uri, overlay = false, rawBody = \"\")"))
        assertTrue(
            "the phone model must only be answered to a paired peer",
            infoBlock.indexOf("put(\"name\", Build.MODEL)") > gate
        )
        assertTrue(
            "the install id identifies this device across scans",
            infoBlock.indexOf("put(\"id\", syncDeviceId)") > gate
        )
        assertTrue(
            "the app version tells an attacker which bugs this build still has",
            infoBlock.indexOf("put(\"version\", BuildConfig.VERSION_NAME)") > gate
        )
    }

    /**
     * NanoHTTPD buffers the request body as it parses it, so a body whose length is never declared
     * is an unauthenticated way to exhaust memory.
     */
    @Test
    fun unboundedRequestBodiesAreRefusedBeforeTheyAreBuffered() {
        val source = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()
        val postBlock = source.substringAfter("} else if (method == Method.POST) {")
            .substringBefore("} else {")
        val guard = source.substringAfter("private fun rejectIfBodyTooLarge(")
            .substringBefore("private fun requireJsonArrayWithinLimit(")

        val sizeCheck = postBlock.indexOf("rejectIfBodyTooLarge(session)")
        val parse = postBlock.indexOf("session.parseBody(files)")
        assertTrue("the size guard must run before the body is buffered", sizeCheck in 0 until parse)

        assertTrue(
            "a request that declares no length cannot be bounded and must be refused",
            guard.contains("content-length required")
        )
        assertTrue("a chunked upload has no declared length either", guard.contains("chunked"))
        assertTrue(guard.contains("Response.Status.LENGTH_REQUIRED"))
    }

    /** A spoofed `hello` makes us broadcast, so answering every one of them is an amplifier. */
    @Test
    fun spoofedHelloBroadcastsCannotTurnTheDeviceIntoAnAmplifier() {
        val source = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()
        val udpBlock = source.substringAfter("private fun handleUdpMessage(")
            .substringBefore("private fun sanitizeBroadcastText(")

        assertTrue(source.contains("private const val MIN_HELLO_RESPONSE_INTERVAL_MS"))
        assertTrue(
            "a hello flood must not produce one broadcast per packet",
            udpBlock.contains("MIN_HELLO_RESPONSE_INTERVAL_MS")
        )
        assertTrue(udpBlock.contains("lastHelloResponseAt = now"))
    }

    /** The sync endpoints belong on the LAN, not on every interface the phone happens to have. */
    @Test
    fun theSyncServerListensOnTheLanAddressOnly() {
        val source = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()
        val startBlock = source.substringAfter("private fun startHttpServer()")
            .substringBefore("private fun startUdpListener(")

        assertTrue(startBlock.contains("HttpServer(bindAddress, HTTP_PORT)"))
        assertTrue(
            "a wildcard bind also exposes sync over VPN and tethering links",
            startBlock.contains("ipAddress.ifBlank")
        )
        assertTrue(source.contains("NanoHTTPD(hostname, port)"))
    }

    /**
     * The catch-all used to answer any unexpected failure with HTTP 200 and the raw exception
     * text. That both leaked internal structure to an unauthenticated caller and made the peer
     * record a failed import as a successful one.
     */
    @Test
    fun unexpectedFailuresAreNeitherEchoedBackNorReportedAsSuccess() {
        val source = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()
        // Anchor on serve()'s catch chain; other catch-alls elsewhere in the file are unrelated.
        val serveCatchChain = source.substringAfter("} catch (e: LanSyncCryptoException) {")
        val catchAll = serveCatchChain.substringAfter("} catch (e: Exception) {")
            .substringBefore("newJsonResponse(")
        val catchAllResponse = serveCatchChain.substringAfter("} catch (e: Exception) {")
            .substringAfter("newJsonResponse(")
            .substringBefore(")")

        assertFalse(
            "exception text is attacker-influenced and must not be echoed to the caller",
            catchAllResponse.contains("e.message")
        )
        assertTrue(
            "an internal failure must not be answered with the default HTTP 200",
            catchAllResponse.contains("Response.Status.INTERNAL_ERROR")
        )
        assertTrue(catchAll.contains("CoreLog.e("))
    }

    /** Malformed JSON is a client error and must be answered as such, not as a server failure. */
    @Test
    fun malformedJsonPayloadsAreAnsweredAsBadRequests() {
        val source = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()
        val jsonCatch = source.substringAfter("} catch (e: org.json.JSONException) {")
            .substringBefore("} catch (e: Exception) {")

        assertTrue(jsonCatch.contains("Response.Status.BAD_REQUEST"))
        assertTrue(jsonCatch.contains("malformed sync payload"))
        assertFalse(jsonCatch.contains("e.message"))
    }

    private fun assertRejected(message: String, block: () -> Unit) {
        try {
            block()
            fail(message)
        } catch (expected: LanSyncAuthException) {
            // Expected.
        }
    }
}
