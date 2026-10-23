package com.mylive.app.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.secure.CredentialCipher
import com.mylive.app.data.local.secure.SensitiveCredentialStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Account cookies and the WebDAV password must never sit in the preferences file in the clear.
 *
 * This used to be checked by grepping the production sources for the strings `"cipher.encrypt"`,
 * `"migrateLegacyCredential"` and so on — which proved that the code *mentions* encryption, not
 * that anything is encrypted. Here the store runs against an in-memory preferences map, so the
 * assertions are about the values that actually land in it.
 */
class SensitiveCredentialStoragePolicyTest {

    private val stored = MutableStateFlow(emptyPreferences())

    /** Reversible stand-in for the Keystore cipher, so ciphertext is recognisable in assertions. */
    private val cipher = object : CredentialCipher {
        override fun encrypt(plainText: String): String =
            if (plainText.isEmpty()) "" else "sealed(${plainText.reversed()})"

        override fun decrypt(cipherText: String): String {
            require(cipherText.startsWith("sealed(") && cipherText.endsWith(")")) {
                "not a sealed value"
            }
            return cipherText.removeSurrounding("sealed(", ")").reversed()
        }
    }

    private val store = SensitiveCredentialStore(inMemorySettingsDataStore(), cipher)

    @Test
    fun aStoredCookieIsCiphertextInThePreferencesFile() = runBlocking {
        store.setBilibiliCookie("SESSDATA=super-secret")

        val onDisk = stored.value[SettingsDataStore.EncryptedBilibiliCookie]

        assertTrue("nothing was written", !onDisk.isNullOrEmpty())
        assertFalse("plaintext leaked into preferences", onDisk!!.contains("super-secret"))
        // ...and it still reads back.
        assertEquals("SESSDATA=super-secret", store.bilibiliCookie.first())
    }

    @Test
    fun noCredentialIsEverWrittenToItsLegacyPlaintextKey() = runBlocking {
        store.setBilibiliCookie("bili")
        store.setDouyinCookie("douyin")
        store.setDouyuCookie("dy_auth=test-login; LTP0=test-renewal")
        store.setWebDavPassword("hunter2")

        val raw = stored.value
        assertNull(raw[SettingsDataStore.BilibiliCookie])
        assertNull(raw[SettingsDataStore.DouyinCookie])
        assertNull(raw[SettingsDataStore.DouyuCookie])
        assertNull(raw[SettingsDataStore.kWebDAVPassword])
    }

    @Test
    fun douyuLoginAndRenewalAreEncryptedTogetherAndLateRenewalCannotUndoLogout() = runBlocking {
        val original = "dy_auth=test-login; LTP0=test-renewal"
        store.setDouyuCookie(original)
        assertFalse(stored.value[SettingsDataStore.EncryptedDouyuCookie].orEmpty().contains("test-renewal"))
        assertEquals(original, store.douyuCookie.first())
        assertTrue(store.replaceDouyuCookieIfUnchanged(original, "dy_auth=fresh; LTP0=test-renewal"))
        store.setDouyuCookie("")
        assertFalse(store.replaceDouyuCookieIfUnchanged("dy_auth=fresh; LTP0=test-renewal", original))
        assertEquals("", store.douyuCookie.first())
        assertNull(stored.value[SettingsDataStore.EncryptedDouyuCookie])
    }

    @Test
    fun aLegacyPlaintextCredentialIsSealedOnFirstReadAndThePlaintextRemoved() = runBlocking {
        // An install that predates encryption: the cookie is sitting in the clear.
        putRaw(SettingsDataStore.DouyinCookie, "ttwid=legacy-plaintext")

        // Reading is what triggers the migration.
        assertEquals("ttwid=legacy-plaintext", store.douyinCookie.first())

        val raw = stored.value
        assertNull("legacy plaintext survived migration", raw[SettingsDataStore.DouyinCookie])
        val sealed = raw[SettingsDataStore.EncryptedDouyinCookie]
        assertTrue("no sealed value written", !sealed.isNullOrEmpty())
        assertFalse(sealed!!.contains("legacy-plaintext"))
    }

    @Test
    fun anAlreadySealedCredentialWinsAndTheStaleLegacyCopyIsDropped() = runBlocking {
        putRaw(SettingsDataStore.EncryptedWebDAVPassword, cipher.encrypt("current"))
        putRaw(SettingsDataStore.kWebDAVPassword, "stale-plaintext")

        assertEquals("current", store.webDavPassword.first())
        assertNull(stored.value[SettingsDataStore.kWebDAVPassword])
    }

    @Test
    fun signingOutRemovesBothTheSealedAndTheLegacyValue() = runBlocking {
        putRaw(SettingsDataStore.BilibiliCookie, "old-plaintext")
        store.setBilibiliCookie("fresh")

        store.setBilibiliCookie("")

        val raw = stored.value
        assertNull(raw[SettingsDataStore.EncryptedBilibiliCookie])
        assertNull(raw[SettingsDataStore.BilibiliCookie])
        assertEquals("", store.bilibiliCookie.first())
    }

    @Test
    fun anUnreadableSealedValueDegradesToSignedOutRatherThanCrashing() = runBlocking {
        // What a lost Keystore key looks like: the envelope is there but will not open.
        putRaw(SettingsDataStore.EncryptedBilibiliCookie, "corrupted-not-an-envelope")

        assertEquals("", store.bilibiliCookie.first())
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun putRaw(key: Preferences.Key<String>, value: String) {
        stored.value = stored.value.toMutablePreferences().apply { set(key, value) }.toPreferences()
    }

    /** A [SettingsDataStore] backed by [stored] instead of a file on disk. */
    private fun inMemorySettingsDataStore(): SettingsDataStore {
        val fake = mockk<SettingsDataStore>()
        every { fake.preferencesFlow() } returns stored
        every { fake.getFlow(any<Preferences.Key<String>>(), any<String>()) } answers {
            val key = firstArg<Preferences.Key<String>>()
            val fallback = secondArg<String>()
            stored.map { it[key] ?: fallback }
        }
        coEvery { fake.edit(any()) } answers {
            val block = firstArg<(MutablePreferences) -> Unit>()
            stored.value = stored.value.toMutablePreferences().apply(block).toPreferences()
        }
        coEvery { fake.removeValue(any<Preferences.Key<String>>()) } answers {
            val key = firstArg<Preferences.Key<String>>()
            stored.value = stored.value.toMutablePreferences()
                .apply { remove(key) }
                .toPreferences()
        }
        return fake
    }
}
