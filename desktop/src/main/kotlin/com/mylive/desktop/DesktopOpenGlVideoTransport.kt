package com.mylive.desktop

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HDC
import com.sun.jna.platform.win32.WinGDI.PIXELFORMATDESCRIPTOR
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import org.jetbrains.skia.BackendTexture
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.SurfaceOrigin
import org.jetbrains.skiko.SkiaLayer
import org.openani.mediamp.mpv.utils.findSkiaLayer
import java.awt.Window
import java.io.File

internal interface DesktopVideoBridgeApi : Library {
    fun myliveEnableGpuTransport(player: Long): Int
    fun myliveAcquireGpuFrame(player: Long, frame: LongArray): Int
    fun myliveReleaseGpuFrame(player: Long, state: Long)
    fun myliveRefreshGpuFrame(player: Long)
}

internal object DesktopVideoBridge {
    private lateinit var libraryFile: File
    private val api by lazy { Native.load(libraryFile.absolutePath, DesktopVideoBridgeApi::class.java) }
    fun load(runtime: File) {
        libraryFile = File(runtime, "mediampv.dll")
    }
    fun enable(player: Long) = api.myliveEnableGpuTransport(player) != 0
    fun acquire(player: Long, output: LongArray) = api.myliveAcquireGpuFrame(player, output) != 0
    fun release(player: Long, state: Long) = api.myliveReleaseGpuFrame(player, state)
    fun refresh(player: Long) = api.myliveRefreshGpuFrame(player)
}

private interface DesktopOpenGlApi : StdCallLibrary {
    fun wglGetCurrentContext(): Pointer?
    fun wglGetCurrentDC(): HDC?
    fun wglCreateContext(dc: HDC): Pointer?
    fun wglShareLists(source: Pointer, destination: Pointer): Boolean
    fun wglMakeCurrent(dc: HDC?, context: Pointer?): Boolean
    fun wglDeleteContext(context: Pointer): Boolean
    fun wglGetProcAddress(name: String): Pointer?
    fun glGenTextures(count: Int, texture: IntByReference)
    fun glBindTexture(target: Int, texture: Int)
    fun glTexParameteri(target: Int, name: Int, value: Int)
    fun glFlush()
    fun glFinish()
}

private interface DesktopPixelFormatApi : StdCallLibrary {
    fun GetPixelFormat(dc: HDC): Int
    fun DescribePixelFormat(dc: HDC, format: Int, size: Int, descriptor: PIXELFORMATDESCRIPTOR.ByReference): Int
    fun SetPixelFormat(dc: HDC, format: Int, descriptor: PIXELFORMATDESCRIPTOR.ByReference): Boolean
}

private val DesktopGl by lazy { Native.load("opengl32", DesktopOpenGlApi::class.java) }
private val DesktopPixelFormat by lazy { Native.load("gdi32", DesktopPixelFormatApi::class.java) }
private const val GL_TEXTURE_2D = 0x0DE1
private const val GL_RGBA8 = 0x8058

/** Compose records each window before Skiko draws it, so another window's GL
 * context can still be current during this window's recording pass. */
private class DesktopGlWindowContext(private val layer: SkiaLayer) {
    private val getRedrawer = SkiaLayer::class.java.getMethod("getRedrawer\$skiko")
    private val redrawerClass = Class.forName("org.jetbrains.skiko.redrawer.WindowsOpenGLRedrawer")
    private val deviceField = redrawerClass.getDeclaredField("device").apply { isAccessible = true }
    private val contextField = redrawerClass.getDeclaredField("context").apply { isAccessible = true }
    private val makeCurrent = Class.forName("org.jetbrains.skiko.redrawer.WindowsOpenGLRedrawerKt")
        .getDeclaredMethod("makeCurrent", java.lang.Long.TYPE, java.lang.Long.TYPE).apply { isAccessible = true }
    private var redrawerToken = 0L
    private var dc: HDC? = null
    private var context: Pointer? = null

