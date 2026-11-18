package com.mylive.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteSyncServiceProxyPolicyTest {

    @Test
    fun releaseBuildDoesNotProbeLocalLoopbackProxyWhenProxyIsUnconfigured() {
        val service = RemoteSyncService()
        var detectorCalls = 0
        service.localProxyDetector = {
            detectorCalls++
            "127.0.0.1:51888"
        }

        service.debugBuild = false
        assertNull(service.resolveProxyAddress(""))
        assertEquals(0, detectorCalls)

        service.debugBuild = true
        assertEquals("127.0.0.1:51888", service.resolveProxyAddress(""))
        assertEquals(1, detectorCalls)
    }

    @Test
    fun explicitProxyIsRespectedEvenInReleaseBuildAndDirectBypassesDetection() {
        val service = RemoteSyncService()
        var detectorCalls = 0
        service.localProxyDetector = {
            detectorCalls++
            "127.0.0.1:51888"
        }

        service.debugBuild = false
        assertEquals("192.168.1.10:7890", service.resolveProxyAddress("http://192.168.1.10:7890"))
        assertNull(service.resolveProxyAddress(RemoteSyncService.K_DIRECT_PROXY_VALUE))
        assertEquals(0, detectorCalls)
    }
}
