package com.mylive.app.ui.screen.sync

import android.app.Application
import android.content.ContentResolver
import android.content.ContextWrapper
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mylive.app.R
import com.mylive.app.data.repository.ProfileBackupManager
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.theme.MyLiveTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Real export screen and ViewModel, with only the document-provider boundary controlled. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
@OptIn(ExperimentalTestApi::class)
class ProfileBackupFileExportTest {
    private val effects = StandardTestDispatcher()
    @get:Rule val compose = createComposeRule(effectContext = effects)
    private val application: Application = RuntimeEnvironment.getApplication()
    private val manager = mockk<ProfileBackupManager>()
    private val resolver = mockk<ContentResolver>()
    private val uri = Uri.parse("content://profile-export-test/config.json")
    private val viewModel = ProfileBackupViewModel(manager)
    private val store = ViewModelStore().also { it.put("profile", viewModel) }
    private val owner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
    }
    private val resultOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                dispatchResult(requestCode, uri)
            }
        }
    }

    @After fun clearViewModel() {
        compose.runOnUiThread {
            store.clear()
            effects.scheduler.runCurrent()
        }
    }

    @Test fun aProviderThatReturnsNoOutputStreamMustNotAnnounceSuccess() {
        every { resolver.openOutputStream(uri) } returns null

        export()

        assertTrue("A crashed document provider must report export failure: ${viewModel.uiState.value}",
            viewModel.uiState.value.message is ProfileMessage.ExportFailed)
        compose.onNodeWithText(application.getString(R.string.profile_export_ok)).assertDoesNotExist()
    }

    @Test fun openingWritingAndClosingTheDocumentMustRunOffTheMainThread() {
        val openedOnMain = AtomicBoolean(false)
        val writtenOnMain = AtomicBoolean(false)
        val closedOnMain = AtomicBoolean(false)
        every { resolver.openOutputStream(uri) } answers {
            openedOnMain.set(Looper.myLooper() == Looper.getMainLooper())
            object : ByteArrayOutputStream() {
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    writtenOnMain.set(Looper.myLooper() == Looper.getMainLooper())
                    super.write(bytes, offset, length)
                }
                override fun close() {
                    closedOnMain.set(Looper.myLooper() == Looper.getMainLooper())
                    super.close()
                }
            }
        }

        export()

        assertTrue(viewModel.uiState.value.message is ProfileMessage.ExportOk)
        assertFalse("Opening a slow document provider must not block the UI", openedOnMain.get())
        assertFalse("Writing the configuration must not block the UI", writtenOnMain.get())
        assertFalse("Closing/flushing the document must not block the UI", closedOnMain.get())
    }

    @Test fun successfulExportWritesTheExactUtf8BackupAndClosesTheDocument() {
        val closed = AtomicBoolean(false)
        val output = object : ByteArrayOutputStream() {
            override fun close() { closed.set(true); super.close() }
        }
        every { resolver.openOutputStream(uri) } returns output

        export()

        assertTrue(viewModel.uiState.value.message is ProfileMessage.ExportOk)
        assertArrayEquals(JSON.toByteArray(Charsets.UTF_8), output.toByteArray())
        assertTrue("Success is allowed only after closing/flushing the file", closed.get())
    }

    @Test fun aDocumentWriteFailureReportsFailure() {
        every { resolver.openOutputStream(uri) } returns object : OutputStream() {
            override fun write(value: Int) { throw IOException("Controlled write failure") }
        }

        export()

        assertTrue(viewModel.uiState.value.message is ProfileMessage.ExportFailed)
        compose.onNodeWithText(application.getString(R.string.profile_export_ok)).assertDoesNotExist()
    }

    @Test fun aDocumentCloseFailureReportsFailure() {
        every { resolver.openOutputStream(uri) } returns object : ByteArrayOutputStream() {
            override fun close() { throw IOException("Controlled close failure") }
        }

        export()

        assertTrue(viewModel.uiState.value.message is ProfileMessage.ExportFailed)
        compose.onNodeWithText(application.getString(R.string.profile_export_ok)).assertDoesNotExist()
    }

    private fun export() {
        coEvery { manager.exportProfileJson() } returns JSON
        val context = object : ContextWrapper(application) {
            override fun getContentResolver(): ContentResolver = resolver
        }
        val navigator = Navigator().apply { navigate(Route.ProfileBackup) }
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalLifecycleOwner provides owner,
                LocalActivityResultRegistryOwner provides resultOwner
            ) {
                MyLiveTheme { ProfileBackupScreen(navigator, viewModel) }
            }
        }
        settleUi()
        compose.onNodeWithText(application.getString(R.string.profile_export)).performClick()
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.runOnUiThread { effects.scheduler.runCurrent() }
            viewModel.uiState.value.message != null && !viewModel.uiState.value.isExporting
        }
        settleUi()
    }

    private fun settleUi() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.runOnUiThread { effects.scheduler.runCurrent() }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnUiThread { effects.scheduler.runCurrent() }
        compose.waitForIdle()
    }

    private companion object {
        const val JSON = """{"type":"my_live_profile","schemaVersion":1,"settings":{"userRemarks":"中文 🎥"}}"""
    }
}
