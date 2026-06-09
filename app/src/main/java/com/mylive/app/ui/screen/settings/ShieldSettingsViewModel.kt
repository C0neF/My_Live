package com.mylive.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.data.local.entity.ShieldPresetEntity
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.data.repository.ShieldRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class ShieldSettingsViewModel @Inject constructor(
    private val shieldRepository: ShieldRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // Global toggles from SettingsRepository
    val danmuShieldEnable: Flow<Boolean> = settingsRepository.danmuShieldEnable
    val danmuKeywordShieldEnable: Flow<Boolean> = settingsRepository.danmuKeywordShieldEnable
    val danmuUserShieldEnable: Flow<Boolean> = settingsRepository.danmuUserShieldEnable

    // All shields
    private val allShields: Flow<List<ShieldEntity>> = shieldRepository.getAllShields()

    // Keywords (value starts with "keyword:")
    val keywords: Flow<List<ShieldEntity>> = allShields.map { list ->
        list.filter { it.value.startsWith("keyword:") }
    }

    // Presets
    val presets: Flow<List<ShieldPresetEntity>> = shieldRepository.getAllPresets()

    // Selected user shield site tab (e.g. "__all__", "bilibili", "douyu", "huya", "douyin")
    private val _selectedUserSiteId = MutableStateFlow("__all__")
    val selectedUserSiteId: StateFlow<String> = _selectedUserSiteId.asStateFlow()

    // Filtered user shields for currently selected site
    val activeUserShields: Flow<List<ShieldEntity>> = combine(
        allShields,
        selectedUserSiteId
    ) { list, siteId ->
        list.filter { it.value.startsWith("user:$siteId:") }
    }

    fun setDanmuShieldEnable(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDanmuShieldEnable(enabled) }
    }

    fun setDanmuKeywordShieldEnable(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDanmuKeywordShieldEnable(enabled) }
    }

    fun setDanmuUserShieldEnable(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDanmuUserShieldEnable(enabled) }
    }

    fun selectUserSite(siteId: String) {
        _selectedUserSiteId.value = siteId
    }

    // Keyword CRUD
    fun addKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            shieldRepository.addShield(ShieldEntity(value = "keyword:$trimmed"))
        }
    }

    fun deleteShield(id: Long) {
        viewModelScope.launch {
            shieldRepository.removeShield(id)
        }
    }

    fun updateKeyword(entity: ShieldEntity, keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            shieldRepository.updateShield(entity.copy(value = "keyword:$trimmed"))
        }
    }

    fun clearKeywords() {
        viewModelScope.launch {
            shieldRepository.clearAllKeywords()
        }
    }

    // User Shield CRUD
    fun addUserShield(username: String, siteId: String) {
        val trimmed = username.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            shieldRepository.addShield(ShieldEntity(value = "user:$siteId:$trimmed"))
        }
    }

    fun updateUserShield(entity: ShieldEntity, username: String) {
        val trimmed = username.trim()
        if (trimmed.isEmpty()) return
        val siteId = entity.value
            .removePrefix("user:")
            .substringBefore(':', missingDelimiterValue = "__all__")
            .ifBlank { "__all__" }
        viewModelScope.launch {
            shieldRepository.updateShield(entity.copy(value = "user:$siteId:$trimmed"))
        }
    }

    fun clearUserShields(siteId: String) {
        viewModelScope.launch {
            shieldRepository.clearUserShieldsBySite(siteId)
        }
    }

    fun clearAllUserShields() {
        viewModelScope.launch {
            shieldRepository.clearAllUserShields()
        }
    }

    // Preset CRUD
    fun savePreset(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch {
            try {
                val currentShields = allShields.first()
                val keywordValues = currentShields
                    .filter { it.value.startsWith("keyword:") }
                    .map { it.value.removePrefix("keyword:") }
                
                val globalUsers = currentShields
                    .filter { it.value.startsWith("user:__all__:") }
                    .map { it.value.removePrefix("user:__all__:") }

                val userGroupsObj = JSONObject()
                val siteIds = listOf("bilibili", "douyu", "huya", "douyin")
                siteIds.forEach { siteId ->
                    val users = currentShields
                        .filter { it.value.startsWith("user:$siteId:") }
                        .map { it.value.removePrefix("user:$siteId:") }
                    if (users.isNotEmpty()) {
                        userGroupsObj.put(siteId, JSONArray(users))
                    }
                }

                val presetObj = JSONObject().apply {
                    put("name", trimmedName)
                    put("keywords", JSONArray(keywordValues))
                    put("users", JSONArray(globalUsers))
                    put("userGroups", userGroupsObj)
                }

                shieldRepository.addPreset(
                    ShieldPresetEntity(name = trimmedName, value = presetObj.toString())
                )
            } catch (e: Exception) {
                Timber.e(e, "savePreset failed")
            }
        }
    }

    fun applyPreset(preset: ShieldPresetEntity) {
        viewModelScope.launch {
            try {
                val decoded = JSONObject(preset.value)
                val keywordArr = decoded.optJSONArray("keywords") ?: JSONArray()
                val globalUsersArr = decoded.optJSONArray("users") ?: JSONArray()
                val userGroupsObj = decoded.optJSONObject("userGroups") ?: JSONObject()

                // Clear current
                shieldRepository.clearAllKeywords()
                shieldRepository.clearAllUserShields()

                // Insert new keywords
                for (i in 0 until keywordArr.length()) {
                    val kw = keywordArr.getString(i).trim()
                    if (kw.isNotEmpty()) {
                        shieldRepository.addShield(ShieldEntity(value = "keyword:$kw"))
                    }
                }

                // Insert global users
                for (i in 0 until globalUsersArr.length()) {
                    val user = globalUsersArr.getString(i).trim()
                    if (user.isNotEmpty()) {
                        shieldRepository.addShield(ShieldEntity(value = "user:__all__:$user"))
                    }
                }

                // Insert group users
                val keys = userGroupsObj.keys()
                while (keys.hasNext()) {
                    val siteId = keys.next()
                    val usersArr = userGroupsObj.optJSONArray(siteId) ?: JSONArray()
                    for (i in 0 until usersArr.length()) {
                        val user = usersArr.getString(i).trim()
                        if (user.isNotEmpty()) {
                            shieldRepository.addShield(ShieldEntity(value = "user:$siteId:$user"))
                        }
                    }
                }

                // Enable toggles
                settingsRepository.setDanmuShieldEnable(true)
                settingsRepository.setDanmuKeywordShieldEnable(true)
                settingsRepository.setDanmuUserShieldEnable(true)
            } catch (e: Exception) {
                Timber.e(e, "applyPreset failed")
            }
        }
    }

    fun deletePreset(name: String) {
        viewModelScope.launch {
            shieldRepository.removePreset(name)
        }
    }

    // Clipboard Import/Export
    suspend fun generateExportJson(): String {
        return try {
            val currentShields = allShields.first()
            val keywordValues = currentShields
                .filter { it.value.startsWith("keyword:") }
                .map { it.value.removePrefix("keyword:") }
            
            val globalUsers = currentShields
                .filter { it.value.startsWith("user:__all__:") }
                .map { it.value.removePrefix("user:__all__:") }

            val userGroupsObj = JSONObject()
            val siteIds = listOf("bilibili", "douyu", "huya", "douyin")
            siteIds.forEach { siteId ->
                val users = currentShields
                    .filter { it.value.startsWith("user:$siteId:") }
                    .map { it.value.removePrefix("user:$siteId:") }
                if (users.isNotEmpty()) {
                    userGroupsObj.put(siteId, JSONArray(users))
                }
            }

            val currentObj = JSONObject().apply {
                put("keywords", JSONArray(keywordValues))
                put("users", JSONArray(globalUsers))
                put("userGroups", userGroupsObj)
            }

            val presetsList = shieldRepository.getAllPresets().first()
            val presetsArr = JSONArray()
            presetsList.forEach { preset ->
                try {
                    val pObj = JSONObject(preset.value)
                    presetsArr.put(pObj)
                } catch (e: Exception) {
                    presetsArr.put(JSONObject().apply {
                        put("name", preset.name)
                        put("value", preset.value)
                    })
                }
            }

            val root = JSONObject().apply {
                put("version", 2)
                put("exportedAt", Instant.now().toString())
                put("current", currentObj)
                put("presets", presetsArr)
            }
            root.toString(2)
        } catch (e: Exception) {
            Timber.e(e, "generateExportJson failed")
            ""
        }
    }

    fun importPresetJson(content: String): Boolean {
        try {
            val decoded = JSONObject(content)
            val currentObj = decoded.optJSONObject("current")
            val presetsArr = decoded.optJSONArray("presets") ?: JSONArray()

            viewModelScope.launch {
                // Import presets
                for (i in 0 until presetsArr.length()) {
                    val pObj = presetsArr.optJSONObject(i) ?: continue
                    val name = pObj.optString("name", "").trim()
                    if (name.isNotEmpty()) {
                        shieldRepository.addPreset(ShieldPresetEntity(name = name, value = pObj.toString()))
                    }
                }

                // Apply current if present
                if (currentObj != null) {
                    val keywordArr = currentObj.optJSONArray("keywords") ?: JSONArray()
                    val globalUsersArr = currentObj.optJSONArray("users") ?: JSONArray()
                    val userGroupsObj = currentObj.optJSONObject("userGroups") ?: JSONObject()

                    shieldRepository.clearAllKeywords()
                    shieldRepository.clearAllUserShields()

                    for (i in 0 until keywordArr.length()) {
                        val kw = keywordArr.getString(i).trim()
                        if (kw.isNotEmpty()) {
                            shieldRepository.addShield(ShieldEntity(value = "keyword:$kw"))
                        }
                    }

                    for (i in 0 until globalUsersArr.length()) {
                        val user = globalUsersArr.getString(i).trim()
                        if (user.isNotEmpty()) {
                            shieldRepository.addShield(ShieldEntity(value = "user:__all__:$user"))
                        }
                    }

                    val keys = userGroupsObj.keys()
                    while (keys.hasNext()) {
                        val siteId = keys.next()
                        val usersArr = userGroupsObj.optJSONArray(siteId) ?: JSONArray()
                        for (i in 0 until usersArr.length()) {
                            val user = usersArr.getString(i).trim()
                            if (user.isNotEmpty()) {
                                shieldRepository.addShield(ShieldEntity(value = "user:$siteId:$user"))
                            }
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Timber.e(e, "importPresetJson failed")
            return false
        }
    }
}
