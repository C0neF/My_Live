package com.mylive.app.ui.screen.settings

import androidx.lifecycle.ViewModelStore
import com.mylive.app.update.AppUpdateInfo
import com.mylive.app.update.AppUpdateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateRequestConcurrencyTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<AppUpdateRepository>()
    private val store = ViewModelStore()
    private lateinit var model: AppUpdateViewModel
    private val apk = File("controlled-update.apk")
    private val update = AppUpdateInfo(
        tagName = "v2.99.0",
        versionName = "2.99.0",
        releaseName = "Controlled update fixture",
        releaseNotes = "",
        releasePageUrl = "https://example.invalid/update",
        apkName = apk.name,
        apkDownloadUrl = "https://example.invalid/update.apk",
        apkSizeBytes = 1024
    )

    @Before fun prepareQueuedMainDispatcher() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.checkLatestUpdate() } returns update
        model = AppUpdateViewModel(repository)
        store.put("update-concurrency", model)
    }

    @After fun clearViewModel() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun repeatedDownloadBeforeMainRunsStartsOnlyOneRepositoryRequest() {
        loadAvailableUpdate()
        val completion = CompletableDeferred<File>()
        coEvery { repository.downloadApk(any(), any()) } coAnswers { completion.await() }

        model.downloadUpdate()
        model.downloadUpdate()
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) { repository.downloadApk(update, any()) }
        assertTrue(model.uiState.value.downloading)
        completion.complete(apk)
        dispatcher.scheduler.runCurrent()
        assertFalse(model.uiState.value.downloading)
        assertEquals(apk, model.uiState.value.downloadedFile)
    }

    @Test fun repeatedCheckBeforeMainRunsStartsOnlyOneRepositoryRequest() {
        val completion = CompletableDeferred<AppUpdateInfo?>()
        coEvery { repository.checkLatestUpdate() } coAnswers { completion.await() }

        model.checkForUpdate()
        model.checkForUpdate()
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) { repository.checkLatestUpdate() }
        completion.complete(update)
        dispatcher.scheduler.runCurrent()
        assertFalse(model.uiState.value.checking)
        assertEquals(update, model.uiState.value.updateInfo)
    }

    @Test fun checkingCannotClearAResultWhileTheApkIsDownloading() {
        loadAvailableUpdate()
        val completion = CompletableDeferred<File>()
        coEvery { repository.downloadApk(any(), any()) } coAnswers { completion.await() }
        model.downloadUpdate()
        dispatcher.scheduler.runCurrent()

        model.checkForUpdate()
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) { repository.checkLatestUpdate() }
        assertTrue(model.uiState.value.downloading)
        completion.complete(apk)
        dispatcher.scheduler.runCurrent()
        assertEquals(apk, model.uiState.value.downloadedFile)
    }

    @Test fun downloadingWaitsUntilTheCurrentCheckFinishes() {
        loadAvailableUpdate()
        val completion = CompletableDeferred<AppUpdateInfo?>()
        coEvery { repository.checkLatestUpdate() } coAnswers { completion.await() }
        coEvery { repository.downloadApk(any(), any()) } returns apk
        model.checkForUpdate()
        dispatcher.scheduler.runCurrent()

        model.downloadUpdate()
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 0) { repository.downloadApk(any(), any()) }
        assertTrue(model.uiState.value.checking)
        completion.complete(update)
        dispatcher.scheduler.runCurrent()
        model.downloadUpdate()
        dispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { repository.downloadApk(update, any()) }
    }

    @Test fun failedDownloadAllowsOneExplicitRetry() {
        loadAvailableUpdate()
        coEvery { repository.downloadApk(any(), any()) } throws IOException("Controlled failure")
        model.downloadUpdate()
        dispatcher.scheduler.runCurrent()
        assertFalse(model.uiState.value.downloading)
        assertEquals("Controlled failure", model.uiState.value.error)

        coEvery { repository.downloadApk(any(), any()) } returns apk
        model.downloadUpdate()
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 2) { repository.downloadApk(update, any()) }
        assertEquals(apk, model.uiState.value.downloadedFile)
        assertEquals(null, model.uiState.value.error)
    }

    private fun loadAvailableUpdate() {
        model.checkForUpdate()
        dispatcher.scheduler.runCurrent()
        assertEquals(update, model.uiState.value.updateInfo)
    }
}