    fun makeCurrent() {
        val redrawer = getRedrawer.invoke(layer)
        val token = contextField.getLong(redrawer)
        if (token != redrawerToken) {
            makeCurrent.invoke(null, deviceField.getLong(redrawer), token)
            context = DesktopGl.wglGetCurrentContext()
            dc = DesktopGl.wglGetCurrentDC()
            redrawerToken = token
        } else if (DesktopGl.wglGetCurrentContext() != context) {
            check(DesktopGl.wglMakeCurrent(dc, context))
        }
    }
}

/** Keeps consumer resources alive until their final GPU use has completed. */
private class DesktopGlCleanupContext(owner: Window) : AutoCloseable {
    private val window = User32.INSTANCE.CreateWindowEx(0, "STATIC", "MyLive video resources", WinUser.WS_POPUP,
        owner.x, owner.y, 1, 1, null, null, null, null)
    private val dc = User32.INSTANCE.GetDC(window)
    private val context: Pointer
    init {
        val sourceContext = DesktopGl.wglGetCurrentContext()!!
        val sourceDc = DesktopGl.wglGetCurrentDC()!!
        val format = DesktopPixelFormat.GetPixelFormat(sourceDc)
        val descriptor = PIXELFORMATDESCRIPTOR.ByReference()
        DesktopPixelFormat.DescribePixelFormat(sourceDc, format, descriptor.size(), descriptor)
        check(DesktopPixelFormat.SetPixelFormat(dc, format, descriptor))
        context = DesktopGl.wglCreateContext(dc)!!
        check(DesktopGl.wglShareLists(sourceContext, context))
    }
    fun <T> useCurrent(block: () -> T): T {
        val previousContext = DesktopGl.wglGetCurrentContext()
        val previousDc = DesktopGl.wglGetCurrentDC()
        check(DesktopGl.wglMakeCurrent(dc, context))
        return try { block() } finally { DesktopGl.wglMakeCurrent(previousDc, previousContext) }
    }
    override fun close() {
        DesktopGl.wglDeleteContext(context)
        User32.INSTANCE.ReleaseDC(window, dc)
        User32.INSTANCE.DestroyWindow(window)
    }
}

internal class DesktopGpuVideoFrames(private val player: Long, private val owner: Window) : AutoCloseable {
    private data class Slot(val id: Int, val descriptor: BackendTexture, val image: Image) {
        fun close() { image.close(); descriptor.close() }
    }
    private class Frame(val state: Long, val slot: Slot) { var fence: Pointer? = null }
    private var cleanupContext: DesktopGlCleanupContext? = null
    private val windowContext = DesktopGlWindowContext(owner.findSkiaLayer()!!)
    private var contextToken = 0L
    private val slots = mutableMapOf<Long, Slot>()
    private val retired = mutableListOf<Frame>()
    private var current: Frame? = null
    private val nativeFrame = LongArray(2)
    private lateinit var bindImage: Function
    private lateinit var fenceSync: Function
    private lateinit var waitSync: Function
    private lateinit var clientWaitSync: Function
    private lateinit var deleteSync: Function
    private var textureAllocations = 0L
    private var framesReceived = 0L
    val stats get() = DesktopVideoUploadStats(0, textureAllocations, framesReceived)

    private fun function(name: String) = Function.getFunction(DesktopGl.wglGetProcAddress(name)!!, Function.ALT_CONVENTION)
    private fun attachContext() {
        val token = Pointer.nativeValue(DesktopGl.wglGetCurrentContext())
        if (token == contextToken) return
        releaseAll()
        cleanupContext?.close()
        cleanupContext = DesktopGlCleanupContext(owner)
        contextToken = token
        bindImage = function("wglBindTexImageARB")
        fenceSync = function("glFenceSync")
        waitSync = function("glWaitSync")
        clientWaitSync = function("glClientWaitSync")
        deleteSync = function("glDeleteSync")
        DesktopVideoBridge.refresh(player)
    }

