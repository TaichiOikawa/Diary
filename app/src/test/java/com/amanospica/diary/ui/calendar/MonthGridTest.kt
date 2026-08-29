package com.amanospica.diary.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthGridTest {

    @Test
    fun `各週は必ず7マスになる`() {
        listOf("2026-02", "2026-08", "2027-01", "2024-02").forEach { month ->
            val grid = buildMonthGrid(YearMonth.parse(month))
            grid.forEach { week -> assertEquals(month, 7, week.size) }
        }
    }

    @Test
    fun `どの月も6行になる`() {
        // 行数が月ごとに変わると、横スワイプの途中で表の高さが跳ねてしまう
        listOf("2026-02", "2026-08", "2027-01", "2024-02", "2026-05").forEach { month ->
            assertEquals(month, 6, buildMonthGrid(YearMonth.parse(month)).size)
        }
    }

    @Test
    fun `月初は曜日に対応する列に配置される`() {
        // 2026-08-01 は土曜日 → 日曜始まりなので7列目（index 6）
        val grid = buildMonthGrid(YearMonth.of(2026, 8))
        val firstWeek = grid.first()

        assertEquals(LocalDate.of(2026, 8, 1), firstWeek[6])
        (0..5).forEach { assertNull(firstWeek[it]) }
    }

    @Test
    fun `月の全日付が重複なく含まれる`() {
        val yearMonth = YearMonth.of(2026, 8)
        val days = buildMonthGrid(yearMonth).flatten().filterNotNull()

        assertEquals(yearMonth.lengthOfMonth(), days.size)
        assertEquals(yearMonth.atDay(1), days.first())
        assertEquals(yearMonth.atEndOfMonth(), days.last())
        assertEquals(days.size, days.distinct().size)
    }

    @Test
    fun `日曜始まりの月は先頭に空白が入らない`() {
        // 2026-02-01 は日曜日
        val firstWeek = buildMonthGrid(YearMonth.of(2026, 2)).first()

        assertEquals(LocalDate.of(2026, 2, 1), firstWeek[0])
    }
}
