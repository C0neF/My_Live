package com.mylive.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LanSyncSecurityPolicyTest {

    @Test
    fun lanPairingTokenIsNotInterpolatedIntoLogs() {
        val source = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()

        assertFalse(
            "LAN pairing token authorizes write endpoints and must not be written to debug logs",
            source.contains("${'$'}syncToken")
        )
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
}
