package com.mylive.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRemarkRepository @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    /**
     * Observe all user remarks as a Map of "siteId::userName" -> "remark"
     */
    fun observeAllRemarks(): Flow<Map<String, String>> =
        settingsRepository.userRemarksJson.map { json ->
            parseRemarksJson(json)
        }

    /**
     * Get a specific user's remark synchronously (from cached Flow).
     */
    suspend fun getRemark(siteId: String, userName: String): String? {
        val json = settingsRepository.userRemarksJson.first()
        val remarks = parseRemarksJson(json)
        val key = buildKey(siteId, userName)
        return remarks[key]?.takeIf { it.isNotBlank() }
    }

    /**
     * Set a user's remark. If remark is blank, removes the entry.
     */
    suspend fun setRemark(siteId: String, userName: String, remark: String) {
        val json = settingsRepository.userRemarksJson.first()
        val remarks = parseRemarksJson(json).toMutableMap()
        val key = buildKey(siteId, userName)

        if (remark.isBlank()) {
            remarks.remove(key)
        } else {
            remarks[key] = remark.trim()
        }

        settingsRepository.setUserRemarksJson(remarksToJson(remarks))
    }

    private fun buildKey(siteId: String, userName: String): String =
        "${siteId.lowercase()}::${userName.trim()}"

    private fun parseRemarksJson(json: String): Map<String, String> {
        if (json.isBlank() || json == "{}") return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { key ->
                map[key] = obj.getString(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun remarksToJson(remarks: Map<String, String>): String {
        val obj = JSONObject()
        remarks.forEach { (key, value) ->
            obj.put(key, value)
        }
        return obj.toString()
    }
}
