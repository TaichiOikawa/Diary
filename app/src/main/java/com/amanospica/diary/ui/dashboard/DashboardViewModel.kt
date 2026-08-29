package com.amanospica.diary.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amanospica.diary.domain.model.DiaryStats
import com.amanospica.diary.domain.usecase.ObserveDiaryStatsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val isLoading: Boolean = true,
    val stats: DiaryStats = DiaryStats(),
)

class DashboardViewModel(
    observeDiaryStats: ObserveDiaryStatsUseCase,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = observeDiaryStats()
        .map { DashboardUiState(isLoading = false, stats = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DashboardUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
