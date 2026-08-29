package com.amanospica.diary.domain.usecase

import com.amanospica.diary.domain.model.DiaryStats
import com.amanospica.diary.domain.model.EmojiCount
import com.amanospica.diary.domain.model.MonthlyCount
import com.amanospica.diary.domain.model.StreakCalculator
import com.amanospica.diary.domain.repository.DiaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth

/**
 * ダッシュボード用の統計をまとめて購読する。
 *
 * 集計そのものは SQL 側（件数・日付一覧・絵文字別・月別）で済ませ、
 * ここでは継続日数の算出と表示用の整形だけを行う。
 */
class ObserveDiaryStatsUseCase(
    private val repository: DiaryRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    operator fun invoke(): Flow<DiaryStats> = combine(
        repository.observeTotalCount(),
        repository.observeRecordedDates(),
        repository.observeEmojiCounts(),
        repository.observeMonthlyCounts(),
    ) { total, recordedDates, emojiCounts, monthlyCounts ->
        val now = today()
        val currentMonth = YearMonth.from(now)

        DiaryStats(
            totalCount = total,
            recordedDayCount = recordedDates.size,
            currentStreak = StreakCalculator.currentStreak(recordedDates, now),
            longestStreak = StreakCalculator.longestStreak(recordedDates),
            thisMonthCount = monthlyCounts.firstOrNull { it.yearMonth == currentMonth }?.count ?: 0,
            monthlyCounts = monthlyCounts.toRecentMonths(currentMonth, MONTHS_ON_CHART),
            emojiCounts = emojiCounts.toRatios(total),
        )
    }

    /** 投稿の無い月も 0 として埋め、直近 [count] ヶ月ぶんの連続した並びにする。 */
    private fun List<MonthlyCount>.toRecentMonths(
        currentMonth: YearMonth,
        count: Int,
    ): List<MonthlyCount> {
        val byMonth = associate { it.yearMonth to it.count }
        return (count - 1 downTo 0).map { offset ->
            val month = currentMonth.minusMonths(offset.toLong())
            MonthlyCount(month, byMonth[month] ?: 0)
        }
    }

    private fun Map<String, Int>.toRatios(total: Int): List<EmojiCount> =
        entries.sortedByDescending { it.value }
            .map { (emoji, count) ->
                EmojiCount(
                    emoji = emoji,
                    count = count,
                    ratio = if (total == 0) 0f else count.toFloat() / total,
                )
            }

    private companion object {
        const val MONTHS_ON_CHART = 6
    }
}
