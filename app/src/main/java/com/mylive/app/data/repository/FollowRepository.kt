package com.mylive.app.data.repository

import com.mylive.app.data.local.dao.FollowUserDao
import com.mylive.app.data.local.dao.FollowUserTagDao
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.FollowUserTagEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FollowRepository @Inject constructor(
    private val followUserDao: FollowUserDao,
    private val followUserTagDao: FollowUserTagDao
) {
    // Follow user operations
    fun getAllFollows(): Flow<List<FollowUserEntity>> = followUserDao.getAll()

    suspend fun addFollow(user: FollowUserEntity) = followUserDao.insert(user)

    suspend fun addFollows(users: List<FollowUserEntity>) {
        if (users.isNotEmpty()) followUserDao.insertAll(users)
    }

    suspend fun removeFollow(id: String) = followUserDao.delete(id)

    suspend fun getFollow(siteId: String, roomId: String): FollowUserEntity? =
        followUserDao.getBySiteAndRoom(siteId, roomId)

    fun observeFollowing(siteId: String, roomId: String): Flow<Boolean> =
        followUserDao.observeBySiteAndRoom(siteId, roomId).map { it != null }

    suspend fun updateLiveStatus(id: String, status: Int, startTime: Long? = null, showTime: String? = null) =
        followUserDao.updateLiveStatus(id, status, startTime, showTime)

    suspend fun updateSpecialFollow(id: String, specialValue: Boolean) =
        followUserDao.updateSpecialFollow(id, specialValue)

    suspend fun isFollowing(siteId: String, roomId: String): Boolean =
        getFollow(siteId, roomId) != null

    // Tag operations
    fun getAllTags(): Flow<List<FollowUserTagEntity>> = followUserTagDao.getAll()

    suspend fun addTag(tag: FollowUserTagEntity) = followUserTagDao.insert(tag)

    suspend fun addTags(tags: List<FollowUserTagEntity>) {
        if (tags.isNotEmpty()) followUserTagDao.insertAll(tags)
    }

    suspend fun removeTag(id: String) = followUserTagDao.delete(id)

    suspend fun updateTag(tag: FollowUserTagEntity) = followUserTagDao.update(tag)

    suspend fun clearAllFollows() = followUserDao.deleteAll()

    suspend fun clearAllTags() = followUserTagDao.deleteAll()

    // ── JSON Import/Export ─────────────────────────────────────────────────

    /**
     * Export all follows + tags to a JSON string.
     * Format: {"follows": [...], "tags": [...]}
     */
    suspend fun exportToJson(): String {
        val follows = getAllFollows().first()
        val tags = getAllTags().first()

        val followsArray = JSONArray()
        follows.forEach { f ->
            followsArray.put(JSONObject().apply {
                put("siteId", f.siteId)
                put("id", f.id)
                put("roomId", f.roomId)
                put("userName", f.userName)
                put("face", f.face)
                put("addTime", f.addTime.toString())
                put("tag", f.tag)
                put("isSpecialFollow", f.isSpecialFollow)
                put("liveStatus", f.liveStatus)
                put("liveStartTime", f.liveStartTime ?: JSONObject.NULL)
                put("showTime", f.showTime ?: JSONObject.NULL)
            })
        }

        val tagsArray = JSONArray()
        tags.forEach { t ->
            tagsArray.put(JSONObject().apply {
                put("id", t.id)
                put("tag", t.tag)
                put("userIds", JSONArray(t.userIds))
            })
        }

        return JSONObject().apply {
            put("follows", followsArray)
            put("tags", tagsArray)
        }.toString(2)
    }

    /**
     * Import follows + tags from a JSON string.
     * Supports both the new format {"follows":[...],"tags":[...]} and
     * the legacy format (plain array of follow objects).
     * @param overlay if true, clears existing data before importing.
     */
    suspend fun importFromJson(json: String, overlay: Boolean = false) {
        val payload = parseImportPayload(json)

        if (overlay) {
            clearAllFollows()
            clearAllTags()
        }

        addFollows(payload.follows)
        addTags(payload.tags)
    }

    private fun parseImportPayload(json: String): FollowImportPayload {
        val trimmed = json.trim()
        return if (trimmed.startsWith("[")) {
            FollowImportPayload(
                follows = parseFollowsFromLegacyArray(JSONArray(trimmed)),
                tags = emptyList()
            )
        } else {
            val obj = JSONObject(trimmed)
            val hasFollows = obj.has("follows")
            val hasTags = obj.has("tags")
            if (!hasFollows && !hasTags) {
                throw IllegalArgumentException("invalid_follow_json")
            }
            FollowImportPayload(
                follows = if (hasFollows) {
                    parseFollowsFromLegacyArray(obj.getJSONArray("follows"))
                } else {
                    emptyList()
                },
                tags = if (hasTags) {
                    parseTagsArray(obj.getJSONArray("tags"))
                } else {
                    emptyList()
                }
            )
        }
    }

    private fun parseFollowsFromLegacyArray(array: JSONArray): List<FollowUserEntity> {
        val follows = mutableListOf<FollowUserEntity>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val follow = FollowUserEntity(
                id = obj.optString("id", "${obj.getString("siteId")}_${obj.getString("roomId")}"),
                roomId = obj.getString("roomId"),
                siteId = obj.getString("siteId"),
                userName = obj.getString("userName"),
                face = obj.optString("face", ""),
                addTime = obj.optString("addTime", System.currentTimeMillis().toString()).toLongOrNull() ?: System.currentTimeMillis(),
                tag = obj.optString("tag", ""),
                isSpecialFollow = obj.optBoolean("isSpecialFollow", false),
                liveStatus = obj.optInt("liveStatus", 0),
                liveStartTime = obj.optNullableLong("liveStartTime"),
                showTime = obj.optNullableString("showTime")
            )
            follows.add(follow)
        }
        return follows
    }

    private fun parseTagsArray(array: JSONArray): List<FollowUserTagEntity> {
        val tags = mutableListOf<FollowUserTagEntity>()
        for (i in 0 until array.length()) {
            val tObj = array.getJSONObject(i)
            val userIds = mutableListOf<String>()
            val userIdsArray = tObj.optJSONArray("userIds")
            if (userIdsArray != null) {
                for (j in 0 until userIdsArray.length()) {
                    userIds.add(userIdsArray.getString(j))
                }
            }
            tags.add(
                FollowUserTagEntity(
                    id = tObj.optString("id", UUID.randomUUID().toString()),
                    tag = tObj.getString("tag"),
                    userIds = userIds
                )
            )
        }
        return tags
    }

    private fun JSONObject.optNullableLong(name: String): Long? =
        opt(name)
            ?.takeUnless { it == JSONObject.NULL }
            ?.toString()
            ?.toLongOrNull()

    private fun JSONObject.optNullableString(name: String): String? =
        opt(name)
            ?.takeUnless { it == JSONObject.NULL }
            ?.toString()
}

private data class FollowImportPayload(
    val follows: List<FollowUserEntity>,
    val tags: List<FollowUserTagEntity>
)
