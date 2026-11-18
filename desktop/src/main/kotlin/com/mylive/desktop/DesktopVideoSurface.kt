@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.mylive.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.LocalWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.SkikoProperties
import org.jetbrains.skiko.hostOs
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.compose.MpvMediampPlayerSurface
import org.openani.mediamp.mpv.utils.findSkiaLayer
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener

internal data class DesktopVideoUploadStats(val cpuBuffers: Long = 0, val gpuTextures: Long = 0, val uploads: Long = 0)

@OptIn(InternalMediampApi::class)
@Composable
internal fun DesktopVideoSurface(playback: MpvDesktopPlayback, modifier: Modifier = Modifier, scaleMode: DesktopVideoScale = DesktopVideoScale.Fit) {
    val player = playback.player
    val window = LocalWindow.current
    val directGpu = remember(player, window) {
        val layer = window?.findSkiaLayer()
        hostOs == OS.Windows && SkikoProperties.renderApi == GraphicsApi.OPENGL &&
            System.getProperty("mylive.video.upload") != "legacy" &&
            layer != null && isDesktopGlWindowContextSupported(layer) &&
            DesktopVideoBridge.enable((player.impl as MPVHandle).ptr)
    }
    if (directGpu) DesktopOpenGlVideoSurface(playback, modifier, scaleMode) else MpvMediampPlayerSurface(player, modifier)
}

@OptIn(InternalMediampApi::class)
@Composable
private fun DesktopOpenGlVideoSurface(playback: MpvDesktopPlayback, modifier: Modifier, scaleMode: DesktopVideoScale) {
    val player = playback.player
    val playerPtr = (player.impl as MPVHandle).ptr
    val window = LocalWindow.current!!
    val interop = remember(player, window) { player.createSkiaInterop(window.findSkiaLayer()!!)!! }
    val frames = remember(player, window) { DesktopGpuVideoFrames(playerPtr, window) }
    val frameTick = remember { mutableLongStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    DisposableEffect(player, frames) {
        val peerListener = HierarchyListener { event ->
            if ((event.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong()) != 0L && window.isDisplayable) {
                DesktopVideoBridge.refresh(playerPtr)
                frameTick.longValue++
            }
        }
        window.addHierarchyListener(peerListener)
        player.setRenderUpdateListener { frameTick.longValue++ }
        onDispose {
            window.removeHierarchyListener(peerListener)
            player.setRenderUpdateListener(null)
            frames.close()
            player.releaseSurface()
            playback.videoUploadStats = DesktopVideoUploadStats()
        }
    }
    LaunchedEffect(player) {
        var configured = false
        snapshotFlow { canvasSize }.filter { it.width > 0 && it.height > 0 }.collectLatest {
            if (configured) delay(150)
            check(player.requestSurface(it.width, it.height, 0L))
            configured = true
        }
    }
    Canvas(modifier.onSizeChanged { canvasSize = it }) {
        frameTick.longValue
        val context = interop.directContext
        if (context == null) {
            DesktopVideoBridge.refresh(playerPtr)
            return@Canvas
        }
        frames.frame(context)?.let { frame ->
            val horizontal = size.width / frame.width
            val vertical = size.height / frame.height
            val scale = if (scaleMode == DesktopVideoScale.Crop) maxOf(horizontal, vertical) else minOf(horizontal, vertical)
            val width = if (scaleMode == DesktopVideoScale.Stretch) size.width else frame.width * scale
            val height = if (scaleMode == DesktopVideoScale.Stretch) size.height else frame.height * scale
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawImageRect(frame, Rect.makeWH(frame.width.toFloat(), frame.height.toFloat()),
                    Rect.makeXYWH((size.width - width) / 2f, (size.height - height) / 2f, width, height),
                    SamplingMode.LINEAR, null, true)
            }
        }
        playback.videoUploadStats = frames.stats
    }
}
