package com.mylive.desktop

import com.mylive.app.core.site.LiveSiteAccount
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.web.WebView
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.openani.mediamp.mpv.EventListener
import org.openani.mediamp.mpv.MPVHandle
import org.jetbrains.skiko.SkikoProperties
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/** Runs only when explicitly requested. All writes go under the supplied QA directory. */
object DesktopSelfCheck {
    fun run(directory: Path, media: String?): Boolean {
        Files.createDirectories(directory)
        val result = JSONObject().put("version", DESKTOP_VERSION).put("time", Instant.now().toString())
            .put("configuredUiRenderApi", SkikoProperties.renderApi.name)
            .put("appName", DESKTOP_APP_NAME).put("uiResourceCacheBudgetBytes", SkikoProperties.gpuResourceCacheLimit)
        val checks = JSONObject(); result.put("checks", checks)
        var fxStarted = false
        try {
            System.setProperty("prism.order", "sw")
            runBlocking {
                val library = DesktopLibrary(directory.resolve("profile/library.json"))
                library.load()
                check(library.updatePlayer { it.copy(volume = 23f, muted = true) })
                check(library.importRooms(RoomProfileImport(listOf(SavedRoom("douyu", "fixture", "QA", "Self check", savedAt = 1, tags = listOf("QA"))), emptyList())))
                val restored = DesktopLibrary(directory.resolve("restored/library.json"))
                check(restored.importRooms(RoomProfileCodec.parse(RoomProfileCodec.export(library.state.value))))
                check(restored.state.value.player.volume == 23f && restored.state.value.follows.single().tags == listOf("QA"))
                checks.put("profile_roundtrip", true)
                val accounts = DesktopAccounts(directory.resolve("profile/credentials"))
                check(accounts.update(LiveSiteAccount.DOUYU, "test_cookie=packaged-self-check", true))
                val restarted = DesktopAccounts(directory.resolve("profile/credentials")); restarted.load()
                check(restarted.cookieFor(LiveSiteAccount.DOUYU) == "test_cookie=packaged-self-check")
                check(restarted.update(LiveSiteAccount.DOUYU, "", false)); accounts.clearMemory(); restarted.clearMemory()
                checks.put("windows_credentials", true)
                GraalJsExecutor().use { js ->
                    check(js.execute("douyin_abogus.js", "getABogus", "room_id=123", "Mozilla/5.0").length > 20)
                    check(js.execute("douyin_webmssdk.js", "getMSSDKSignature", "0123456789abcdef0123456789abcdef", "Mozilla/5.0").length > 10)
                }
                checks.put("bundled_signing_scripts", true)
                val http = OkHttpClient()
                try { DesktopLanServer(restored).use { server ->
                    server.start("127.0.0.1", 0, discover = false)
                    DesktopLanClient(http).send("127.0.0.1", server.state.value.port, server.token, RoomProfileCodec.export(library.state.value))
                    check(restored.state.value.follows.size == 1)
                } } finally { http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll() }
                checks.put("encrypted_local_sync", true)
                if (media != null) checks.put("native_media", decode(media))
            }
            SwingUtilities.invokeAndWait { JFXPanel() }
            fxStarted = true
            Platform.setImplicitExit(false)
            val evaluated = CompletableFuture<Boolean>()
            Platform.runLater { try { evaluated.complete(WebView().engine.executeScript("1+41") == 42) } catch (error: Throwable) { evaluated.completeExceptionally(error) } }
            check(evaluated.get(15, TimeUnit.SECONDS))
            checks.put("native_web_javascript", true)
            checks.put("jvm_http_server_module", ModuleLayer.boot().findModule("jdk.httpserver").isPresent)
            checks.put("jvm_flight_recorder_module", ModuleLayer.boot().findModule("jdk.jfr").isPresent)
            result.put("passed", true)
        } catch (error: Throwable) {
            result.put("passed", false).put("errorType", error.javaClass.simpleName).put("message", redactDesktopLog(error.message.orEmpty()))
        } finally {
            if (fxStarted) Platform.exit()
            writeDesktopFileAtomically(directory.resolve("self-check.json"), result.toString(2).toByteArray(Charsets.UTF_8))
        }
        return result.optBoolean("passed")
    }

    private suspend fun decode(media: String): JSONObject = MPVHandle(Any()).use { handle ->
        handle.setEventListener(object : EventListener {
            override fun onPropertyChange(name: String) {}
            override fun onPropertyChange(name: String, value: Boolean) {}
            override fun onPropertyChange(name: String, value: Long) {}
            override fun onPropertyChange(name: String, value: Double) {}
            override fun onPropertyChange(name: String, value: String) {}
            override fun onEvent(event: Int) {}
            override fun onStartFile(playlistEntryId: Long) {}
            override fun onEndFile(reason: Int, mpvError: Int, playlistEntryId: Long) {}
        })
        check(handle.option("vo", "null")); check(handle.option("ao", "null")); check(handle.option("keep-open", "yes"))
        check(handle.initialize()); check(handle.command("loadfile", normalizeMediaLocation(media)))
        withTimeout(10_000) { while (handle.getPropertyDouble("time-pos") < .1 || handle.getPropertyInt("width") <= 0) delay(25) }
        JSONObject().put("width", handle.getPropertyInt("width")).put("height", handle.getPropertyInt("height"))
            .put("codec", handle.getPropertyString("video-format")).put("decoded", true)
    }
}
