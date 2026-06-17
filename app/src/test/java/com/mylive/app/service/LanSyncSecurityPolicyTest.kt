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
    fun lanSyncWriteEndpointsDoNotAcknowledgeBeforeRepositoryWritesFinish() {
        val source = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()
        val postBlock = source.substringAfter("} else if (method == Method.POST) {")
            .substringBefore("} else {")

        assertFalse(
            "LAN sync write endpoints must return success only after repository writes complete",
            postBlock.contains("serviceScope.launch")
        )
        assertTrue(postBlock.contains("runBlocking"))
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
}
