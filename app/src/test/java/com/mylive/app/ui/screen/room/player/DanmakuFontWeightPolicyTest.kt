package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuFontWeightPolicyTest {

    @Test
    fun rendererNormalizesLegacyCssAndCompactFontWeights() {
        assertEquals(2, normalizeDanmakuFontWeight(2))
        assertEquals(2, normalizeDanmakuFontWeight(200))
        assertEquals(4, normalizeDanmakuFontWeight(4))
        assertEquals(4, normalizeDanmakuFontWeight(400))
        assertEquals(6, normalizeDanmakuFontWeight(6))
        assertEquals(6, normalizeDanmakuFontWeight(600))
        assertEquals(8, normalizeDanmakuFontWeight(8))
        assertEquals(8, normalizeDanmakuFontWeight(800))
        assertEquals(4, normalizeDanmakuFontWeight(123))
    }
}
