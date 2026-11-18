package com.mylive.desktop

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

class DesktopIconTest {
    @Test fun desktopWindowArtworkIsTheExactAndroidLauncherAsset() {
        val android = Files.readAllBytes(Path.of("../app/src/main/res/drawable-nodpi/ic_launcher_foreground_art.png"))
        val desktop = requireNotNull(javaClass.getResourceAsStream("/icons/mylive.png")).use { it.readBytes() }
        assertArrayEquals(android, desktop)
    }

    @Test fun installerIconContainsDecodableImagesForTaskbarAndExplorerSizes() {
        val artwork = requireNotNull(javaClass.getResourceAsStream("/icons/mylive.png")).use { it.readBytes() }
        val ico = DesktopIcon.encode(artwork)
        val header = ByteBuffer.wrap(ico).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, header.short.toInt())
        assertEquals(1, header.short.toInt())
        val count = header.short.toInt()
        assertEquals(7, count)
        val sizes = mutableListOf<Int>()
        repeat(count) {
            val width = (header.get().toInt() and 255).let { if (it == 0) 256 else it }
            val height = (header.get().toInt() and 255).let { if (it == 0) 256 else it }
            header.get(); header.get(); header.short; header.short
            val length = header.int; val offset = header.int
            val decoded = requireNotNull(ImageIO.read(ByteArrayInputStream(ico, offset, length)))
            assertEquals(width, decoded.width); assertEquals(height, decoded.height)
            assertTrue(decoded.colorModel.hasAlpha())
            sizes += width
        }
        assertEquals(listOf(16, 24, 32, 48, 64, 128, 256), sizes)
    }
}
