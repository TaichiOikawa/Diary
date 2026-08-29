package com.amanospica.diary.domain.model

import java.time.LocalDate
import java.time.YearMonth

/** 月ごとの投稿数。 */
data class MonthlyCount(
    val yearMonth: YearMonth,
    val count: Int,
)

/** 絵文字ごとの投稿数と全体に占める割合。 */
data class EmojiCount(
    val emoji: String,
    val count: Int,
    /** 0f〜1f。 */
    val ratio: Float,
)

/** ダッシュボードに出す統計一式。 */
data class DiaryStats(
    val totalCount: Int = 0,
    val recordedDayCount: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val thisMonthCount: Int = 0,
    val monthlyCounts: List<MonthlyCount> = emptyList(),
    val emojiCounts: List<EmojiCount> = emptyList(),
) {
    val isEmpty: Boolean get() = totalCount == 0
}

/**
 * 日記を書いた日付の集合から継続日数を求める。
 *
 * 「今日まだ書いていない」だけで継続が途切れた表示になると体験が悪いので、
 * 現在のストリークは **今日または昨日** を終端とする連続日数として数える。
 */
object StreakCalculator {

    fun currentStreak(recordedDates: Collection<LocalDate>, today: LocalDate): Int {
        if (recordedDates.isEmpty()) return 0
        val days = recordedDates.toHashSet()

        val end = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return 0
        }

        var streak = 0
        var cursor = end
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun longestStreak(recordedDates: Collection<LocalDate>): Int {
        if (recordedDates.isEmpty()) return 0
        val sorted = recordedDates.distinct().sorted()

        var longest = 1
        var running = 1
        for (index in 1 until sorted.size) {
            running = if (sorted[index] == sorted[index - 1].plusDays(1)) running + 1 else 1
            longest = maxOf(longest, running)
        }
        return longest
    }
}
