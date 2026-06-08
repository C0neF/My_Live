package com.mylive.app.core.site.douyin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.core.script.JsEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinSignInstrumentedTest {

    @Test
    fun abogusSigningAddsRequiredQueryParametersOnDevice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val jsEngine = JsEngine(context)
        val sign = DouyinSign(jsEngine)
        val url = "https://live.douyin.com/webcast/web/partition/detail/room/v2/" +
            "?aid=6383&app_name=douyin_web&live_id=1&device_platform=web" +
            "&count=15&offset=0&partition=720&partition_type=1&req_from=2"

        try {
            val signedUrl = sign.getAbogusUrl(
                url,
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/116.0.5845.97 Safari/537.36"
            )

            assertTrue(signedUrl, signedUrl.contains("msToken="))
            assertTrue(signedUrl, signedUrl.contains("a_bogus="))
        } finally {
            jsEngine.destroy()
        }
    }
}
