package com.syncone.health.presentation.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncone.health.domain.model.SmsThread
import com.syncone.health.domain.model.enums.ThreadStatus
import com.syncone.health.domain.model.enums.UrgencyLevel
import com.syncone.health.domain.usecase.GetThreadsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SmsMonitorViewModel @Inject constructor(
    private val getThreadsUseCase: GetThreadsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ThreadFilter.ALL)
    val selectedFilter: StateFlow<ThreadFilter> = _selectedFilter.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeThreads(ThreadFilter.ALL)
    }

    fun onFilterSelected(filter: ThreadFilter) {
        _selectedFilter.value = filter
        observeThreads(filter)
    }

    private fun observeThreads(filter: ThreadFilter) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            val flow = when (filter) {
                ThreadFilter.ALL -> getThreadsUseCase.all()
                ThreadFilter.CRITICAL -> getThreadsUseCase.byUrgency(UrgencyLevel.CRITICAL)
                ThreadFilter.URGENT -> getThreadsUseCase.byUrgency(UrgencyLevel.URGENT)
                ThreadFilter.RESOLVED -> getThreadsUseCase.byStatus(ThreadStatus.RESOLVED)
            }

            flow.collectLatest { threads ->
                _uiState.value = if (threads.isEmpty()) {
                    UiState.Empty
                } else {
                    UiState.Success(threads)
                }
            }
        }
    }

    sealed class UiState {
        object Loading : UiState()
        object Empty : UiState()
        data class Success(val threads: List<SmsThread>) : UiState()
    }

    enum class ThreadFilter {
        ALL, CRITICAL, URGENT, RESOLVED
    }
}
