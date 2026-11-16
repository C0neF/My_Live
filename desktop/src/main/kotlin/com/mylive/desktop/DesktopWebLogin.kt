package com.mylive.desktop

import com.mylive.app.core.site.LiveSiteAccount
import com.mylive.app.core.site.douyu.douyuCookiePairs
import com.mylive.app.core.site.douyu.inspectDouyuCookie
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import java.awt.BorderLayout
import java.awt.Dialog
import java.awt.Dimension
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.nio.file.Files
import java.util.Comparator
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*

internal fun loginCookieHeader(account: LiveSiteAccount, cookies: List<HttpCookie>): String {
    val domain = when (account) { LiveSiteAccount.BILIBILI -> "bilibili.com"; LiveSiteAccount.DOUYU -> "douyu.com"; LiveSiteAccount.DOUYIN -> "douyin.com" }
    val values = cookies.filter { cookie ->
        val host = cookie.domain.orEmpty().removePrefix(".").lowercase()
        !cookie.hasExpired() && (host == domain || host.endsWith(".$domain"))
    }.associate { it.name to it.value }
    val header = values.entries.joinToString("; ") { "${it.key}=${it.value}" }
    val authenticated = when (account) {
        LiveSiteAccount.BILIBILI -> !values["SESSDATA"].isNullOrBlank()
        LiveSiteAccount.DOUYU -> inspectDouyuCookie(header).let { it.hasLoginToken || it.canRenew }
        LiveSiteAccount.DOUYIN -> listOf("sessionid", "sessionid_ss", "sid_guard", "sid_tt").any { !values[it].isNullOrBlank() }
    }
    return if (authenticated) header else ""
}

/** Isolated in-app web login; it never reads an external browser's cookie database. */
object DesktopWebLogin {
    private val active = AtomicBoolean(false)
    private var initialized = false
    fun open(owner: Window, account: LiveSiteAccount, onCookie: (String) -> Unit, onError: (String) -> Unit) {
        check(SwingUtilities.isEventDispatchThread())
        if (!active.compareAndSet(false, true)) { onError("已有登录窗口，请先完成或关闭它。"); return }
        val previous = CookieHandler.getDefault()
        val manager = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
        val sessionDirectory = try { Files.createTempDirectory("mylive-web-session-").toAbsolutePath().normalize() }
        catch (_: Exception) { active.set(false); onError("无法创建登录会话目录，请使用 Cookie 导入。"); return }
        val dialog = try { JDialog(owner, "网页登录", Dialog.ModalityType.DOCUMENT_MODAL) }
        catch (_: Exception) { Files.deleteIfExists(sessionDirectory); active.set(false); onError("无法创建登录窗口，请使用 Cookie 导入。"); return }
        var engine: javafx.scene.web.WebEngine? = null
        fun deleteSession() { CompletableFuture.runAsync {
                // This directory was created exclusively for this session; never follow links.
                runCatching { Files.walk(sessionDirectory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { path ->
                    check(path.toAbsolutePath().normalize().startsWith(sessionDirectory)); Files.deleteIfExists(path)
                } } }
        } }
        val cleanup = DesktopLoginCleanup(manager, previous,
            schedule = { action -> if (initialized) Platform.runLater(action) else action() },
            stopBrowser = { runCatching { engine?.let { it.loadWorker.cancel(); it.load("about:blank") } }; deleteSession() },
            finished = { active.set(false) })
        try {
            CookieHandler.setDefault(manager)
            val panel = JFXPanel()
            initialized = true
            Platform.setImplicitExit(false)
            val status = JLabel("请在此窗口完成登录，再点击“完成登录”。")
            val complete = JButton("完成登录")
            val cancel = JButton("取消")
            val controls = JPanel(BorderLayout(12, 0)).apply {
                border = BorderFactory.createEmptyBorder(10, 12, 10, 12)
                add(status, BorderLayout.CENTER)
                add(JPanel().apply { add(complete); add(cancel) }, BorderLayout.EAST)
            }
            complete.addActionListener {
                val header = loginCookieHeader(account, manager.cookieStore.cookies)
                if (header.isEmpty()) status.text = "尚未检测到登录状态，请完成登录后重试。"
                else { cleanup.close(); dialog.dispose(); onCookie(header) }
            }
            cancel.addActionListener { cleanup.close(); dialog.dispose() }
            dialog.addWindowListener(object : WindowAdapter() { override fun windowClosing(event: WindowEvent) { cleanup.close() } })
            dialog.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
            dialog.contentPane.add(panel, BorderLayout.CENTER); dialog.contentPane.add(controls, BorderLayout.SOUTH)
            dialog.minimumSize = Dimension(900, 650); dialog.setSize(1080, 800); dialog.setLocationRelativeTo(owner)
            Platform.runLater {
                cleanup.ifOpen {
                try {
                    val view = WebView()
                    engine = view.engine
                    view.engine.userDataDirectory = sessionDirectory.toFile()
                    view.engine.userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
                    view.engine.setOnError { SwingUtilities.invokeLater { status.text = "页面加载遇到问题，可取消后使用 Cookie 导入。" } }
                    panel.scene = Scene(view)
                    view.engine.load(when (account) {
                        LiveSiteAccount.BILIBILI -> "https://passport.bilibili.com/login"
                        LiveSiteAccount.DOUYU -> "https://www.douyu.com/"
                        LiveSiteAccount.DOUYIN -> "https://live.douyin.com/"
                    })
                } catch (_: Exception) { SwingUtilities.invokeLater { status.text = "网页登录暂不可用，可取消后使用 Cookie 导入。" } }
                }
            }
            dialog.isVisible = true
        } catch (_: Exception) { onError("网页登录窗口启动失败，请使用 Cookie 导入。"); dialog.dispose() }
        finally { cleanup.close() }
    }
    fun shutdown() { if (initialized) Platform.exit() }
}
