package com.mylive.app.di

import android.content.Context
import androidx.room.Room
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.dao.FollowUserDao
import com.mylive.app.data.local.dao.FollowUserTagDao
import com.mylive.app.data.local.dao.HistoryDao
import com.mylive.app.data.local.dao.ShieldDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "my_live.db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }

    @Provides
    fun provideFollowUserDao(db: AppDatabase): FollowUserDao = db.followUserDao()

    @Provides
    fun provideFollowUserTagDao(db: AppDatabase): FollowUserTagDao = db.followUserTagDao()

    @Provides
    fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideShieldDao(db: AppDatabase): ShieldDao = db.shieldDao()

    // NOTE: Repositories (FollowRepository, HistoryRepository, SettingsRepository,
    // ShieldRepository, AccountRepository) all use @Inject constructor with
    // @Singleton scope. Hilt resolves them automatically -- no explicit @Provides needed.
    // SettingsDataStore is provided via AppModule.
}
