package com.mylive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseVersionPolicyTest {

    @Test
    fun v2UsesVersionTwoZeroSix() {
        assertEquals("2.0.6", BuildConfig.VERSION_NAME)
        assertEquals(26, BuildConfig.VERSION_CODE)
    }
}
