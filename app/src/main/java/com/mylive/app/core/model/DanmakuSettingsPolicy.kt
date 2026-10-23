package com.mylive.app.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

/** Stored profiles may contain the old 16 sp font size and speed 10 sentinel. */
object DanmakuSettingsPolicy {
    const val BASE_FONT_SIZE = 16.0
    val sizeRange = 0.8f..1.5f
    val speedRange = 0.6f..1.5f
    val areaRange = 0.25f..1.0f
    val opacityRange = 0.2f..1.0f
    val strokeRange = 0f..4f
    val sizeValues = (8..15).map { it / 10.0 }
    val speedValues = (6..15).map { it / 10.0 }
    val areaValues = ((5..20).map { it / 20.0 } + listOf(0.333, 0.667)).sorted()
    val opacityValues = (2..10).map { it / 10.0 }
    val strokeValues = (0..8).map { it / 2.0 }
    val fontWeightValues = listOf(200, 400, 600, 800)

    fun sizeMultiplier(value: Double): Double =
        normalize(if (value <= 2.0) value else value / BASE_FONT_SIZE, 1.0, sizeRange)

    fun speedMultiplier(value: Double): Double =
        normalize(if (value > 5.0) 1.0 else value, 1.0, speedRange)

    fun area(value: Double): Double = normalize(value, 0.8, areaRange)
    fun opacity(value: Double): Double = normalize(value, 1.0, opacityRange)
    fun strokeWidth(value: Double): Double = normalize(value, 2.0, strokeRange)
    fun fontWeight(value: Int): Int = when (value) {
        2, 200 -> 200
        6, 600 -> 600
        8, 800 -> 800
        else -> 400
    }

    fun sameValue(first: Double, second: Double): Boolean = abs(first - second) < 0.0005

    fun choicesWithCurrent(values: List<Double>, current: Double): List<Double> =
        if (values.any { sameValue(it, current) }) values else (values + current).sorted()

    fun multiplierText(value: Double): String = "${decimalText(value, atLeastOneDecimal = true)}x"
    fun percentText(value: Double): String = "${(value * 100).roundToInt()}%"
    fun strokeText(value: Double): String = if (value <= 0.0) "无" else "${decimalText(value)} px"

    private fun normalize(value: Double, fallback: Double, range: ClosedFloatingPointRange<Float>): Double {
        val safe = (value.takeIf { it.isFinite() } ?: fallback)
            .coerceIn(range.start.toDouble(), range.endInclusive.toDouble())
        return round(safe * 1000) / 1000
    }

    private fun decimalText(value: Double, atLeastOneDecimal: Boolean = false): String {
        val text = BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        return if (atLeastOneDecimal && '.' !in text) "$text.0" else text
    }
}
