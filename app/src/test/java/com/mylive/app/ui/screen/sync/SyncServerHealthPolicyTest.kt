package com.mylive.app.ui.screen.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyncServerHealthPolicyTest {

    @Test
    fun endpointLabelShowsOnlyTheHostAndNeverCredentialsOrQuery() {
        assertEquals("默认同步服务", syncEndpointDisplayName(""))

        val label = syncEndpointDisplayName(
            "wss://user:password@sync.example.com/sync?token=top-secret"
        )
        assertEquals("自定义同步服务 · sync.example.com", label)
        assertFalse(label.contains("password"))
        assertFalse(label.contains("top-secret"))
    }

    @Test
    fun healthTextKeepsReachabilityDistinctFromLatency() {
        assertEquals("可用 · 42 ms", syncProbeStatusText(isReachable = true, latencyMillis = 42L, error = ""))
        assertEquals("不可用 · 连接超时", syncProbeStatusText(isReachable = false, latencyMillis = null, error = "连接超时"))
    }
}
