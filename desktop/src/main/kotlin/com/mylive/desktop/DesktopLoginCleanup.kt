package com.mylive.desktop

import java.net.CookieHandler
import java.net.CookieManager
import java.util.concurrent.atomic.AtomicBoolean

/** Keep the session's cookie handler installed until its queued WebKit work has stopped. */
internal class DesktopLoginCleanup(
    private val manager: CookieManager,
    private val previous: CookieHandler?,
    private val schedule: (() -> Unit) -> Unit,
    private val stopBrowser: () -> Unit,
    private val finished: () -> Unit
) {
    private val closing = AtomicBoolean(false)
    val isClosed get() = closing.get()
    fun close() {
        if (!closing.compareAndSet(false, true)) return
        schedule {
            try { stopBrowser() }
            finally {
                if (CookieHandler.getDefault() === manager) CookieHandler.setDefault(previous)
                manager.cookieStore.removeAll()
                finished()
            }
        }
    }
    fun ifOpen(action: () -> Unit) { if (!closing.get()) action() }
}
