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

    @Test
    fun remoteCollectionReceiversUseBatchRepositoryOperations() {
        val source = File(
            "src/main/java/com/mylive/app/ui/screen/sync/RemoteSyncRoomViewModel.kt"
        ).readText()
        val historyBlock = source.substringAfter("remoteSyncService.onHistoryReceived")
            .substringBefore("remoteSyncService.onShieldWordReceived")
        val shieldBlock = source.substringAfter("remoteSyncService.onShieldWordReceived")
            .substringBefore("remoteSyncService.onBiliAccountReceived")

        assertTrue(historyBlock.contains("val existingHistoriesById = historyRepository.getAllHistory().first().associateBy { it.id }"))
        assertTrue(historyBlock.contains("historyRepository.addHistories(histories)"))
        assertFalse(historyBlock.contains("historyRepository.getHistoryById("))
        assertFalse(historyBlock.contains("historyRepository.addHistory("))
        assertTrue(shieldBlock.contains("shieldRepository.addShields(shields)"))
        assertFalse(shieldBlock.contains("shieldRepository.addShield("))
    }

    @Test
    fun remoteAccountCookieSyncRequiresExplicitConfirmationDialog() {
        val source = File(
            "src/main/java/com/mylive/app/ui/screen/sync/RemoteSyncRoomScreen.kt"
        ).readText()

        assertTrue(source.contains("var showAccountDialog by remember { mutableStateOf<AccountSyncType?>(null) }"))
        assertTrue(source.contains("onClick = { showAccountDialog = AccountSyncType.BILIBILI }"))
        assertTrue(source.contains("onClick = { showAccountDialog = AccountSyncType.DOUYIN }"))
        assertTrue(source.contains("确定要发送"))
        assertTrue(source.contains("AccountSyncType.BILIBILI -> viewModel.syncBiliAccount()"))
        assertTrue(source.contains("AccountSyncType.DOUYIN -> viewModel.syncDouyinAccount()"))
        assertFalse(source.contains("onClick = { viewModel.syncBiliAccount() }"))
        assertFalse(source.contains("onClick = { viewModel.syncDouyinAccount() }"))
    }
}
