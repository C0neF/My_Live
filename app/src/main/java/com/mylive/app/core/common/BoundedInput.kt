package com.mylive.app.core.common

import java.io.ByteArrayOutputStream
import java.io.InputStream

const val MAX_IMPORTED_JSON_BYTES = 5 * 1024 * 1024

fun InputStream.readUtf8TextWithinLimit(
    maxBytes: Int = MAX_IMPORTED_JSON_BYTES
): String {
    require(maxBytes > 0) { "读取大小限制必须大于 0" }
    val output = ByteArrayOutputStream(minOf(DEFAULT_BUFFER_SIZE, maxBytes))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var totalBytes = 0
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        totalBytes += read
        require(totalBytes <= maxBytes) { "导入文件不能超过 ${maxBytes / 1024 / 1024} MB" }
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}
