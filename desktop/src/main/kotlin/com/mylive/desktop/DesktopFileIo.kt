package com.mylive.desktop

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.nio.file.StandardOpenOption.*

internal suspend fun <T> withDesktopFileLock(file: Path, action: () -> T): T {
    Files.createDirectories(file.toAbsolutePath().parent)
    return FileChannel.open(file.resolveSibling("${file.fileName}.lock"), CREATE, WRITE).use { channel ->
        val lock = withTimeoutOrNull(2_000) {
            var result: java.nio.channels.FileLock? = null
            while (result == null) {
                result = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
                if (result == null) delay(20)
            }
            result
        } ?: throw IOException("Data file is busy")
        lock.use { action() }
    }
}

internal fun writeDesktopFileAtomically(file: Path, bytes: ByteArray) {
    val temporary = Files.createTempFile(file.toAbsolutePath().parent, "mylive-", ".tmp")
    try {
        FileChannel.open(temporary, WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        try { Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, file, REPLACE_EXISTING) }
    } finally { Files.deleteIfExists(temporary) }
}
