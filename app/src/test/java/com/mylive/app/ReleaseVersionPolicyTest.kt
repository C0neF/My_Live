package com.mylive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseVersionPolicyTest {

    @Test
    fun v2UsesVersionTwoZeroTwo() {
        assertEquals("2.0.2", BuildConfig.VERSION_NAME)
        assertEquals(22, BuildConfig.VERSION_CODE)
    }
}
