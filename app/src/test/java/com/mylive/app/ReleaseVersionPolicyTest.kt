package com.mylive.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReleaseVersionPolicyTest {

    @Test
    fun releaseUsesVersionThreeZeroZero() {
        assertEquals("3.0.0", BuildConfig.VERSION_NAME)
        assertEquals(30, BuildConfig.VERSION_CODE)
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("My Live V${BuildConfig.VERSION_NAME}", context.getString(R.string.mine_version))
    }
}
