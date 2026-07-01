package com.mylive.app.data.repository

import com.mylive.app.data.local.dao.ShieldDao
import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.data.local.entity.ShieldPresetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShieldRepository @Inject constructor(
    private val shieldDao: ShieldDao
) {
    // Shield words
    fun getAllShields(): Flow<List<ShieldEntity>> = shieldDao.getAllShields()

    suspend fun addShield(shield: ShieldEntity) = shieldDao.insertShield(shield)

    suspend fun addShields(shields: List<ShieldEntity>) {
        if (shields.isNotEmpty()) shieldDao.insertShields(shields)
    }

    suspend fun updateShield(shield: ShieldEntity) = shieldDao.insertShield(shield)

    suspend fun removeShield(id: Long) = shieldDao.deleteShield(id)

    suspend fun clearAllKeywords() = shieldDao.clearAllKeywords()

    suspend fun clearUserShieldsBySite(siteId: String) = shieldDao.clearUserShieldsBySite(siteId)

    suspend fun clearAllUserShields() = shieldDao.clearAllUserShields()

    // Shield presets
    fun getAllPresets(): Flow<List<ShieldPresetEntity>> = shieldDao.getAllPresets()

    suspend fun addPreset(preset: ShieldPresetEntity) = shieldDao.insertPreset(preset)

    suspend fun addPresets(presets: List<ShieldPresetEntity>) {
        if (presets.isNotEmpty()) shieldDao.insertPresets(presets)
    }

    suspend fun removePreset(name: String) = shieldDao.deletePreset(name)
}
