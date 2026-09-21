package com.mylive.app.data.repository

import com.mylive.app.data.local.dao.HistoryDao
import com.mylive.app.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: HistoryDao
) {
    fun getAllHistory(): Flow<List<HistoryEntity>> = historyDao.getAll()

    suspend fun addHistory(history: HistoryEntity) = historyDao.insert(history)

    suspend fun addHistories(histories: List<HistoryEntity>) {
        if (histories.isNotEmpty()) historyDao.insertAll(histories)
    }

    suspend fun removeHistory(id: String) = historyDao.delete(id)

    suspend fun getHistoryById(id: String): HistoryEntity? = historyDao.getById(id)

    suspend fun clearAllHistory() = historyDao.deleteAll()

    /** Parse every row before starting a transaction that may replace local history. */
    suspend fun importFromJson(json: String, overlay: Boolean) {
        val array = JSONArray(json)
        val histories = List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val siteId = item.optString("siteId", item.optString("site", ""))
            val roomId = item.optString("roomId", item.optString("room", ""))
            require(siteId.isNotBlank() && roomId.isNotBlank()) { "invalid_history_item" }
            HistoryEntity(
                id = "${siteId}_${roomId}",
                siteId = siteId,
                roomId = roomId,
                userName = item.optString("userName", item.optString("name", "")),
                face = item.optString("face", item.optString("avatar", "")),
                updateTime = item.optLong("updateTime", System.currentTimeMillis())
            )
        }
        historyDao.importAll(histories, overlay)
    }
}
