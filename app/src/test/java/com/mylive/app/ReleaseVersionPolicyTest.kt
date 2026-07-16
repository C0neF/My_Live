package com.mylive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseVersionPolicyTest {

    @Test
    fun v2UsesVersionTwoZeroThree() {
        assertEquals("2.0.3", BuildConfig.VERSION_NAME)
        assertEquals(23, BuildConfig.VERSION_CODE)
    }
}
