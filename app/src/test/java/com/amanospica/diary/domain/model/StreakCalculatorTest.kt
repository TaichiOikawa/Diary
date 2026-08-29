package com.amanospica.diary.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    private val today = LocalDate.of(2026, 8, 2)

    /** 8月の日付を並べるヘルパー。 */
    private fun days(vararg dayOfMonth: Int) =
        dayOfMonth.map { LocalDate.of(2026, 8, it) }

    private val july31 = LocalDate.of(2026, 7, 31)

    @Test
    fun `記録がなければ0`() {
        assertEquals(0, StreakCalculator.currentStreak(emptyList(), today))
        assertEquals(0, StreakCalculator.longestStreak(emptyList()))
    }

    @Test
    fun `今日まで連続していれば今日を含めて数える`() {
        assertEquals(3, StreakCalculator.currentStreak(days(1, 2) + july31, today))
    }

    @Test
    fun `今日未記入でも昨日まで続いていれば継続とみなす`() {
        assertEquals(2, StreakCalculator.currentStreak(days(1) + july31, today))
    }

    @Test
    fun `2日以上空いていれば継続は途切れる`() {
        assertEquals(0, StreakCalculator.currentStreak(days(28, 29, 30), today))
    }

    @Test
    fun `同じ日に複数書いても1日として数える`() {
        val dates = listOf(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 2),
        )
        assertEquals(2, StreakCalculator.currentStreak(dates, today))
        assertEquals(2, StreakCalculator.longestStreak(dates))
    }

    @Test
    fun `最長連続は過去の途切れた区間からも拾う`() {
        // 8/1〜8/4 の4連続が最長。8/10-11 と 7/20 は短い
        val dates = days(1, 2, 3, 4, 10, 11) + LocalDate.of(2026, 7, 20)
        assertEquals(4, StreakCalculator.longestStreak(dates))
    }

    @Test
    fun `月をまたいでも連続として扱う`() {
        val dates = listOf(
            LocalDate.of(2026, 7, 30),
            july31,
            LocalDate.of(2026, 8, 1),
        )
        assertEquals(3, StreakCalculator.longestStreak(dates))
    }

    @Test
    fun `記録が1日だけなら最長は1`() {
        assertEquals(1, StreakCalculator.longestStreak(days(2)))
    }
}
