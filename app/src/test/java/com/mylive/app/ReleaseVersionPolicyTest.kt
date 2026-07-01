package com.mylive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseVersionPolicyTest {

    @Test
    fun v2UsesVersionTwoZeroOne() {
        assertEquals("2.0.1", BuildConfig.VERSION_NAME)
        assertEquals(21, BuildConfig.VERSION_CODE)
    }
}