    fun frame(context: DirectContext): Image? {
        windowContext.makeCurrent()
        attachContext()
        current?.let { frame ->
            // Compose records the video draw before Skiko replays it on the GPU.
            // The next draw pass can fence that completed recording's GPU work.
            context.flush().submit(false)
            frame.fence?.let { deleteSync.invokeVoid(arrayOf(it)) }
            frame.fence = fenceSync.invokePointer(arrayOf(0x9117, 0))
            DesktopGl.glFlush()
        }
        retired.removeAll { frame ->
            val status = clientWaitSync.invokeInt(arrayOf(frame.fence, 0, 0L))
            if (status != 0x911A && status != 0x911C) return@removeAll false
            release(frame)
            true
        }
        if (!DesktopVideoBridge.acquire(player, nativeFrame)) return current?.slot?.image
        context.flush().submit(false)
        current?.let { retired.add(it) }
        val state = nativeFrame[0]
        val width = ((state ushr 30) and 0x3FFF).toInt()
        val height = ((state ushr 16) and 0x3FFF).toInt()
        val key = state ushr 44
        val existingSlot = slots[key]
        val slot = existingSlot ?: run {
            val texture = IntByReference()
            DesktopGl.glGenTextures(1, texture)
            DesktopGl.glBindTexture(GL_TEXTURE_2D, texture.value)
            DesktopGl.glTexParameteri(GL_TEXTURE_2D, 0x2801, 0x2601)
            DesktopGl.glTexParameteri(GL_TEXTURE_2D, 0x2800, 0x2601)
            DesktopGl.glTexParameteri(GL_TEXTURE_2D, 0x2802, 0x812F)
            DesktopGl.glTexParameteri(GL_TEXTURE_2D, 0x2803, 0x812F)
            check(bindImage.invokeInt(arrayOf(Pointer(nativeFrame[1]), 0x2083)) != 0)
            val descriptor = BackendTexture.makeGL(width, height, false, texture.value, GL_TEXTURE_2D, GL_RGBA8)
            context.resetGLAll()
            textureAllocations++
            Slot(texture.value, descriptor, Image.adoptTextureFrom(context, descriptor, SurfaceOrigin.BOTTOM_LEFT, ColorType.RGBA_8888))
                .also { slots[key] = it }
        }
        // New slots were bound before Skia adopted them; existing slots rebind the
        // same pbuffer storage after their previous use was returned to the producer.
        if (existingSlot != null) {
            DesktopGl.glBindTexture(GL_TEXTURE_2D, slot.id)
            check(bindImage.invokeInt(arrayOf(Pointer(nativeFrame[1]), 0x2083)) != 0)
        }
        current = Frame(state, slot)
        slot.descriptor.glTextureParametersModified()
        context.resetGLAll()
        framesReceived++
        slots.entries.removeAll { (slotKey, value) ->
            val oldGeneration = (slotKey ushr 4) != (key ushr 4)
            if (!oldGeneration || retired.any { it.slot === value }) return@removeAll false
            value.close()
            true
        }
        return slot.image
    }

    private fun release(frame: Frame) {
        frame.fence?.let { deleteSync.invokeVoid(arrayOf(it)) }
        DesktopVideoBridge.release(player, frame.state)
    }
    private fun releaseAll() {
        if (cleanupContext == null) return
        cleanupContext!!.useCurrent {
            val frames = retired + listOfNotNull(current)
            frames.forEach { frame -> frame.fence?.let { waitSync.invokeVoid(arrayOf(it, 0, -1L)) } }
            DesktopGl.glFinish()
            frames.forEach(::release)
            slots.values.forEach { it.close() }
        }
        retired.clear()
        current = null
        slots.clear()
    }
    override fun close() {
        releaseAll()
        cleanupContext?.close()
        cleanupContext = null
        contextToken = 0L
    }
}
