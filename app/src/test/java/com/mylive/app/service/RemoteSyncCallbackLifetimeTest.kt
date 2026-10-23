package com.mylive.app.service

import androidx.lifecycle.ViewModelStore
import com.mylive.app.ui.screen.sync.RemoteSyncRoomViewModel
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteSyncCallbackLifetimeTest {
    @Test fun outgoingPageCannotDetachIncomingPagesCallbacks() {
        val service = RemoteSyncService()
        val oldStore = ViewModelStore()
        val newStore = ViewModelStore()
        try {
            oldStore.put("old", createViewModel(service))
            val oldCallback = service.onFavoriteReceived
            newStore.put("new", createViewModel(service))
            val newCallback = service.onFavoriteReceived
            assertNotSame(oldCallback, newCallback)
            assertNotNull(newCallback)
            oldStore.clear()
            assertSame(newCallback, service.onFavoriteReceived)
            newStore.clear()
            assertNull(service.onFavoriteReceived)
            assertNull(service.onHistoryReceived)
            assertNull(service.onBiliAccountReceived)
            assertEquals(RemoteSyncConnectionState.DISCONNECTED, service.connectionState.value)
        } finally {
            oldStore.clear()
            newStore.clear()
        }
    }

    private fun createViewModel(service: RemoteSyncService) = RemoteSyncRoomViewModel(
        service, mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true)
    )
}
