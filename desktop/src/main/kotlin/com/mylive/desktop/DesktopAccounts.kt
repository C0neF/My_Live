package com.mylive.desktop

import com.mylive.app.core.site.LiveSiteAccount
import com.mylive.app.core.site.LiveSiteAccountStore
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

interface DesktopCredentialProtector {
    fun protect(bytes: ByteArray): ByteArray
    fun unprotect(bytes: ByteArray): ByteArray
}

/** Default DPAPI user scope: no machine-wide key and no interactive Windows prompt. */
class WindowsCredentialProtector : DesktopCredentialProtector {
    override fun protect(bytes: ByteArray): ByteArray = Crypt32Util.cryptProtectData(bytes, null,
        WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, "My Live credentials", null)
    override fun unprotect(bytes: ByteArray): ByteArray = Crypt32Util.cryptUnprotectData(bytes, null,
        WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, null)
}

data class DesktopAccountStatus(val present: Boolean = false, val remembered: Boolean = false, val error: String? = null)

class DesktopAccounts(
    private val directory: Path = defaultDesktopLibraryPath().toAbsolutePath().parent.resolve("credentials"),
    private val protector: DesktopCredentialProtector = WindowsCredentialProtector(),
    private val io: CoroutineDispatcher = Dispatchers.IO
) : LiveSiteAccountStore {
    private val cookies = ConcurrentHashMap<LiveSiteAccount, String>()
    private val mutex = Mutex()
    private val _state = MutableStateFlow(LiveSiteAccount.entries.associateWith { DesktopAccountStatus() })
    val state = _state.asStateFlow()

    override suspend fun cookieFor(account: LiveSiteAccount): String = cookies[account].orEmpty()

    suspend fun load() = withContext(io) {
        mutex.withLock {
            LiveSiteAccount.entries.forEach { account ->
                try {
                    val file = path(account)
                    val value = if (Files.exists(file)) read(file) else ""
                    if (value.isEmpty()) cookies.remove(account) else cookies[account] = value
                    publish(account, DesktopAccountStatus(value.isNotEmpty(), value.isNotEmpty()))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    cookies.remove(account)
                    publish(account, DesktopAccountStatus(error = "登录信息读取失败，原加密文件已保留；可重新登录或退出该账号。"))
                }
            }
        }
    }

    suspend fun update(account: LiveSiteAccount, cookie: String, remember: Boolean): Boolean = withContext(io) {
        mutex.withLock {
            val value = cookie.trim()
            require(value.toByteArray(Charsets.UTF_8).size <= 64 * 1024 && value.none { it == '\r' || it == '\n' }) { "Cookie 格式不正确" }
            try {
                withDesktopFileLock(path(account)) {
                    if (remember && value.isNotEmpty()) save(path(account), value)
                    else Files.deleteIfExists(path(account))
                }
                if (value.isEmpty()) cookies.remove(account) else cookies[account] = value
                publish(account, DesktopAccountStatus(value.isNotEmpty(), remember && value.isNotEmpty()))
                true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                publish(account, state.value.getValue(account).copy(error = "登录信息保存失败，请检查磁盘权限后重试。"))
                false
            }
        }
    }

    override suspend fun replaceCookieIfUnchanged(account: LiveSiteAccount, expected: String, replacement: String): Boolean = withContext(io) {
        mutex.withLock {
            if (cookies[account].orEmpty() != expected) return@withLock false
            require(replacement.toByteArray(Charsets.UTF_8).size <= 64 * 1024 && replacement.none { it == '\r' || it == '\n' })
            try {
                if (state.value.getValue(account).remembered) {
                    val changed = withDesktopFileLock(path(account)) {
                        // A renewal from an older process must not undo another process's login/sign-out.
                        if (!Files.exists(path(account)) || read(path(account)) != expected) false
                        else { save(path(account), replacement); true }
                    }
                    if (!changed) return@withLock false
                }
                cookies[account] = replacement
                publish(account, state.value.getValue(account).copy(present = replacement.isNotEmpty(), error = null))
                true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                publish(account, state.value.getValue(account).copy(error = "账号续期保存失败，请重新登录。"))
                false
            }
        }
    }

    fun clearMemory() { cookies.clear() }
    private fun path(account: LiveSiteAccount) = directory.resolve("${account.name.lowercase()}.bin")
    private fun read(file: Path): String {
        require(Files.size(file) <= 256 * 1024)
        val clear = protector.unprotect(Files.readAllBytes(file))
        return try { clear.toString(Charsets.UTF_8).also { require(it.toByteArray(Charsets.UTF_8).size <= 64 * 1024 && it.none { c -> c == '\r' || c == '\n' }) } }
        finally { clear.fill(0) }
    }
    private fun save(file: Path, cookie: String) {
        val bytes = cookie.toByteArray(Charsets.UTF_8)
        try { writeDesktopFileAtomically(file, protector.protect(bytes)) } finally { bytes.fill(0) }
    }
    private fun publish(account: LiveSiteAccount, status: DesktopAccountStatus) { _state.value = _state.value + (account to status) }
}
