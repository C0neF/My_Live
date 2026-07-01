package com.mylive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mylive.app.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM histories ORDER BY updateTime DESC")
    fun getAll(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(histories: List<HistoryEntity>)

    @Query("DELETE FROM histories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM histories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HistoryEntity?

    @Query("DELETE FROM histories")
    suspend fun deleteAll()
}
