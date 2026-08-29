package com.amanospica.diary.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amanospica.diary.R
import kotlinx.coroutines.launch
import com.amanospica.diary.ui.common.DiaryCard
import com.amanospica.diary.ui.common.DiaryViewModelFactory
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * カレンダー画面。日記のある日には、その日の最新エントリーの絵文字を表示する。
 */
@Composable
fun CalendarScreen(
    onOpenDiary: (diaryId: String, isDraft: Boolean) -> Unit,
    onCreateDiary: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = viewModel(factory = DiaryViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()

    val anchorMonth = viewModel.anchorMonth
    val pagerState = rememberPagerState(initialPage = ANCHOR_PAGE) { PAGE_COUNT }
    val scope = rememberCoroutineScope()

    // ページ番号から年月を出す。ヘッダーは指の動きに合わせて先に切り替わってほしいので
    // 落ち着いたページ（settledPage）ではなく currentPage を見る。
    val visibleMonth by remember(anchorMonth) {
        derivedStateOf { anchorMonth.plusMonths((pagerState.currentPage - ANCHOR_PAGE).toLong()) }
    }

    // 表示中の月が変わったら、その前後を含むマーカーを読み直す
    LaunchedEffect(anchorMonth) {
        snapshotFlow { visibleMonth }.collect(viewModel::showMonth)
    }

    fun scrollToMonth(target: YearMonth) {
        val page = ANCHOR_PAGE + anchorMonth.until(target, ChronoUnit.MONTHS).toInt()
        if (page !in 0 until PAGE_COUNT) return
        scope.launch {
            // 遠くへ飛ぶときのアニメーションは間の月が流れるだけなので、その場で移る
            if (abs(page - pagerState.currentPage) > 1) {
                pagerState.scrollToPage(page)
            } else {
                pagerState.animateScrollToPage(page)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
    ) {
        MonthHeader(
            yearMonth = visibleMonth,
            onPrevious = { scrollToMonth(visibleMonth.minusMonths(1)) },
            onNext = { scrollToMonth(visibleMonth.plusMonths(1)) },
            onToday = { scrollToMonth(YearMonth.now()) },
        )
        HorizontalPager(
            state = pagerState,
            // 隣の月も先に組み立てておき、指を動かした瞬間に中身が出ているようにする
            beyondViewportPageCount = 1,
            key = { it },
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            MonthGrid(
                yearMonth = anchorMonth.plusMonths((page - ANCHOR_PAGE).toLong()),
                markers = uiState.markers,
                today = uiState.today,
                onDayClick = viewModel::selectDate,
            )
        }
    }

    selectedDay?.let { day ->
        DayEntriesSheet(
            day = day,
            onDismiss = viewModel::clearSelection,
            onOpenDiary = { diaryId, isDraft ->
                viewModel.clearSelection()
                onOpenDiary(diaryId, isDraft)
            },
            onCreateDiary = {
                viewModel.clearSelection()
                onCreateDiary(day.date)
            },
        )
    }
}

/** 前後およそ100年分。この範囲を超えて遡る使い方は想定しない。 */
private const val PAGE_COUNT = 2401

/** 基準の年月（[CalendarViewModel.anchorMonth]）を置くページ。 */
private const val ANCHOR_PAGE = PAGE_COUNT / 2

@Composable
private fun MonthHeader(
    yearMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.calendar_previous_month),
            )
        }
        Text(
            text = stringResource(
                R.string.calendar_month_format,
                yearMonth.year,
                yearMonth.monthValue,
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.calendar_next_month),
            )
        }
        TextButton(onClick = onToday) {
            Text(stringResource(R.string.calendar_today))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayEntriesSheet(
    day: SelectedDayUiState,
    onDismiss: () -> Unit,
    onOpenDiary: (diaryId: String, isDraft: Boolean) -> Unit,
    onCreateDiary: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPANESE) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = day.date.format(dateFormatter),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))

            if (day.cards.isEmpty()) {
                Text(
                    text = stringResource(R.string.calendar_sheet_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = day.cards, key = { it.id }) { card ->
                        DiaryCard(
                            state = card,
                            onClick = { onOpenDiary(card.id, card.isDraft) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onCreateDiary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.calendar_sheet_create),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
