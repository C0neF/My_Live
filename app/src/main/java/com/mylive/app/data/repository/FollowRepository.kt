package com.mylive.app.data.repository

import androidx.room.withTransaction
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.dao.FollowUserDao
import com.mylive.app.data.local.dao.FollowLiveStatusUpdate
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
    private val followUserTagDao: FollowUserTagDao,
    private val database: AppDatabase
) {
    // Follow user operations
    fun getAllFollows(): Flow<List<FollowUserEntity>> = followUserDao.getAll()

    suspend fun addFollow(user: FollowUserEntity) = followUserDao.insert(user)

    suspend fun addFollows(users: List<FollowUserEntity>) {
        if (users.isEmpty()) return
        database.withTransaction {
            val previous = getAllFollows().first().associateBy { it.id }
            followUserDao.insertAll(users.map { user ->
                preserveCanonicalRoomId(user, previous[user.id])
            })
        }
    }

    suspend fun removeFollow(id: String) = followUserDao.delete(id)

    suspend fun getFollow(siteId: String, roomId: String): FollowUserEntity? =
        followUserDao.getBySiteAndRoom(siteId, roomId)

    fun observeFollowing(siteId: String, roomId: String): Flow<Boolean> =
        followUserDao.observeBySiteAndRoom(siteId, roomId).map { it != null }

    suspend fun updateLiveStatus(id: String, status: Int, startTime: Long? = null, showTime: String? = null) =
        followUserDao.updateLiveStatus(id, status, startTime, showTime)

    suspend fun updateLiveStatuses(users: List<FollowUserEntity>) {
        if (users.isEmpty()) return
        database.withTransaction {
            followUserDao.updateLiveStatuses(
                users.map { user ->
                    FollowLiveStatusUpdate(
                        id = user.id,
                        liveStatus = user.liveStatus,
                        liveStartTime = user.liveStartTime,
                        showTime = user.showTime
                    )
                }
            )
            users.forEach { user ->
                if (user.siteId == "douyin") user.canonicalRoomId?.takeIf { it.isNotBlank() }?.let {
                    followUserDao.updateCanonicalRoomId(user.id, it)
                }
            }
        }
    }

    suspend fun rememberCanonicalRoomId(siteId: String, requestedRoomId: String, canonicalRoomId: String) {
        if (siteId == "douyin" && canonicalRoomId.isNotBlank()) {
            followUserDao.rememberDouyinRoomId(requestedRoomId, canonicalRoomId)
        }
    }

    suspend fun updateSpecialFollow(id: String, specialValue: Boolean) =
        followUserDao.updateSpecialFollow(id, specialValue)

    suspend fun updateFollowTag(id: String, tag: String) {
        followUserDao.updateFollowTag(id, tag)
    }

    suspend fun isFollowing(siteId: String, roomId: String): Boolean =
        getFollow(siteId, roomId) != null

    // Tag operations
    fun getAllTags(): Flow<List<FollowUserTagEntity>> = followUserTagDao.getAll()

    suspend fun addTag(tag: FollowUserTagEntity) = followUserTagDao.insert(tag)

    suspend fun addTags(tags: List<FollowUserTagEntity>) {
        if (tags.isNotEmpty()) followUserTagDao.insertAll(tags)
    }

    suspend fun assignFollowTag(followId: String, targetTagId: String?) = database.withTransaction {
        // Dialog snapshots carry identity only: the target may have new members or a new name.
        val target = if (targetTagId == null) null
            else followUserTagDao.getById(targetTagId) ?: return@withTransaction
        // A deleted target leaves the original assignment intact; a deleted follow gains no member.
        if (followUserDao.updateFollowTag(followId, target?.tag.orEmpty()) == 0) return@withTransaction

        followUserTagDao.getAll().first().forEach { tag ->
            val members = if (tag.id == target?.id) (tag.userIds + followId).distinct()
                else tag.userIds.filterNot { it == followId }
            if (members != tag.userIds) followUserTagDao.update(tag.copy(userIds = members))
        }
    }

    suspend fun removeTag(id: String) = database.withTransaction {
        val tag = followUserTagDao.getById(id) ?: return@withTransaction
        // Group edits must never reinsert a deleted follow or overwrite refreshed full-row data.
        tag.userIds.forEach { userId -> followUserDao.updateFollowTag(userId, "") }
        followUserTagDao.delete(id)
    }

    suspend fun renameTag(id: String, name: String) = database.withTransaction {
        val tag = followUserTagDao.getById(id) ?: return@withTransaction
        tag.userIds.forEach { userId -> followUserDao.updateFollowTag(userId, name) }
        followUserTagDao.update(tag.copy(tag = name))
    }

    suspend fun updateTag(tag: FollowUserTagEntity) = followUserTagDao.update(tag)

    suspend fun clearAllFollows() = followUserDao.deleteAll()

    suspend fun clearAllTags() = followUserTagDao.deleteAll()

    suspend fun importFollows(users: List<FollowUserEntity>, overlay: Boolean) = database.withTransaction {
        if (overlay) clearAllFollows()
        addFollows(users)
    }

    suspend fun importTags(tags: List<FollowUserTagEntity>, overlay: Boolean) = database.withTransaction {
        if (overlay) clearAllTags()
        addTags(tags)
    }

    // ── JSON Import/Export ─────────────────────────────────────────────────

    /**
     * Export all follows + tags to a JSON string.
     * Format: {"follows": [...], "tags": [...]}
     */
    suspend fun exportToJson(): String {
        val (follows, tags) = database.withTransaction {
            getAllFollows().first() to getAllTags().first()
        }

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
                put("canonicalRoomId", f.canonicalRoomId ?: JSONObject.NULL)
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

        database.withTransaction {
            if (payload.isBundle) {
                importBundle(payload, overlay)
            } else {
                if (overlay) {
                    clearAllFollows()
                    clearAllTags()
                }
                addFollows(payload.follows)
                addTags(payload.tags)
            }
        }
    }

    /** Reconcile only the bundle's follows, while retaining unrelated local group members. */
    private suspend fun importBundle(payload: FollowImportPayload, overlay: Boolean) {
        if (!overlay && payload.follows.isEmpty() && payload.tags.isEmpty()) return
        val previousFollows = if (overlay) emptyMap() else getAllFollows().first().associateBy { it.id }
        val previousTags = if (overlay) emptyMap() else getAllTags().first().associateBy { it.id }
        if (overlay) {
            clearAllFollows()
            clearAllTags()
        }

        val incomingFollows = payload.follows.associateBy { it.id }
        val incomingTags = payload.tags.associateBy { it.id }
        val tags = previousTags.toMutableMap().apply { putAll(incomingTags) }
        val members = tags.keys.associateWith { id ->
            previousTags[id]?.userIds.orEmpty().filterNot { it in incomingFollows }.toMutableSet()
        }.toMutableMap()
        val inputMemberships = mutableMapOf<String, MutableList<FollowUserTagEntity>>()
        incomingTags.values.forEach { tag ->
            tag.userIds.forEach { id ->
                if (id in incomingFollows) inputMemberships.getOrPut(id) { mutableListOf() }.add(tag)
            }
        }
        val inputByName = incomingTags.values.groupBy { it.tag }.mapValues { it.value.first() }
        val byName = tags.values.groupBy { it.tag }.mapValues { it.value.first() }.toMutableMap()
        val normalizedFollows = incomingFollows.values.map { follow ->
            val candidates = inputMemberships[follow.id].orEmpty()
            val target = candidates.firstOrNull { it.tag == follow.tag }
                ?: candidates.firstOrNull()
                ?: inputByName[follow.tag]
                ?: byName[follow.tag]
                ?: follow.tag.takeIf { it.isNotBlank() }?.let { name ->
                    FollowUserTagEntity(UUID.randomUUID().toString(), name, emptyList()).also { tag ->
                        tags[tag.id] = tag
                        members[tag.id] = mutableSetOf()
                        byName[name] = tag
                    }
                }
            if (target != null) members.getValue(target.id).add(follow.id)
            preserveCanonicalRoomId(follow.copy(tag = target?.tag.orEmpty()), previousFollows[follow.id])
        }
        if (normalizedFollows.isNotEmpty()) followUserDao.insertAll(normalizedFollows)

        incomingTags.values.forEach { tag ->
            val previous = previousTags[tag.id]
            if (previous != null && previous.tag != tag.tag) {
                // A stable group ID may be renamed by the source. Retained local members still
                // belong to that group; update only their tag, preserving refreshed row metadata.
                members.getValue(tag.id).filterNot { it in incomingFollows }.forEach { id ->
                    followUserDao.updateFollowTag(id, tag.tag)
                }
            }
        }
        val changedTags = tags.values.map { it.copy(userIds = members.getValue(it.id).toList()) }
            .filter { it != previousTags[it.id] }
        if (changedTags.isNotEmpty()) followUserTagDao.insertAll(changedTags)
    }

    private fun preserveCanonicalRoomId(user: FollowUserEntity, previous: FollowUserEntity?): FollowUserEntity =
        if (user.canonicalRoomId == null && previous != null &&
            previous.siteId == user.siteId && previous.roomId == user.roomId)
            user.copy(canonicalRoomId = previous.canonicalRoomId) else user

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
                    parseTagsArray(obj.getJSONArray("tags"), validate = hasFollows)
                } else {
                    emptyList()
                },
                isBundle = hasFollows && hasTags
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
                showTime = obj.optNullableString("showTime"),
                canonicalRoomId = obj.optNullableString("canonicalRoomId")?.takeIf { it.isNotBlank() }
            )
            follows.add(follow)
        }
        return follows
    }

    private fun parseTagsArray(array: JSONArray, validate: Boolean): List<FollowUserTagEntity> {
        val tags = mutableListOf<FollowUserTagEntity>()
        for (i in 0 until array.length()) {
            val tObj = array.getJSONObject(i)
            val id = tObj.optString("id", UUID.randomUUID().toString())
            val name = tObj.getString("tag")
            if (validate) require(id.isNotBlank() && name.isNotBlank()) { "invalid_follow_tag" }
            val userIds = mutableListOf<String>()
            val userIdsArray = tObj.optJSONArray("userIds")
            if (userIdsArray != null) {
                for (j in 0 until userIdsArray.length()) {
                    userIds.add(userIdsArray.getString(j))
                }
            }
            tags.add(
                FollowUserTagEntity(
                    id = id,
                    tag = name,
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
    val tags: List<FollowUserTagEntity>,
    val isBundle: Boolean = false
)
