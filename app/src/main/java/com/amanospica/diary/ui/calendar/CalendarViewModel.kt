package com.amanospica.diary.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amanospica.diary.domain.model.CalendarDayMarker
import com.amanospica.diary.domain.usecase.ObserveCalendarMonthUseCase
import com.amanospica.diary.domain.usecase.ObserveDiariesByDateUseCase
import com.amanospica.diary.domain.usecase.ResolveMediaPathUseCase
import com.amanospica.diary.ui.common.DiaryCardUiState
import com.amanospica.diary.ui.common.toCardUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    val markers: Map<LocalDate, CalendarDayMarker> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
)

/** 日付タップで開くシートの状態。 */
data class SelectedDayUiState(
    val date: LocalDate,
    val cards: List<DiaryCardUiState>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    observeCalendarMonth: ObserveCalendarMonthUseCase,
    observeDiariesByDate: ObserveDiariesByDateUseCase,
    private val resolveMediaPath: ResolveMediaPathUseCase,
) : ViewModel() {

    /**
     * ページ番号と年月を対応づける基準。画面を開いている間は動かさない。
     * （動かすとスワイプ中にページ位置がずれる）
     */
    val anchorMonth: YearMonth = YearMonth.now()

    private val _yearMonth = MutableStateFlow(anchorMonth)

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)

    val uiState: StateFlow<CalendarUiState> = _yearMonth
        .flatMapLatest { month ->
            observeCalendarMonth(month).map { markers ->
                CalendarUiState(yearMonth = month, markers = markers)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CalendarUiState(),
        )

    /** 選択中の日付が無いときは null（＝シート非表示）。 */
    val selectedDay: StateFlow<SelectedDayUiState?> = _selectedDate
        .flatMapLatest { date ->
            if (date == null) {
                flowOf(null)
            } else {
                observeDiariesByDate(date).map { diaries ->
                    SelectedDayUiState(
                        date = date,
                        cards = diaries.map { it.toCardUiState(resolveMediaPath::invoke) },
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null,
        )

    /** 表示中の月が変わったことを受け取り、その前後を含めたマーカーを読み直す。 */
    fun showMonth(yearMonth: YearMonth) {
        _yearMonth.value = yearMonth
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun clearSelection() {
        _selectedDate.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
