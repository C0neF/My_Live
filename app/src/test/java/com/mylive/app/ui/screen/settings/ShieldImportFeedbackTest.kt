package com.mylive.app.ui.screen.settings

import android.app.Application
import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mylive.app.R
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.data.repository.ShieldRepository
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.theme.MyLiveTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Real file-import screen and ViewModel; transaction completion is held at the repository boundary.
 * Text-import dialogs are not exercised here: this host's Robolectric text layout does not settle.
 * The shared await contract is covered separately with real Room transactions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
@OptIn(ExperimentalTestApi::class)
class ShieldImportFeedbackTest {
    private val effects = StandardTestDispatcher()
    // Queue resumes from Dispatchers.IO onto the test's main loop instead of resuming unconfined.
    @get:Rule val compose = createComposeRule(effectContext = effects)
    private val context: Application = RuntimeEnvironment.getApplication()
    private val repository = mockk<ShieldRepository>()
    private val settings = mockk<SettingsRepository>()
    private val commit = CompletableDeferred<Unit>()
    private val transactionEntered = AtomicBoolean(false)
    private val transactionCancelled = AtomicBoolean(false)
    private val visible = mutableStateOf(true)
    private val store = ViewModelStore()
    private lateinit var viewModel: ShieldSettingsViewModel
    private val owner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
    }
    private val importUri = Uri.parse("content://shield-test/import.json")
    private val resultOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                dispatchResult(requestCode, importUri)
            }
        }
    }

    @Before fun prepareImport() {
        ShadowToast.reset()
        every { repository.getAllShields() } returns flowOf(emptyList())
        every { repository.getAllPresets() } returns flowOf(emptyList())
        every { settings.danmuShieldEnable } returns flowOf(true)
        every { settings.danmuKeywordShieldEnable } returns flowOf(true)
        every { settings.danmuUserShieldEnable } returns flowOf(true)
        coEvery { repository.importConfiguration(any(), any()) } coAnswers {
            transactionEntered.set(true)
            try {
                commit.await()
            } catch (cancelled: CancellationException) {
                transactionCancelled.set(true)
                throw cancelled
            }
        }
        viewModel = ShieldSettingsViewModel(repository, settings)
        store.put("shield", viewModel)
        shadowOf(context.contentResolver).registerInputStream(importUri, JSON.byteInputStream())
    }

    @After fun cleanUpImport() {
        compose.runOnUiThread {
            store.clear()
            commit.cancel()
            effects.scheduler.runCurrent()
        }
        ShadowToast.reset()
    }

    @Test fun fileImportReportsSuccessOnlyAfterTheTransactionCompletes() {
        startImport()
        assertEquals("An uncommitted import must not announce success", 0, ShadowToast.shownToastCount())
        compose.onNodeWithText(context.getString(R.string.shield_preset_import)).assertIsNotEnabled()
        coVerify(exactly = 1) { repository.importConfiguration(any(), any()) }

        compose.runOnUiThread { commit.complete(Unit) }
        settleUi()

        assertEquals(context.getString(R.string.shield_preset_import_success), ShadowToast.getTextOfLatestToast())
        assertEquals(1, ShadowToast.shownToastCount())
    }

    @Test fun fileImportReportsDatabaseFailureWithoutAnnouncingSuccess() {
        startImport()
        compose.runOnUiThread { commit.completeExceptionally(SQLiteException("Controlled persistence failure")) }
        settleUi()

        assertEquals("Database failure must produce failed-import feedback",
            context.getString(R.string.shield_preset_import_failed), ShadowToast.getTextOfLatestToast())
        assertFalse("A failed transaction must never announce success",
            ShadowToast.showedToast(context.getString(R.string.shield_preset_import_success)))
        assertEquals(1, ShadowToast.shownToastCount())
    }

    @Test fun leavingTheScreenDuringFileImportPreventsLateFeedback() {
        startImport()
        // Clear earlier feedback so this case specifically detects feedback after disposal.
        ShadowToast.reset()
        compose.runOnUiThread { visible.value = false }
        settleUi()
        compose.onNodeWithText(context.getString(R.string.shield_title)).assertDoesNotExist()
        assertTrue("Disposing the screen must cancel its pending import", transactionCancelled.get())
        compose.runOnUiThread { commit.complete(Unit) }
        settleUi()
        assertEquals("A disposed page must not show completion feedback: ${ShadowToast.getTextOfLatestToast()}",
            0, ShadowToast.shownToastCount())
    }

    private fun startImport() {
        val navigator = Navigator().apply { navigate(Route.SettingsDanmuShield) }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner,
                LocalActivityResultRegistryOwner provides resultOwner) {
                MyLiveTheme { if (visible.value) ShieldSettingsScreen(navigator, viewModel) }
            }
        }
        settleUi()
        compose.onNodeWithText(context.getString(R.string.shield_tab_preset)).performClick()
        settleUi()
        compose.onNodeWithText(context.getString(R.string.shield_preset_import)).performClick()
        settleUi()
        compose.onNodeWithText("从文件导入").performClick()
        settleUi()
        compose.waitUntil(5_000) {
            compose.runOnUiThread { effects.scheduler.runCurrent() }
            transactionEntered.get()
        }
        settleUi()
    }

    private fun settleUi() {
        compose.runOnUiThread { effects.scheduler.runCurrent() }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnUiThread { effects.scheduler.runCurrent() }
        compose.waitForIdle()
    }

    private companion object {
        const val JSON = """{"current":{"keywords":["new"]}}"""
    }
}
