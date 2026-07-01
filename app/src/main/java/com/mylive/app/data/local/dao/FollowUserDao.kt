package com.mylive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mylive.app.data.local.entity.FollowUserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowUserDao {

    @Query(
        "SELECT * FROM follow_users " +
            "ORDER BY isSpecialFollow DESC, " +
            "CASE liveStatus WHEN 1 THEN 0 WHEN 0 THEN 1 ELSE 2 END ASC, " +
            "liveStartTime DESC"
    )
    fun getAll(): Flow<List<FollowUserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: FollowUserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<FollowUserEntity>)

    @Query("DELETE FROM follow_users WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM follow_users WHERE siteId = :siteId AND roomId = :roomId LIMIT 1")
    suspend fun getBySiteAndRoom(siteId: String, roomId: String): FollowUserEntity?

    @Query("SELECT * FROM follow_users WHERE siteId = :siteId AND roomId = :roomId LIMIT 1")
    fun observeBySiteAndRoom(siteId: String, roomId: String): Flow<FollowUserEntity?>

    @Query("UPDATE follow_users SET liveStatus = :status, liveStartTime = :startTime, showTime = :showTime WHERE id = :id")
    suspend fun updateLiveStatus(id: String, status: Int, startTime: Long?, showTime: String? = null)

    @Query("UPDATE follow_users SET isSpecialFollow = :specialValue WHERE id = :id")
    suspend fun updateSpecialFollow(id: String, specialValue: Boolean)

    @Query("DELETE FROM follow_users")
    suspend fun deleteAll()
}
