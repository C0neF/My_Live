package com.mylive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseVersionPolicyTest {

    @Test
    fun v2UsesVersionTwoZeroSeven() {
        assertEquals("2.0.7", BuildConfig.VERSION_NAME)
        assertEquals(27, BuildConfig.VERSION_CODE)
    }
}
