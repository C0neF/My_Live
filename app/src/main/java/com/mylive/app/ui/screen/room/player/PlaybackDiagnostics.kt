package com.mylive.app.ui.screen.room.player

import java.net.URI
import java.util.Locale

internal data class PlaybackAudioDiagnostics(
    val sampleMimeType: String? = null,
    val codecs: String? = null,
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
    val decoderName: String? = null,
    val outputSampleRate: Int? = null,
    val underrunCount: Long = 0,
    val sinkErrorCount: Long = 0,
    val timestampDiscontinuityCount: Long = 0,
    val lastTimestampDeltaUs: Long? = null
)

internal data class PlaybackDiagnosticInput(
    val qualityName: String,
    val currentUrl: String?,
    val currentLineIndex: Int,
    val lineCount: Int,
    val playbackState: String,
    val sampleMimeType: String?,
    val codecs: String?,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val bitrate: Int,
    val decoderName: String?,
    val droppedFrames: Long,
    val bufferingCount: Int,
    val stallRecoveryCount: Int,
    val bufferedMillis: Long? = null,
    val liveOffsetMillis: Long? = null,
    val firstFrameMillis: Long? = null,
    val outputFrameRate: Float? = null,
    val playMillis: Long = 0L,
    val rebufferMillis: Long = 0L,
    val recoveryAttempts: Int = 0,
    val lastRecoveryReason: String? = null,
    val roomFirstFrameMillis: Long? = null,
    val decoderConfiguredFrameRate: Float? = null,
    val audio: PlaybackAudioDiagnostics = PlaybackAudioDiagnostics()
)

internal data class PlaybackDiagnosticEntry(
    val label: String,
    val value: String
)

internal data class PlaybackDiagnosticSnapshot(
    val entries: List<PlaybackDiagnosticEntry>
) {
    fun valueFor(label: String): String? = entries.firstOrNull { it.label == label }?.value

    fun asText(): String = entries.joinToString("\n") { "${it.label}: ${it.value}" }
}

