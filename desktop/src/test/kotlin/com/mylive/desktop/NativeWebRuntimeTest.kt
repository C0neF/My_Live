package com.mylive.desktop

import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/** Loads the bundled WebKit and executes a local document without automating a user window. */
class NativeWebRuntimeTest {
    @Test fun embeddedBrowserLoadsLocalHtmlAndExecutesJavaScript() {
        val result = CompletableFuture<Pair<String, Int>>()
        lateinit var panel: JFXPanel
        SwingUtilities.invokeAndWait { panel = JFXPanel() }
        try {
            Platform.runLater {
                try {
                    val view = WebView()
                    panel.scene = Scene(view)
                    view.engine.loadWorker.stateProperty().addListener { _, _, state ->
                        if (state == Worker.State.SUCCEEDED) {
                            try { result.complete((view.engine.executeScript("document.title") as String) to (view.engine.executeScript("6 * 7") as Number).toInt()) }
                            catch (error: Throwable) { result.completeExceptionally(error) }
                        } else if (state == Worker.State.FAILED) result.completeExceptionally(IllegalStateException("Local WebKit load failed"))
                    }
                    view.engine.loadContent("<html><head><title>My Live runtime probe</title></head><body>local fixture</body></html>")
                } catch (error: Throwable) { result.completeExceptionally(error) }
            }
            val (title, number) = result.get(25, TimeUnit.SECONDS)
            assertEquals("My Live runtime probe", title)
            assertEquals(42, number)
        } finally { Platform.runLater { panel.scene = null; Platform.exit() } }
    }
}
