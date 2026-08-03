package com.mylive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseVersionPolicyTest {

    @Test
    fun v2UsesVersionTwoZeroEight() {
        assertEquals("2.0.8", BuildConfig.VERSION_NAME)
        assertEquals(28, BuildConfig.VERSION_CODE)
    }
}