internal fun buildPlaybackDiagnosticSnapshot(
    input: PlaybackDiagnosticInput
): PlaybackDiagnosticSnapshot {
    val host = input.currentUrl?.let { rawUrl ->
        try {
            URI(rawUrl).host
        } catch (_: Exception) {
            null
        }
    }.orEmpty().ifBlank { "未知" }
    val line = if (input.lineCount > 0 && input.currentLineIndex in 0 until input.lineCount) {
        "${input.currentLineIndex + 1} / ${input.lineCount}"
    } else {
        "未知"
    }
    val resolution = if (input.width > 0 && input.height > 0) {
        "${input.width} × ${input.height}"
    } else {
        "未知"
    }
    val frameRate = input.frameRate.takeIf { it.isFinite() && it > 0f }
        ?.let { String.format(Locale.US, "%.1f fps", it) }
        ?: "未知"
    val bitrate = input.bitrate.takeIf { it > 0 }
        ?.let { String.format(Locale.US, "%.2f Mbps", it / 1_000_000.0) }
        ?: "未知"

    return PlaybackDiagnosticSnapshot(
        listOf(
            PlaybackDiagnosticEntry("请求画质", input.qualityName.ifBlank { "未知" }),
            PlaybackDiagnosticEntry("线路", line),
            PlaybackDiagnosticEntry("线路域名", host),
            PlaybackDiagnosticEntry("播放状态", input.playbackState),
            PlaybackDiagnosticEntry("视频 MIME", input.sampleMimeType.orEmpty().ifBlank { "未知" }),
            PlaybackDiagnosticEntry("视频编码", input.codecs.orEmpty().ifBlank { "未知" }),
            PlaybackDiagnosticEntry("源分辨率", resolution),
            PlaybackDiagnosticEntry("源帧率", frameRate),
            PlaybackDiagnosticEntry("解码帧率配置", input.decoderConfiguredFrameRate
                ?.takeIf { it.isFinite() && it > 0f }
                ?.let { String.format(Locale.US, "%.1f fps", it) } ?: "未指定"),
            PlaybackDiagnosticEntry("声明码率", bitrate),
            PlaybackDiagnosticEntry("实际解码器", input.decoderName.orEmpty().ifBlank { "未知" }),
            PlaybackDiagnosticEntry("音频 MIME", input.audio.sampleMimeType.orEmpty().ifBlank { "未知" }),
            PlaybackDiagnosticEntry("音频编码", input.audio.codecs.orEmpty().ifBlank { "未知" }),
            PlaybackDiagnosticEntry("音频采样率", input.audio.sampleRate?.takeIf { it > 0 }?.let { "$it Hz" } ?: "未知"),
            PlaybackDiagnosticEntry("音频声道", input.audio.channelCount?.takeIf { it > 0 }?.toString() ?: "未知"),
            PlaybackDiagnosticEntry("音频解码器", input.audio.decoderName.orEmpty().ifBlank { "未知" }),
            PlaybackDiagnosticEntry("音频输出采样率", input.audio.outputSampleRate?.takeIf { it > 0 }?.let { "$it Hz" } ?: "未知"),
            PlaybackDiagnosticEntry("音频缓冲欠载次数", input.audio.underrunCount.toString()),
            PlaybackDiagnosticEntry("音频输出异常次数", input.audio.sinkErrorCount.toString()),
            PlaybackDiagnosticEntry("音频时间戳跳变次数", input.audio.timestampDiscontinuityCount.toString()),
            PlaybackDiagnosticEntry("最近音频时间戳偏差", input.audio.lastTimestampDeltaUs
                ?.let { String.format(Locale.US, "%.2f ms", it / 1000.0) } ?: "无"),
            PlaybackDiagnosticEntry("丢帧", input.droppedFrames.toString()),
            PlaybackDiagnosticEntry("缓冲次数", input.bufferingCount.toString()),
            PlaybackDiagnosticEntry("停滞恢复次数", input.stallRecoveryCount.toString()),
            PlaybackDiagnosticEntry("播放器首帧", input.firstFrameMillis?.let { "$it ms" } ?: "等待首帧"),
            PlaybackDiagnosticEntry("进房首帧（含请求）", input.roomFirstFrameMillis?.let { "$it ms" } ?: "等待首帧"),
            PlaybackDiagnosticEntry("视频输出帧率（近段）", input.outputFrameRate?.let { String.format(Locale.US, "%.1f fps", it) } ?: "未采样"),
            PlaybackDiagnosticEntry("已缓冲", input.bufferedMillis?.let { "$it ms" } ?: "未知"),
            PlaybackDiagnosticEntry("直播延迟", input.liveOffsetMillis?.let { "$it ms" } ?: "当前流不提供"),
            PlaybackDiagnosticEntry("播放阶段再缓冲", String.format(Locale.US, "%.1f s / %.1f%%",
                input.rebufferMillis / 1000.0,
                if (input.playMillis + input.rebufferMillis > 0) input.rebufferMillis * 100.0 / (input.playMillis + input.rebufferMillis) else 0.0)),
            PlaybackDiagnosticEntry("当前恢复预算已用", "${input.recoveryAttempts} / $MAX_PLAYBACK_RECOVERY_ATTEMPTS"),
            PlaybackDiagnosticEntry("最近恢复原因", input.lastRecoveryReason ?: "无")
        )
    )
}

internal fun PlaybackDiagnosticSnapshot.withDanmaku(danmaku: DanmakuRenderDiagnostics?): PlaybackDiagnosticSnapshot {
    if (danmaku == null) return this
    return copy(entries = entries + listOf(
        PlaybackDiagnosticEntry("弹幕屏内 / 排队", "${danmaku.activeCount} / ${danmaku.pendingCount}"),
        PlaybackDiagnosticEntry("弹幕丢弃", danmaku.droppedCount.toString()),
        PlaybackDiagnosticEntry("弹幕最近绘制耗时", String.format(Locale.US, "%.2f ms", danmaku.lastDrawMillis)),
        PlaybackDiagnosticEntry("弹幕图片缓存", String.format(Locale.US, "%.2f MB", danmaku.cacheBytes / 1048576.0))
    ))
}
