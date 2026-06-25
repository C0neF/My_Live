package com.mylive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseVersionPolicyTest {

    @Test
    fun releaseUsesVersionOneOneSix() {
        assertEquals("1.1.6", BuildConfig.VERSION_NAME)
        assertEquals(16, BuildConfig.VERSION_CODE)
    }
}