package com.mylive.app.ui.screen.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RemoteSyncFavoritePolicyTest {

    @Test
    fun remoteFavoriteSyncUsesRepositoryJsonRoundTripSoTagsArePreserved() {
        val source = File(
            "src/main/java/com/mylive/app/ui/screen/sync/RemoteSyncRoomViewModel.kt"
        ).readText()
        val receiveBlock = source.substringAfter("remoteSyncService.onFavoriteReceived")
            .substringBefore("remoteSyncService.onHistoryReceived")
        val sendBlock = source.substringAfter("fun syncFollow(")
            .substringBefore("fun syncHistory(")

        assertTrue(receiveBlock.contains("followRepository.importFromJson(content, overlay)"))
        assertFalse(receiveBlock.contains("JSONArray(content)"))
        assertTrue(sendBlock.contains("val json = followRepository.exportToJson()"))
        assertTrue(sendBlock.contains("content = json"))
        assertFalse(sendBlock.contains("JSONArray().apply"))
    }

    @Test
    fun remoteShieldSyncSendsOnlyKeywordPayloads() {
        val source = File(
            "src/main/java/com/mylive/app/ui/screen/sync/RemoteSyncRoomViewModel.kt"
        ).readText()
        val sendBlock = source.substringAfter("fun syncBlockedWord(")
            .substringBefore("fun syncBiliAccount(")

        assertTrue(sendBlock.contains("encodeShieldKeywordsForLanSync(shields)"))
        assertFalse(sendBlock.contains("put(sh.value)"))
    }
}
