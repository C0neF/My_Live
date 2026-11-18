package com.mylive.app.data.local.secure

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import com.mylive.app.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
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
        cachedSecretKey?.let { return it }
        return synchronized(keystoreLock) {
            cachedSecretKey?.let { return it }
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { existing ->
                cachedSecretKey = existing
                return existing
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
            keyGenerator.generateKey().also { generated ->
                cachedSecretKey = generated
            }
        }
    }

    private companion object {
        private val keystoreLock = Any()
        @Volatile
        private var cachedSecretKey: SecretKey? = null
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

    val douyuCookie: Flow<String> = credentialFlow(
        encryptedKey = SettingsDataStore.EncryptedDouyuCookie,
        legacyKey = SettingsDataStore.DouyuCookie
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

    suspend fun setDouyuCookie(value: String) {
        setCredential(SettingsDataStore.EncryptedDouyuCookie, SettingsDataStore.DouyuCookie, value)
    }

    suspend fun replaceDouyuCookieIfUnchanged(expected: String, replacement: String): Boolean {
        var replaced = false
        settingsDataStore.edit { prefs ->
            val encrypted = prefs[SettingsDataStore.EncryptedDouyuCookie].orEmpty()
            val current = if (encrypted.isNotEmpty()) {
                decryptMemoized(SettingsDataStore.EncryptedDouyuCookie, encrypted)
            } else prefs[SettingsDataStore.DouyuCookie].orEmpty()
            if (current == expected) {
                if (replacement.isEmpty()) {
                    prefs.remove(SettingsDataStore.EncryptedDouyuCookie)
                    decryptedCache.remove(SettingsDataStore.EncryptedDouyuCookie)
                } else {
                    val sealed = cipher.encrypt(replacement)
                    prefs[SettingsDataStore.EncryptedDouyuCookie] = sealed
                    decryptedCache[SettingsDataStore.EncryptedDouyuCookie] = sealed to replacement
                }
                prefs.remove(SettingsDataStore.DouyuCookie)
                replaced = true
            }
        }
        return replaced
    }

    suspend fun setWebDavPassword(value: String) {
        setCredential(SettingsDataStore.EncryptedWebDAVPassword, SettingsDataStore.kWebDAVPassword, value)
    }

    private val migratedKeys = ConcurrentHashMap.newKeySet<String>()
    private val decryptedCache = ConcurrentHashMap<Preferences.Key<String>, Pair<String, String>>()

    private fun decryptMemoized(key: Preferences.Key<String>, cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        val cached = decryptedCache[key]
        if (cached != null && cached.first == cipherText) {
            return cached.second
        }
        val plain = runCatching { cipher.decrypt(cipherText) }.getOrDefault("")
        decryptedCache[key] = cipherText to plain
        return plain
    }

    private fun credentialFlow(
        encryptedKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>
    ): Flow<String> {
        return settingsDataStore.preferencesFlow()
            .onStart { migrateLegacyCredential(encryptedKey, legacyKey) }
            .map { prefs ->
                prefs[encryptedKey]?.takeIf { it.isNotEmpty() }?.let { encrypted ->
                    decryptMemoized(encryptedKey, encrypted)
                } ?: prefs[legacyKey].orEmpty()
            }
            .distinctUntilChanged()
    }

    private suspend fun setCredential(
        encryptedKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>,
        value: String
    ) {
        settingsDataStore.edit { prefs ->
            if (value.isEmpty()) {
                prefs.remove(encryptedKey)
                decryptedCache.remove(encryptedKey)
            } else {
                val sealed = cipher.encrypt(value)
                prefs[encryptedKey] = sealed
                decryptedCache[encryptedKey] = sealed to value
            }
            prefs.remove(legacyKey)
        }
    }

    private suspend fun migrateLegacyCredential(
        encryptedKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>
    ) {
        val legacy = settingsDataStore.getFlow(legacyKey, "").first()
        if (legacy.isEmpty()) {
            migratedKeys.add(encryptedKey.name)
            return
        }
        val encrypted = settingsDataStore.getFlow(encryptedKey, "").first()
        if (encrypted.isEmpty()) {
            setCredential(encryptedKey, legacyKey, legacy)
        } else {
            settingsDataStore.removeValue(legacyKey)
        }
        migratedKeys.add(encryptedKey.name)
    }
}
