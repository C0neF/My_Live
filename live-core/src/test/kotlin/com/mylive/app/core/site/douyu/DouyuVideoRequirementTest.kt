package com.mylive.app.core.site.douyu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DouyuVideoRequirementTest {
    @Test fun highFrameRateOriginalHasTheResolutionAndRateNeededForADecoderQuery() {
        assertEquals(DouyuVideoRequirement(2560, 1440, 120.0), douyuVideoRequirement("原画2K120"))
        assertEquals(DouyuVideoRequirement(1920, 1080, 60.0), douyuVideoRequirement("原画1080P60"))
        assertEquals(DouyuVideoRequirement(3840, 2160, 60.0), douyuVideoRequirement("原画4k60fps"))
        assertEquals(DouyuVideoRequirement(2560, 1440, 120.0), douyuVideoRequirement("原画（1440P 120帧）"))
    }

    @Test fun bitrateAndUnspecifiedFrameRatesDoNotInventVideoRequirements() {
        listOf("蓝光8M", "蓝光4M", "原画", "原画2K", "1080P", "原画2K000", "原画2K1200")
            .forEach { assertNull(it, douyuVideoRequirement(it)) }
    }
}
