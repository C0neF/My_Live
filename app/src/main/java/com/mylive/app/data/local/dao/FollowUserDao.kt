package com.mylive.app.data.local.dao

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mylive.app.data.local.entity.FollowUserEntity
import kotlinx.coroutines.flow.Flow

data class FollowLiveStatusUpdate(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "liveStatus") val liveStatus: Int,
    @ColumnInfo(name = "liveStartTime") val liveStartTime: Long?,
    @ColumnInfo(name = "showTime") val showTime: String?
)

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

    @Query("SELECT * FROM follow_users WHERE siteId = :siteId AND " +
        "(roomId = :roomId OR (siteId = 'douyin' AND canonicalRoomId = :roomId)) LIMIT 1")
    suspend fun getBySiteAndRoom(siteId: String, roomId: String): FollowUserEntity?

    @Query("SELECT * FROM follow_users WHERE siteId = :siteId AND " +
        "(roomId = :roomId OR (siteId = 'douyin' AND canonicalRoomId = :roomId)) LIMIT 1")
    fun observeBySiteAndRoom(siteId: String, roomId: String): Flow<FollowUserEntity?>

    @Query("UPDATE follow_users SET liveStatus = :status, liveStartTime = :startTime, showTime = :showTime WHERE id = :id")
    suspend fun updateLiveStatus(id: String, status: Int, startTime: Long?, showTime: String? = null)

    @Update(entity = FollowUserEntity::class)
    suspend fun updateLiveStatuses(updates: List<FollowLiveStatusUpdate>)

    @Query("UPDATE follow_users SET canonicalRoomId = :canonicalRoomId WHERE id = :id")
    suspend fun updateCanonicalRoomId(id: String, canonicalRoomId: String)

    @Query("UPDATE follow_users SET canonicalRoomId = :canonicalRoomId WHERE siteId = 'douyin' AND " +
        "(roomId = :requestedRoomId OR canonicalRoomId = :requestedRoomId)")
    suspend fun rememberDouyinRoomId(requestedRoomId: String, canonicalRoomId: String)

    @Query("UPDATE follow_users SET isSpecialFollow = :specialValue WHERE id = :id")
    suspend fun updateSpecialFollow(id: String, specialValue: Boolean)

    @Query("UPDATE follow_users SET tag = :tag WHERE id = :id")
    suspend fun updateFollowTag(id: String, tag: String): Int

    @Query("DELETE FROM follow_users")
    suspend fun deleteAll()
}
