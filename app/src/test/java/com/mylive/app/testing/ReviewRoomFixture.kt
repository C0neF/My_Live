package com.mylive.app.testing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.HistoryRepository
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.data.repository.ShieldRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before

/** Real Room DAOs; only device settings are replaced by fixed test flows. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ReviewRoomFixture {
    protected lateinit var database: AppDatabase
    protected lateinit var follows: FollowRepository
    protected lateinit var shields: ShieldRepository
    protected lateinit var history: HistoryRepository
    protected val settings = mockk<SettingsRepository> {
        every { danmuShieldEnable } returns flowOf(true)
        every { danmuKeywordShieldEnable } returns flowOf(true)
        every { danmuUserShieldEnable } returns flowOf(true)
        every { siteSort } returns flowOf("bilibili,douyu,huya,douyin")
    }
    private val stores = mutableListOf<ViewModelStore>()

    protected open fun configureDatabase(builder: RoomDatabase.Builder<AppDatabase>) = builder

    @Before
    fun setUpReviewRoom() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = configureDatabase(Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java))
            .allowMainThreadQueries().build()
        follows = FollowRepository(database.followUserDao(), database.followUserTagDao(), database)
        shields = ShieldRepository(database.shieldDao())
        history = HistoryRepository(database.historyDao())
    }

    protected fun <T : ViewModel> own(viewModel: T): T = viewModel.also { vm ->
        stores += ViewModelStore().also { it.put("review", vm) }
    }

    protected suspend fun awaitWork(viewModel: ViewModel) {
        withTimeout(10_000) {
            viewModel.viewModelScope.coroutineContext[Job]!!.children.toList().joinAll()
        }
    }

    @After
    fun tearDownReviewRoom() {
        stores.forEach(ViewModelStore::clear)
        database.close()
        Dispatchers.resetMain()
    }
}
