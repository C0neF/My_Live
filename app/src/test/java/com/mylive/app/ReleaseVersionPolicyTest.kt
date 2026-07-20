package com.mylive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseVersionPolicyTest {

    @Test
    fun v2UsesVersionTwoZeroFour() {
        assertEquals("2.0.4", BuildConfig.VERSION_NAME)
        assertEquals(24, BuildConfig.VERSION_CODE)
    }
}
