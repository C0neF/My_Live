package com.mylive.app.ui.screen.follow

import androidx.lifecycle.viewModelScope
import androidx.room.RoomDatabase
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.service.FollowStatusRefreshCoordinator
import com.mylive.app.testing.ReviewRoomFixture
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Measures real Room reads instead of requiring one particular ViewModel implementation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FollowGroupReadBudgetTest : ReviewRoomFixture() {
    private val followReads = AtomicInteger()

    override fun configureDatabase(builder: RoomDatabase.Builder<AppDatabase>) =
        builder.setQueryCallback({ sql, _ ->
            if (sql.trimStart().startsWith("SELECT", ignoreCase = true) &&
                sql.contains("FROM follow_users", ignoreCase = true)) followReads.incrementAndGet()
        }, Executor(Runnable::run))

    @Test
    fun editingAManyMemberGroupKeepsFollowReadsBounded() = runBlocking {
        val members = List(32) { index ->
            FollowUserEntity(id = "douyu_$index", roomId = "$index", siteId = "douyu",
                userName = "anchor $index", face = "", addTime = index.toLong(), tag = "old")
        }
        members.forEach { follows.addFollow(it) }
        follows.addTag(FollowUserTagEntity("group", "old", members.map { it.id }))
        val vm = own(FollowViewModel(follows, settings,
            FollowStatusRefreshCoordinator(follows, settings, emptySet())))
        val parent = requireNotNull(vm.viewModelScope.coroutineContext[Job])
        val existingJobs = parent.children.toSet()

        vm.renameTag("group", "new")
        withTimeout(10_000) { parent.children.filterNot { it in existingJobs }.toList().joinAll() }

        assertTrue("Group edits must not read the follow list once per member", followReads.get() <= 2)
        assertEquals(members.map { it.copy(tag = "new") }.toSet(), follows.getAllFollows().first().toSet())
        assertEquals("new", follows.getAllTags().first().single().tag)
    }
}
