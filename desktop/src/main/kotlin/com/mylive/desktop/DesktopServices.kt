package com.mylive.desktop

import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.site.LiveSite
import com.mylive.app.core.site.LiveSiteAccount
import com.mylive.app.core.site.LiveSiteAccountStore
import com.mylive.app.core.site.bilibili.BiliBiliSite
import com.mylive.app.core.site.douyin.DouyinSite
import com.mylive.app.core.site.douyu.DouyuAccountSession
import com.mylive.app.core.site.douyu.DouyuPlaybackSigner
import com.mylive.app.core.site.douyu.DouyuSite
import com.mylive.app.core.site.huya.HuyaSite
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Provider

class DesktopServices : AutoCloseable {
    val accounts = DesktopAccounts()
    val library = DesktopLibrary()
    private val jsRuntime = lazy { GraalJsExecutor() }
    private val js by jsRuntime
    internal val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(BrotliInterceptor)
        .build()
    private val http = HttpClient(client)
    val webDav = DesktopWebDav(client)
    val lanServer = DesktopLanServer(library)
    val lanClient = DesktopLanClient(client)
    val douyuSession = DouyuAccountSession(accounts, http)
    val login = DesktopLoginService(http, accounts)
    val sites: List<LiveSite> = listOf(
        BiliBiliSite(http, client, accounts),
        DouyuSite(http, douyuDesktopWebSocketClient(client), douyuSession, DouyuPlaybackSigner(http)),
        HuyaSite(http, client),
        DouyinSite(http, Provider { js }, client, accounts)
    )

    override fun close() {
        lanServer.close()
        if (jsRuntime.isInitialized()) js.close()
        accounts.clearMemory()
        client.dispatcher.cancelAll()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
