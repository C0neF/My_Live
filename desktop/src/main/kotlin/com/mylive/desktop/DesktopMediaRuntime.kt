package com.mylive.desktop

import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MpvMediampPlayer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal suspend fun initializeDesktopRuntime(services: DesktopServices, runtime: File) = coroutineScope {
    launch(Dispatchers.IO) { prepareDesktopMediaRuntime(runtime) }
    launch { services.accounts.load() }
    launch { services.library.load() }
}

internal fun prepareDesktopMediaRuntime(runtime: File) {
    MPVHandle.setLogHandler { event ->
        if (event.isError) {
            DesktopDiagnostics.record("ERROR", "[mpv/${event.prefix}] ${event.line}")
            System.err.println("[mpv/${event.prefix}] ${sanitizeMediaLogLine(event.line)}")
        }
    }
    MpvMediampPlayer.prepareLibraries(runtime.absolutePath, extractRuntimeLibrary = false)
    DesktopVideoBridge.load(runtime)
}
