package com.mylive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseVersionPolicyTest {

    @Test
    fun v2UsesVersionTwoZeroZero() {
        assertEquals("2.0.0", BuildConfig.VERSION_NAME)
        assertEquals(20, BuildConfig.VERSION_CODE)
    }
}
