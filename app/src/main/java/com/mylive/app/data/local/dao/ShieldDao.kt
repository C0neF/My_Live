package com.mylive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.data.local.entity.ShieldPresetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Dao
interface ShieldDao {

    @Query("SELECT * FROM shields")
    fun getAllShields(): Flow<List<ShieldEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShield(shield: ShieldEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShields(shields: List<ShieldEntity>)

    @Transaction
    suspend fun importKeywords(shields: List<ShieldEntity>, overlay: Boolean) {
        if (overlay) clearAllKeywords()
        val existingValues = getAllShields().first().mapTo(mutableSetOf()) { it.value }
        val additions = shields.filter { existingValues.add(it.value) }
        if (additions.isNotEmpty()) insertShields(additions)
    }

    @Transaction
    suspend fun importConfiguration(currentShields: List<ShieldEntity>?, presets: List<ShieldPresetEntity>) {
        if (currentShields != null) {
            clearAllKeywords()
            clearAllUserShields()
            if (currentShields.isNotEmpty()) insertShields(currentShields)
        }
        if (presets.isNotEmpty()) insertPresets(presets)
    }

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresets(presets: List<ShieldPresetEntity>)

    @Query("DELETE FROM shield_presets WHERE name = :name")
    suspend fun deletePreset(name: String)
}
