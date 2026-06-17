package com.mylive.app.data.local.secure

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import com.mylive.app.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

interface CredentialCipher {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}

class AndroidKeystoreCredentialCipher @Inject constructor() : CredentialCipher {
    override fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(
            cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
        return "$VERSION:$iv:$encrypted"
    }

    override fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        val parts = cipherText.split(":", limit = 3)
        require(parts.size == 3 && parts[0] == VERSION) { "Unsupported credential payload" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(parts[1], Base64.NO_WRAP))
        )
        return cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val VERSION = "v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "mylive_sensitive_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}

@Singleton
class SensitiveCredentialStore @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val cipher: CredentialCipher
) {
    val bilibiliCookie: Flow<String> = credentialFlow(
        encryptedKey = SettingsDataStore.EncryptedBilibiliCookie,
        legacyKey = SettingsDataStore.BilibiliCookie
    )

    val douyinCookie: Flow<String> = credentialFlow(
        encryptedKey = SettingsDataStore.EncryptedDouyinCookie,
        legacyKey = SettingsDataStore.DouyinCookie
    )

    val webDavPassword: Flow<String> = credentialFlow(
        encryptedKey = SettingsDataStore.EncryptedWebDAVPassword,
        legacyKey = SettingsDataStore.kWebDAVPassword
    )

    suspend fun setBilibiliCookie(value: String) {
        setCredential(SettingsDataStore.EncryptedBilibiliCookie, SettingsDataStore.BilibiliCookie, value)
    }

    suspend fun setDouyinCookie(value: String) {
        setCredential(SettingsDataStore.EncryptedDouyinCookie, SettingsDataStore.DouyinCookie, value)
    }

    suspend fun setWebDavPassword(value: String) {
        setCredential(SettingsDataStore.EncryptedWebDAVPassword, SettingsDataStore.kWebDAVPassword, value)
    }

    private fun credentialFlow(
        encryptedKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>
    ): Flow<String> {
        return settingsDataStore.preferencesFlow()
            .onStart { migrateLegacyCredential(encryptedKey, legacyKey) }
            .map { prefs ->
                prefs[encryptedKey]?.takeIf { it.isNotEmpty() }?.let { encrypted ->
                    runCatching { cipher.decrypt(encrypted) }.getOrDefault("")
                } ?: prefs[legacyKey].orEmpty()
            }
    }

    private suspend fun setCredential(
        encryptedKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>,
        value: String
    ) {
        settingsDataStore.edit { prefs ->
            if (value.isEmpty()) {
                prefs.remove(encryptedKey)
            } else {
                prefs[encryptedKey] = cipher.encrypt(value)
            }
            prefs.remove(legacyKey)
        }
    }

    private suspend fun migrateLegacyCredential(
        encryptedKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>
    ) {
        val encrypted = settingsDataStore.getFlow(encryptedKey, "").first()
        val legacy = settingsDataStore.getFlow(legacyKey, "").first()
        if (legacy.isEmpty()) return

        if (encrypted.isEmpty()) {
            setCredential(encryptedKey, legacyKey, legacy)
        } else {
            settingsDataStore.removeValue(legacyKey)
        }
    }
}
