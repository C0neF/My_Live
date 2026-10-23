package com.mylive.app.data

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.core.common.MAX_IMPORTED_JSON_BYTES
import com.mylive.app.core.common.readUtf8TextWithinLimit
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.local.secure.AndroidKeystoreCredentialCipher
import com.mylive.app.data.local.secure.SensitiveCredentialStore
import com.mylive.app.data.repository.ProfileBackupManager
import com.mylive.app.testing.ReviewRoomFixture
import com.mylive.app.ui.screen.sync.ProfileBackupViewModel
import com.mylive.app.ui.screen.sync.ProfileMessage
import com.mylive.app.ui.screen.sync.WebDavMessage
import com.mylive.app.ui.screen.sync.WebDavViewModel
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Uses the production Manager, ViewModels, DataStore and Room, including the restore input reader.
 * One legal long history name keeps the fixture small in row count. Normal use reaches the same
 * aggregate limit by accumulating many history rows; neither path has a history retention limit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileBackupRestoreLimitTest : ReviewRoomFixture() {
    private lateinit var store: SettingsDataStore
    private lateinit var manager: ProfileBackupManager

    @Before
    fun setUpBackup() = runBlocking {
        store = SettingsDataStore(ApplicationProvider.getApplicationContext<Context>())
        store.clearAll()
        manager = ProfileBackupManager(store, follows, history, shields, database)
    }

    @After
    fun clearSettings() = runBlocking { store.clearAll() }

    @Test
    fun oversizedAsciiBackupIsRejectedBeforeTheFileExportCallback() = runBlocking {
        assertRejectedBeforeFileCallback("a".repeat(MAX_IMPORTED_JSON_BYTES))
    }

    @Test
    fun fileExportLimitCountsUtf8BytesInsteadOfStringCharacters() = runBlocking {
        val name = oversizedUtf8Name()
        assertTrue(name.length < MAX_IMPORTED_JSON_BYTES)
        assertTrue(name.toByteArray(Charsets.UTF_8).size > MAX_IMPORTED_JSON_BYTES)

        assertRejectedBeforeFileCallback(name)
    }

    @Test
    fun oversizedBackupNeverPutsOrReplacesThePreviousWebDavBackup() = runBlocking {
        val previousBackup = manager.exportProfileJson()
        history.addHistory(historyRow(oversizedUtf8Name()))
        val remoteBackup = AtomicReference(previousBackup)
        withBackupServer(remoteBackup) { server, client ->
            store.setValue(SettingsDataStore.kWebDAVLastUploadTime, "previous-upload")
            val viewModel = newWebDavViewModel(client)

            val message = backup(viewModel)

            assertEquals("An export that the restore reader rejects must not reach HTTP PUT", 0, server.requestCount)
            assertEquals(previousBackup, remoteBackup.get())
            assertTrue("The UI must report a failed backup", message is WebDavMessage.BackupFailed)
            assertEquals("previous-upload", store.getFlow(SettingsDataStore.kWebDAVLastUploadTime, "").first())
            assertFalse(viewModel.uiState.value.isLoading)
        }
    }

    @Test
    fun restorableUtf8BackupReachesBothOutputsAndRoundTripsThroughTheBoundedReader() = runBlocking {
        // Exercise the successful side close to the byte limit, leaving room for JSON metadata.
        val original = historyRow("界".repeat((MAX_IMPORTED_JSON_BYTES - 64 * 1024) / 3))
        history.addHistory(original)
        val localViewModel = own(ProfileBackupViewModel(manager))
        var localBackup: String? = null

        runAction(localViewModel) { localViewModel.exportProfile { localBackup = it } }

        assertEquals(ProfileMessage.ExportOk, localViewModel.uiState.value.message)
        val fromFile = requireNotNull(localBackup)
        assertTrue(fromFile.toByteArray(Charsets.UTF_8).size <= MAX_IMPORTED_JSON_BYTES)
        assertEquals(fromFile, fromFile.byteInputStream(Charsets.UTF_8).readUtf8TextWithinLimit())
        history.clearAllHistory()
        manager.importProfileJson(fromFile.byteInputStream(Charsets.UTF_8).readUtf8TextWithinLimit())
        assertEquals(original, history.getAllHistory().first().single())

        val remoteBackup = AtomicReference("old")
        withBackupServer(remoteBackup) { server, client ->
            val webDavViewModel = newWebDavViewModel(client)

            assertEquals(WebDavMessage.BackupOk, backup(webDavViewModel))

            assertEquals(1, server.requestCount)
            val put = server.takeRequest()
            assertEquals("PUT", put.method)
            assertEquals("/backups/mylive_profile_backup.json", put.path)
            val fromWebDav = remoteBackup.get().byteInputStream(Charsets.UTF_8).readUtf8TextWithinLimit()
            history.clearAllHistory()
            manager.importProfileJson(fromWebDav)
            assertEquals(original, history.getAllHistory().first().single())
        }
    }

    private suspend fun assertRejectedBeforeFileCallback(historyName: String) {
        val previousBackup = manager.exportProfileJson()
        history.addHistory(historyRow(historyName))
        val viewModel = own(ProfileBackupViewModel(manager))
        var callbackCount = 0
        var savedFile = previousBackup

        runAction(viewModel) {
            viewModel.exportProfile { callbackCount++; savedFile = it }
        }

        assertEquals("Unrestorable output must be rejected before a file can be overwritten", 0, callbackCount)
        assertEquals(previousBackup, savedFile)
        assertTrue(viewModel.uiState.value.message is ProfileMessage.ExportFailed)
        assertFalse(viewModel.uiState.value.isExporting)
        assertEquals(historyName, history.getAllHistory().first().single().userName)
    }

    private suspend fun newWebDavViewModel(client: OkHttpClient): WebDavViewModel {
        store.setValue(SettingsDataStore.WebDAVUri, "https://backup.example.test/backups/")
        val credentials = SensitiveCredentialStore(store, AndroidKeystoreCredentialCipher())
        return own(WebDavViewModel(store, credentials, manager, client)).also { viewModel ->
            withTimeout(10_000) { viewModel.uiState.first { it.serverUrl == "https://backup.example.test/backups/" } }
        }
    }

    private suspend fun backup(viewModel: WebDavViewModel): WebDavMessage = coroutineScope {
        val message = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(20_000) { viewModel.message.filterNotNull().first() }
        }
        viewModel.backup()
        message.await()
    }

    private suspend fun runAction(viewModel: ViewModel, action: () -> Unit) {
        val parent = requireNotNull(viewModel.viewModelScope.coroutineContext[Job])
        val before = parent.children.toSet()
        action()
        val actions = parent.children.filterNot { it in before }.toList()
        withTimeout(20_000) { actions.joinAll() }
        actions.forEach { assertFalse("The export action must finish without cancellation", it.isCancelled) }
    }

    private suspend fun withBackupServer(
        remoteBackup: AtomicReference<String>,
        test: suspend (MockWebServer, OkHttpClient) -> Unit
    ) {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = if (request.method == "PUT") {
                remoteBackup.set(request.body.readUtf8())
                MockResponse().setResponseCode(204)
            } else MockResponse().setResponseCode(500)
        }
        server.start()
        // The UI validates the configured HTTPS URL; only this test transport is redirected locally.
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val localUrl = server.url(chain.request().url.encodedPath)
            chain.proceed(chain.request().newBuilder().url(localUrl).build())
        }.build()
        try {
            test(server, client)
        } finally {
            server.shutdown()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun oversizedUtf8Name() = "界".repeat(MAX_IMPORTED_JSON_BYTES / 3 + 1)

    private fun historyRow(name: String) = HistoryEntity(
        id = "huya_history", roomId = "history", siteId = "huya", userName = name, face = "", updateTime = 123L
    )
}
