package com.mylive.app.ui.screen.settings

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.theme.MyLiveTheme
import com.mylive.app.update.AppUpdateInfo
import com.mylive.app.update.AppUpdateInstaller
import com.mylive.app.update.AppUpdateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Controlled update/installer state: this does not claim a live release is available. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class AppUpdateInstallRetryTest {
    @get:Rule val compose = createComposeRule()
    private val owner = UpdateLifecycleOwner()
    private val context = InstallRecordingContext(RuntimeEnvironment.getApplication())
    private val model = mockk<AppUpdateViewModel>(relaxed = true)
    private val downloadedFile = File(context.cacheDir, "updates/retry-update.apk")
    private val state = MutableStateFlow(AppUpdateUiState(updateInfo = AppUpdateInfo(
        tagName = "v2.99.0",
        versionName = "2.99.0",
        releaseName = "Controlled update fixture",
        releaseNotes = "",
        releasePageUrl = "https://example.invalid/update",
        apkName = downloadedFile.name,
        apkDownloadUrl = "https://example.invalid/retry-update.apk",
        apkSizeBytes = 1024
    )))
    private var installPermissionGranted = true
    private val store = ViewModelStore()

    @Before fun prepareInstaller() {
        mockkObject(AppUpdateInstaller)
        every { AppUpdateInstaller.canRequestPackageInstalls(any()) } answers { installPermissionGranted }
        every { AppUpdateInstaller.createInstallIntent(any(), any()) } answers {
            Intent(INSTALL_ACTION).putExtra("path", secondArg<File>().absolutePath)
        }
        every { AppUpdateInstaller.createInstallPermissionIntent(any()) } returns
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        every { model.uiState } returns state
        every { model.downloadUpdate() } answers {
            state.value = state.value.copy(downloading = true, downloadedFile = null)
        }
    }

    @After fun restoreInstaller() {
        store.clear()
        unmockkObject(AppUpdateInstaller)
    }

    @Test fun immediateSamePathDownloadsStillHaveDistinctInstallAttempts() {
        val repository = mockk<AppUpdateRepository>()
        coEvery { repository.checkLatestUpdate() } returns state.value.updateInfo
        // Both VM writes happen without suspension: Compose may never see the intermediate null.
        coEvery { repository.downloadApk(any(), any()) } returns downloadedFile
        val realModel = AppUpdateViewModel(repository)
        store.put("immediate-update", realModel)
        showUpdateScreen(realModel)

        compose.onNodeWithText("下载并安装").performClick()
        compose.waitForIdle()
        assertEquals(1, context.installRequests)
        returnFromExternalActivity()

        compose.onNodeWithText("下载并安装").performClick()
        compose.waitForIdle()
        coVerify(exactly = 2) { repository.downloadApk(any(), any()) }
        assertEquals("Every completed user download must launch once even when the null state is conflated", 2, context.installRequests)
        returnFromExternalActivity()
        assertEquals(2, context.installRequests)
    }

    @Test fun explicitDownloadAfterCancelCanLaunchTheSameApkAgain() {
        showUpdateScreen()
        startAndCompleteDownload()
        assertEquals(1, context.installRequests)

        returnFromExternalActivity()
        assertEquals("Canceling the installer must not reopen it automatically", 1, context.installRequests)

        startAndCompleteDownload()
        assertEquals("An explicit second download must reopen installation even at the same file path", 2, context.installRequests)
        assertEquals(listOf(downloadedFile.absolutePath, downloadedFile.absolutePath), context.installPaths)

        returnFromExternalActivity()
        assertEquals("Resuming again must not create an install loop", 2, context.installRequests)
    }

    @Test fun grantingUnknownSourcePermissionResumesInstallationOnlyOnce() {
        installPermissionGranted = false
        showUpdateScreen()
        startAndCompleteDownload()
        assertEquals(0, context.installRequests)
        assertEquals(1, context.permissionRequests)

        returnFromExternalActivity()
        assertEquals("Returning without permission must not reopen the permission screen", 1, context.permissionRequests)
        assertEquals(0, context.installRequests)

        installPermissionGranted = true
        returnFromExternalActivity()
        assertEquals(1, context.installRequests)
        returnFromExternalActivity()
        assertEquals(1, context.installRequests)
        assertEquals(1, context.permissionRequests)
    }

    @Test fun rejectedUnknownSourcePermissionCanBeRequestedAgainExplicitly() {
        installPermissionGranted = false
        showUpdateScreen()
        startAndCompleteDownload()
        assertEquals(1, context.permissionRequests)

        returnFromExternalActivity()
        assertEquals(1, context.permissionRequests)
        assertEquals(0, context.installRequests)

        startAndCompleteDownload()
        assertEquals(2, context.permissionRequests)
        installPermissionGranted = true
        returnFromExternalActivity()
        assertEquals(1, context.installRequests)
        returnFromExternalActivity()
        assertEquals(1, context.installRequests)
    }

    @Test fun failedInstallerLaunchCanBeRetriedWithTheSameApkExplicitly() {
        context.failNextInstall = true
        showUpdateScreen()
        startAndCompleteDownload()
        assertEquals(1, context.installRequests)

        returnFromExternalActivity()
        assertEquals("A failed launch must not turn every resume into another install attempt", 1, context.installRequests)

        startAndCompleteDownload()
        assertEquals("An explicit retry must recover from a failed installer launch", 2, context.installRequests)
        returnFromExternalActivity()
        assertEquals(2, context.installRequests)
    }

    private fun showUpdateScreen(screenModel: AppUpdateViewModel = model) {
        val navigator = Navigator().apply { navigate(Route.SettingsUpdate) }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalLifecycleOwner provides owner) {
                MyLiveTheme { AppUpdateScreen(navigator, screenModel) }
            }
        }
        compose.waitForIdle()
    }

    private fun startAndCompleteDownload() {
        compose.onNodeWithText("下载并安装").performClick()
        // The real ViewModel clears downloadedFile before downloading again. Render that state.
        compose.waitForIdle()
        compose.runOnIdle {
            state.value = state.value.copy(
                downloading = false,
                downloadProgress = 100,
                downloadedFile = downloadedFile,
                installRequestId = state.value.installRequestId + 1
            )
        }
        compose.waitForIdle()
    }

    private fun returnFromExternalActivity() {
        compose.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE) }
        compose.waitForIdle()
        compose.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
        compose.waitForIdle()
    }

    private class UpdateLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private class InstallRecordingContext(base: Context) : ContextWrapper(base) {
        val intents = mutableListOf<Intent>()
        var failNextInstall = false
        val installRequests get() = intents.count { it.action == INSTALL_ACTION }
        val permissionRequests get() = intents.count { it.action == Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES }
        val installPaths get() = intents.filter { it.action == INSTALL_ACTION }.map { it.getStringExtra("path") }

        override fun startActivity(intent: Intent) {
            intents += intent
            if (intent.action == INSTALL_ACTION && failNextInstall) {
                failNextInstall = false
                throw android.content.ActivityNotFoundException("Controlled installer failure")
            }
        }
    }

    private companion object {
        const val INSTALL_ACTION = "com.mylive.test.INSTALL_UPDATE"
    }
}
