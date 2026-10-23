package com.mylive.app.core.site.douyu

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DouyuCookiePolicyTest {
    @Test fun cookieHeaderPastePreservesEncodedAndEqualsCharacters() {
        val input = "User-Agent: browser\nCookie: dy_did=test; dy_auth=a%2Fb==; LTP0=token==\nAccept: */*"
        val cookie = normalizeDouyuCookie(input)
        assertEquals("a%2Fb==", douyuCookiePairs(cookie)["dy_auth"])
        assertEquals("token==", douyuCookiePairs(cookie)["LTP0"])
        assertFalse(cookie.contains("User-Agent"))
        assertFalse(cookie.contains("Accept"))
    }

    @Test fun inspectionDoesNotMistakeDeviceOrRenewalCookiesForALogin() {
        assertFalse(inspectDouyuCookie("dy_did=test; acf_did=test").hasLoginToken)
        val renewal = inspectDouyuCookie("dy_did=test; LTP0=renewal-secret")
        assertFalse(renewal.hasLoginToken)
        assertTrue(renewal.canRenew)
        assertTrue(inspectDouyuCookie("dy_auth=opaque").hasLoginToken)
    }

    @Test fun expiryComesFromJwtPayloadRatherThanAFixedSevenDayAssumption() {
        val token = "header." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":12345}""".toByteArray()) + ".signature"
        val info = inspectDouyuCookie("dy_did=test; acf_jwt_token=$token; LTP0=$token")
        assertEquals(12_345_000L, info.expiresAtMillis)
        assertEquals(12_345_000L, info.renewalExpiresAtMillis)
        assertNull(douyuJwtExpiryMillis("malformed"))
    }

    @Test fun apiAndMediaHeadersDoNotExposeThePassportRenewalCredential() {
        val session = DouyuRequestSession("dy_did=test; LTP0=long-secret; PHPSESSID=php-secret; dy_auth=login-secret")
        assertTrue(session.headers("100").getValue("Cookie").contains("dy_auth=login-secret"))
        assertFalse(session.headers("100").getValue("Cookie").contains("long-secret"))
        assertFalse(session.headers("100").getValue("Cookie").contains("php-secret"))
        assertEquals("dy_did=test; acf_did=test", session.mediaHeaders("100")["Cookie"])
        assertFalse(session.toString().contains("secret"))
    }

    @Test fun webCookieMergeRetainsPassportTokenAndWebsiteLoginTogether() {
        val cookie = mergeDouyuCookies("dy_auth=website; dy_did=device", "LTP0=passport; dy_did=device")
        val info = inspectDouyuCookie(cookie)
        assertTrue(info.hasLoginToken)
        assertTrue(info.canRenew)
        assertEquals("passport", douyuCookiePairs(cookie)["LTP0"])
    }
}
