package com.mylive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.data.local.entity.ShieldPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShieldDao {

    @Query("SELECT * FROM shields")
    fun getAllShields(): Flow<List<ShieldEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShield(shield: ShieldEntity)

    @Query("DELETE FROM shields WHERE id = :id")
    suspend fun deleteShield(id: Long)

    @Query("DELETE FROM shields WHERE value LIKE 'keyword:%'")
    suspend fun clearAllKeywords()

    @Query("DELETE FROM shields WHERE value LIKE 'user:' || :siteId || ':%'")
    suspend fun clearUserShieldsBySite(siteId: String)

    @Query("DELETE FROM shields WHERE value LIKE 'user:%'")
    suspend fun clearAllUserShields()

    @Query("SELECT * FROM shield_presets")
    fun getAllPresets(): Flow<List<ShieldPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: ShieldPresetEntity)

    @Query("DELETE FROM shield_presets WHERE name = :name")
    suspend fun deletePreset(name: String)
}
