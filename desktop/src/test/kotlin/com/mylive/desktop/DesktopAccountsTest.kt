package com.mylive.desktop

import com.mylive.app.core.site.LiveSiteAccount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopAccountsTest {
    @get:Rule val folder = TemporaryFolder()
    private val account = LiveSiteAccount.DOUYU
    private val directory get() = folder.root.toPath().resolve("credentials")
    private val fixtureProtector = object : DesktopCredentialProtector {
        override fun protect(bytes: ByteArray) = byteArrayOf(42) + bytes.reversedArray()
        override fun unprotect(bytes: ByteArray): ByteArray {
            require(bytes.first() == 42.toByte()); return bytes.drop(1).toByteArray().reversedArray()
        }
    }
    @Test fun reloadingCorruptedCredentialsCannotKeepUsingAStaleLogin() = runTest {
        val accounts = DesktopAccounts(directory, fixtureProtector, StandardTestDispatcher(testScheduler))
        accounts.update(account, "token=old", true)
        Files.write(directory.resolve("douyu.bin"), byteArrayOf(0))
        accounts.load()
        assertEquals("", accounts.cookieFor(account))
        assertNotNull(accounts.state.value.getValue(account).error)
    }

    @Test fun actualWindowsProtectionRoundTripsAndDoesNotWritePlaintext() = runTest {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val accounts = DesktopAccounts(directory, WindowsCredentialProtector(), dispatcher)
        val cookie = "test_cookie=isolated-dummy-value; uid=fixture"
        assertTrue(accounts.update(account, cookie, true))
        val encrypted = Files.readAllBytes(directory.resolve("douyu.bin"))
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("isolated-dummy-value"))
        val restarted = DesktopAccounts(directory, WindowsCredentialProtector(), dispatcher)
        restarted.load()
        assertEquals(cookie, restarted.cookieFor(account))
        assertTrue(restarted.state.value.getValue(account).remembered)
    }

    @Test fun sessionOnlyCredentialsAreNotRestoredAfterRestart() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val accounts = DesktopAccounts(directory, fixtureProtector, dispatcher)
        accounts.update(account, "token=fixture", false)
        assertEquals("token=fixture", accounts.cookieFor(account))
        assertFalse(Files.exists(directory.resolve("douyu.bin")))
        val restarted = DesktopAccounts(directory, fixtureProtector, dispatcher); restarted.load()
        assertEquals("", restarted.cookieFor(account))
    }

    @Test fun signOutRemovesStoredCredentialsAndLateRenewalCannotRestoreThem() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val accounts = DesktopAccounts(directory, fixtureProtector, dispatcher)
        accounts.update(account, "token=old", true)
        assertTrue(accounts.update(account, "", false))
        assertFalse(accounts.replaceCookieIfUnchanged(account, "token=old", "token=late"))
        val restarted = DesktopAccounts(directory, fixtureProtector, dispatcher); restarted.load()
        assertEquals("", restarted.cookieFor(account))
    }

    @Test fun renewalPersistsButCannotReplaceANewerLogin() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val accounts = DesktopAccounts(directory, fixtureProtector, dispatcher)
        accounts.update(account, "token=old", true)
        assertTrue(accounts.replaceCookieIfUnchanged(account, "token=old", "token=renewed"))
        accounts.update(account, "token=new-login", true)
        assertFalse(accounts.replaceCookieIfUnchanged(account, "token=renewed", "token=late"))
        val restarted = DesktopAccounts(directory, fixtureProtector, dispatcher); restarted.load()
        assertEquals("token=new-login", restarted.cookieFor(account))
    }

    @Test fun anotherProcessSignOutPreventsAnOlderProcessFromSavingRenewal() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = DesktopAccounts(directory, fixtureProtector, dispatcher)
        first.update(account, "token=old", true)
        val second = DesktopAccounts(directory, fixtureProtector, dispatcher); second.load()
        second.update(account, "", false)
        assertFalse(first.replaceCookieIfUnchanged(account, "token=old", "token=late"))
        assertFalse(Files.exists(directory.resolve("douyu.bin")))
    }

    @Test fun unreadableEncryptedFileIsPreservedAndReported() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Files.createDirectories(directory)
        val bad = byteArrayOf(1, 2, 3)
        Files.write(directory.resolve("douyu.bin"), bad)
        val accounts = DesktopAccounts(directory, fixtureProtector, dispatcher); accounts.load()
        assertEquals("", accounts.cookieFor(account))
        assertNotNull(accounts.state.value.getValue(account).error)
        assertArrayEquals(bad, Files.readAllBytes(directory.resolve("douyu.bin")))
    }

    @Test fun failedEncryptionKeepsThePreviousLoginAndFile() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val existing = DesktopAccounts(directory, fixtureProtector, dispatcher)
        existing.update(account, "token=old", true)
        val original = Files.readAllBytes(directory.resolve("douyu.bin"))
        val failing = object : DesktopCredentialProtector {
            override fun protect(bytes: ByteArray): ByteArray = error("protection failure")
            override fun unprotect(bytes: ByteArray) = fixtureProtector.unprotect(bytes)
        }
        val accounts = DesktopAccounts(directory, failing, dispatcher); accounts.load()
        assertFalse(accounts.update(account, "token=new", true))
        assertEquals("token=old", accounts.cookieFor(account))
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("douyu.bin")))
    }

    @Test fun persistedProviderAccountsAndSessionClearAreIndependent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val accounts = DesktopAccounts(directory, fixtureProtector, dispatcher)
        LiveSiteAccount.entries.forEach { accounts.update(it, "token=${it.name}", true) }
        accounts.update(account, "", false)
        accounts.clearMemory()
        assertEquals("", accounts.cookieFor(LiveSiteAccount.BILIBILI))
        val restarted = DesktopAccounts(directory, fixtureProtector, dispatcher); restarted.load()
        assertEquals("token=BILIBILI", restarted.cookieFor(LiveSiteAccount.BILIBILI))
        assertEquals("token=DOUYIN", restarted.cookieFor(LiveSiteAccount.DOUYIN))
        assertEquals("", restarted.cookieFor(account))
    }
}
