package com.mylive.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.data.local.entity.ShieldPresetEntity
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.data.repository.ShieldRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
                val shields = decodeShieldRules(JSONObject(preset.value))
                shieldRepository.importConfiguration(shields)

                // Enable toggles
                settingsRepository.setDanmuShieldEnable(true)
                settingsRepository.setDanmuKeywordShieldEnable(true)
                settingsRepository.setDanmuUserShieldEnable(true)
            } catch (cancelled: CancellationException) {
                throw cancelled
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

    /** Accepts valid input for a background import; screen feedback must await persistence. */
    fun importPresetJson(content: String): Boolean {
        val imported = decodeImportConfiguration(content) ?: return false
        viewModelScope.launch { persistImportConfiguration(imported) }
        return true
    }

    suspend fun importPresetJsonAndAwait(content: String): Boolean {
        currentCoroutineContext().ensureActive()
        val imported = decodeImportConfiguration(content) ?: return false
        return persistImportConfiguration(imported)
    }

    private data class ImportedConfiguration(
        val currentShields: List<ShieldEntity>?,
        val presets: List<ShieldPresetEntity>
    )

    private fun decodeImportConfiguration(content: String): ImportedConfiguration? =
        try {
            val decoded = JSONObject(content)
            val currentShields = if (decoded.has("current")) {
                decodeShieldRules(decoded.getJSONObject("current"))
            } else null
            val presetsArr = decoded.optionalArray("presets")
            val importedPresets = List(presetsArr.length()) { index ->
                val preset = presetsArr.getJSONObject(index)
                val rawName = preset.get("name")
                require(rawName is String && rawName.isNotBlank()) { "invalid_preset_name" }
                // Validate every nested rule before scheduling any database write.
                decodeShieldRules(preset)
                ShieldPresetEntity(name = rawName.trim(), value = preset.toString())
            }

            ImportedConfiguration(currentShields, importedPresets)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.e(e, "importPresetJson failed")
            null
        }

    private suspend fun persistImportConfiguration(imported: ImportedConfiguration): Boolean =
        try {
            shieldRepository.importConfiguration(imported.currentShields, imported.presets)
            currentCoroutineContext().ensureActive()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.e(e, "importPresetJson persistence failed")
            false
        }

    private fun JSONObject.optionalArray(key: String): JSONArray =
        if (has(key)) getJSONArray(key) else JSONArray()

    private fun decodeShieldRules(source: JSONObject): List<ShieldEntity> = buildList {
        fun addRules(array: JSONArray, prefix: String) {
            for (index in 0 until array.length()) {
                val rawValue = array.get(index)
                require(rawValue is String) { "invalid_shield_rule" }
                val value = rawValue.trim()
                if (value.isNotEmpty()) add(ShieldEntity(value = "$prefix$value"))
            }
        }

        addRules(source.optionalArray("keywords"), "keyword:")
        addRules(source.optionalArray("users"), "user:__all__:")
        val userGroups = if (source.has("userGroups")) source.getJSONObject("userGroups") else JSONObject()
        val siteIds = userGroups.keys()
        while (siteIds.hasNext()) {
            val siteId = siteIds.next()
            addRules(userGroups.getJSONArray(siteId), "user:$siteId:")
        }
    }
}
