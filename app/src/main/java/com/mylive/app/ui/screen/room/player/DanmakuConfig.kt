package com.mylive.app.ui.screen.room.player

/**
 * Single object for surface danmaku presentation knobs.
 * Shrinks the wide PlayerView parameter surface without changing runtime behavior.
 */
data class DanmakuConfig(
    val size: Double = 16.0,
    val speed: Double = 10.0,
    val area: Double = 0.8,
    val lineCount: Int = 8,
    val delayMs: Int = 0,
    val opacity: Double = 1.0,
    val fontWeight: Int = 4,
    val strokeWidth: Double = 2.0,
    val topMargin: Double = 0.0,
    val bottomMargin: Double = 0.0,
    val hideScroll: Boolean = false,
    val hideTop: Boolean = false,
    val hideBottom: Boolean = false,
    val dedupeEnable: Boolean = false,
    val dedupeWindow: Int = 10,
    val dedupeStep: Int = 2,
    val dedupeStrictMode: Boolean = false,
    val renderEmoji: Boolean = true
)

internal fun applyDanmakuConfig(controller: DanmakuController, config: DanmakuConfig) {
    controller.danmuSize = config.size.toFloat()
    controller.danmuSpeed = config.speed.toFloat()
    controller.danmuArea = config.area.toFloat()
    controller.danmuLineCount = config.lineCount
    controller.danmuDelayMs = config.delayMs
    controller.danmuOpacity = config.opacity.toFloat()
    controller.danmuFontWeight = config.fontWeight
    controller.danmuStrokeWidth = config.strokeWidth.toFloat()
    controller.danmuTopMargin = config.topMargin.toFloat()
    controller.danmuBottomMargin = config.bottomMargin.toFloat()
    controller.danmuHideScroll = config.hideScroll
    controller.danmuHideTop = config.hideTop
    controller.danmuHideBottom = config.hideBottom
    controller.dedupeEnabled = config.dedupeEnable
    controller.dedupeWindowSize = config.dedupeWindow
    controller.dedupeStepSize = config.dedupeStep
    controller.dedupeStrictMode = config.dedupeStrictMode
    controller.danmuRenderEmoji = config.renderEmoji
}
