package com.mylive.app.ui.screen.other

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    val history: StateFlow<List<HistoryEntity>> = historyRepository.getAllHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteHistory(id: String) {
        viewModelScope.launch {
            historyRepository.removeHistory(id)
        }
    }
}
