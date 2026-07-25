package com.mylive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseVersionPolicyTest {

    @Test
    fun v2UsesVersionTwoZeroFive() {
        assertEquals("2.0.5", BuildConfig.VERSION_NAME)
        assertEquals(25, BuildConfig.VERSION_CODE)
    }
}
