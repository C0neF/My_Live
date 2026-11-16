package com.mylive.desktop

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** All Windows icon sizes use the unchanged Android launcher artwork. */
object DesktopIcon {
    @JvmStatic fun main(args: Array<String>) {
        val source = Path.of(args[0]); val output = Path.of(args[1])
        Files.createDirectories(output.toAbsolutePath().parent)
        Files.write(output, encode(Files.readAllBytes(source)))
    }

    internal fun encode(artwork: ByteArray): ByteArray {
        val original = requireNotNull(ImageIO.read(ByteArrayInputStream(artwork))) { "Invalid Android icon artwork" }
        val sizes = listOf(16, 24, 32, 48, 64, 128, 256)
        val images = sizes.map { size ->
            val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            image.createGraphics().let { graphics ->
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                    graphics.drawImage(original, 0, 0, size, size, null)
                } finally { graphics.dispose() }
            }
            ByteArrayOutputStream().use { output -> ImageIO.write(image, "png", output); output.toByteArray() }
        }
        val header = ByteBuffer.allocate(6 + sizes.size * 16).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0).putShort(1).putShort(sizes.size.toShort())
        var offset = header.capacity()
        sizes.zip(images).forEach { (size, png) ->
            header.put(if (size == 256) 0 else size.toByte()).put(if (size == 256) 0 else size.toByte())
                .put(0).put(0).putShort(1).putShort(32).putInt(png.size).putInt(offset)
            offset += png.size
        }
        return ByteArrayOutputStream(offset).use { output ->
            output.write(header.array()); images.forEach(output::write); output.toByteArray()
        }
    }
}
