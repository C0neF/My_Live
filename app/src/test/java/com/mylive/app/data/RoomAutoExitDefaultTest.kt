package com.mylive.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.secure.CredentialCipher
import com.mylive.app.data.local.secure.SensitiveCredentialStore
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.ui.screen.settings.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomAutoExitDefaultTest {

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        settingsDataStore = SettingsDataStore(context)
        settingsDataStore.clearAll()
        settingsRepository = SettingsRepository(
            settingsDataStore = settingsDataStore,
            sensitiveCredentialStore = SensitiveCredentialStore(
                settingsDataStore = settingsDataStore,
                cipher = PassthroughCredentialCipher
            )
        )
    }

    @After
    fun tearDown() = runBlocking {
        settingsDataStore.clearAll()
    }

    @Test
    fun unsetRoomAutoExitDefaultsToNeverExit() = runBlocking {
        assertEquals(0, settingsRepository.roomAutoExitDuration.first())
        assertEquals(0, SettingsViewModel(settingsRepository).roomAutoExitDuration.value)
    }

    private object PassthroughCredentialCipher : CredentialCipher {
        override fun encrypt(plainText: String): String = plainText

        override fun decrypt(cipherText: String): String = cipherText
    }
}
