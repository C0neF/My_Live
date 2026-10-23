package com.mylive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseVersionPolicyTest {

    @Test
    fun releaseUsesVersionTwoOneZero() {
        assertEquals("2.1.0", BuildConfig.VERSION_NAME)
        assertEquals(29, BuildConfig.VERSION_CODE)
    }
}
