package com.mylive.app.ui.screen.room.quickaccess

import android.os.Looper
import com.mylive.app.core.model.LiveCategoryResult
import com.mylive.app.core.model.LiveSubCategory
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickAccessViewModelTest {
    private val f = QuickAccessTestFixture()
    @After fun close() { f.close(); idle() }
    private fun idle() { shadowOf(Looper.getMainLooper()).idle() }
    private fun result(vararg ids: String, more: Boolean = false) = LiveCategoryResult(more, ids.map(QuickAccessTestFixture::room))

    @Test fun initialRequestIsSharedAndReopeningDoesNotReload() {
        val pending = CompletableDeferred<LiveCategoryResult>()
        coEvery { f.site.getCategoryRooms(any(), any()) } coAnswers { pending.await() }
        repeat(3) { f.vm.ensureRecommendations("bilibili", "a") }
        idle()
        coVerify(exactly = 1) { f.site.getCategoryRooms(any(), 1) }
        pending.complete(result("1")); idle()
        f.vm.ensureRecommendations("bilibili", "a"); idle()
        coVerify(exactly = 1) { f.site.getCategoryRooms(any(), 1) }
        assertEquals(listOf("1"), f.vm.recommendations.value.rooms.map { it.roomId })
    }

    @Test fun pagingIsGuardedDeduplicatedAndStopsAtServerEnd() {
        val pending = CompletableDeferred<LiveCategoryResult>()
        coEvery { f.site.getCategoryRooms(any(), 1) } returns result("1", "2", more = true)
        coEvery { f.site.getCategoryRooms(any(), 2) } coAnswers { pending.await() }
        f.vm.ensureRecommendations("bilibili", "a"); idle()
        repeat(3) { f.vm.loadMoreRecommendations() }; idle()
        coVerify(exactly = 1) { f.site.getCategoryRooms(any(), 2) }
        pending.complete(result("2", "3")); idle()
        assertEquals(listOf("1", "2", "3"), f.vm.recommendations.value.rooms.map { it.roomId })
        assertFalse(f.vm.recommendations.value.hasMore)
        f.vm.loadMoreRecommendations(); idle()
        coVerify(exactly = 2) { f.site.getCategoryRooms(any(), any()) }
    }

    @Test fun failedRefreshKeepsContentAndRetriesTheSameFirstPage() {
        f.vm.ensureRecommendations("bilibili", "a"); idle()
        coEvery { f.site.getCategoryRooms(any(), 1) } throws IllegalStateException("offline")
        f.vm.refreshRecommendations(); idle()
        assertEquals(listOf("1"), f.vm.recommendations.value.rooms.map { it.roomId })
        assertNotNull(f.vm.recommendations.value.error)
        coEvery { f.site.getCategoryRooms(any(), 1) } returns result("new")
        f.vm.retryRecommendations(); idle()
        assertEquals(listOf("new"), f.vm.recommendations.value.rooms.map { it.roomId })
        assertNull(f.vm.recommendations.value.error)
    }

    @Test fun newCategoryCancelsPreviousRequestWithoutMixingItsResults() {
        val pending = CompletableDeferred<LiveCategoryResult>()
        coEvery { f.site.getCategoryRooms(match { it.id == "old" }, any()) } coAnswers { pending.await() }
        coEvery { f.site.getCategoryRooms(match { it.id == "new" }, any()) } returns result("new-room")
        f.vm.ensureRecommendations("bilibili", "old"); idle()
        f.vm.ensureRecommendations("bilibili", "new"); idle()
        pending.complete(result("old-room")); idle()
        assertEquals("new", f.vm.recommendations.value.source?.categoryId)
        assertEquals(listOf("new-room"), f.vm.recommendations.value.rooms.map { it.roomId })
    }

    @Test fun noCategoryFallsBackToPlatformRecommendations() {
        f.vm.ensureRecommendations("bilibili", null); idle()
        coVerify(exactly = 1) { f.site.getRecommendRooms(1) }
        coVerify(exactly = 0) { f.site.getCategoryRooms(any(), any()) }
        assertTrue(f.vm.recommendations.value.hasLoaded)
    }

    @Test fun repeatedPageCannotDriveAnEndlessAutoLoadLoop() {
        coEvery { f.site.getCategoryRooms(any(), any()) } returns result("1", more = true)
        f.vm.ensureRecommendations("bilibili", "a"); idle()
        f.vm.loadMoreRecommendations(); idle()
        assertFalse(f.vm.recommendations.value.hasMore)
    }
}
