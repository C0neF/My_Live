package com.mylive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseVersionPolicyTest {

    @Test
    fun releaseUsesVersionOneOneFive() {
        assertEquals("1.1.5", BuildConfig.VERSION_NAME)
        assertEquals(15, BuildConfig.VERSION_CODE)
    }
}
