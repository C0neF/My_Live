package com.mylive.app.data.repository

import com.mylive.app.data.local.dao.HistoryDao
import com.mylive.app.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow
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
}
