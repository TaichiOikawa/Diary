package com.amanospica.diary.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amanospica.diary.R
import com.amanospica.diary.domain.model.DiaryStats
import com.amanospica.diary.ui.common.DiaryViewModelFactory
import com.amanospica.diary.ui.common.EmptyState
import java.time.YearMonth

/**
 * ダッシュボード画面。継続日数などの数値はタイル、推移と内訳はグラフで見せる。
 */
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(factory = DiaryViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.stats.isEmpty) {
        EmptyState(
            emoji = "📊",
            title = stringResource(R.string.dashboard_empty_title),
            message = stringResource(R.string.dashboard_empty_message),
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    DashboardContent(stats = uiState.stats, modifier = modifier)
}

@Composable
private fun DashboardContent(
    stats: DiaryStats,
    modifier: Modifier = Modifier,
) {
    val currentMonth = remember { YearMonth.now() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 下部中央の作成ボタンに最後の行が隠れないよう余白を足す
            .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                value = stats.currentStreak.toString(),
                unit = stringResource(R.string.dashboard_unit_days),
                label = stringResource(R.string.dashboard_current_streak),
                accent = true,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = stats.totalCount.toString(),
                unit = stringResource(R.string.dashboard_unit_entries),
                label = stringResource(R.string.dashboard_total_count),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                value = stats.longestStreak.toString(),
                unit = stringResource(R.string.dashboard_unit_days),
                label = stringResource(R.string.dashboard_longest_streak),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = stats.recordedDayCount.toString(),
                unit = stringResource(R.string.dashboard_unit_days),
                label = stringResource(R.string.dashboard_recorded_days),
                modifier = Modifier.weight(1f),
            )
        }

        Section(title = stringResource(R.string.dashboard_monthly_title)) {
            MonthlyBarChart(
                monthlyCounts = stats.monthlyCounts,
                currentMonth = currentMonth,
            )
        }

        Section(title = stringResource(R.string.dashboard_mood_title)) {
            EmojiBreakdown(emojiCounts = stats.emojiCounts)
        }
    }
}

@Composable
private fun Section(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
