package com.mylive.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SensitiveCredentialStoragePolicyTest {

    @Test
    fun accountCookiesAreNotReadOrWrittenThroughPlainSettingsDataStore() {
        val accountRepository = File(
            "src/main/java/com/mylive/app/data/repository/AccountRepository.kt"
        ).readText()
        val settingsRepository = File(
            "src/main/java/com/mylive/app/data/repository/SettingsRepository.kt"
        ).readText()

        assertTrue(accountRepository.contains("SensitiveCredentialStore"))
        assertTrue(settingsRepository.contains("SensitiveCredentialStore"))
        assertFalse(accountRepository.contains("SettingsDataStore.BilibiliCookie"))
        assertFalse(accountRepository.contains("SettingsDataStore.DouyinCookie"))
        assertFalse(settingsRepository.contains("SettingsDataStore.BilibiliCookie"))
        assertFalse(settingsRepository.contains("SettingsDataStore.DouyinCookie"))
    }

    @Test
    fun webDavPasswordIsNotReadOrWrittenThroughPlainSettingsDataStore() {
        val source = File(
            "src/main/java/com/mylive/app/ui/screen/sync/WebDavSyncScreen.kt"
        ).readText()

        assertTrue(source.contains("SensitiveCredentialStore"))
        assertFalse(source.contains("getFlow(SettingsDataStore.kWebDAVPassword"))
        assertFalse(source.contains("setValue(SettingsDataStore.kWebDAVPassword"))
    }

    @Test
    fun sensitiveCredentialStoreEncryptsValuesAndClearsLegacyPlaintextKeys() {
        val source = File(
            "src/main/java/com/mylive/app/data/local/secure/SensitiveCredentialStore.kt"
        ).readText()

        assertTrue(source.contains("EncryptedBilibiliCookie"))
        assertTrue(source.contains("EncryptedDouyinCookie"))
        assertTrue(source.contains("EncryptedWebDAVPassword"))
        assertTrue(source.contains("cipher.encrypt"))
        assertTrue(source.contains("cipher.decrypt"))
        assertTrue(source.contains("migrateLegacyCredential"))
        assertTrue(source.contains("settingsDataStore.removeValue(legacyKey)"))
    }
}
