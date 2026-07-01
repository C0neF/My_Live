package com.mylive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mylive.app.data.local.entity.FollowUserTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowUserTagDao {

    @Query("SELECT * FROM follow_user_tags")
    fun getAll(): Flow<List<FollowUserTagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: FollowUserTagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<FollowUserTagEntity>)

    @Query("DELETE FROM follow_user_tags WHERE id = :id")
    suspend fun delete(id: String)

    @Update
    suspend fun update(tag: FollowUserTagEntity)

    @Query("DELETE FROM follow_user_tags")
    suspend fun deleteAll()
}
