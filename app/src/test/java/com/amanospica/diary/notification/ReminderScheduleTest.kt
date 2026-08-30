package com.amanospica.diary.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderScheduleTest {

    private val zone = ZoneId.of("Asia/Tokyo")

    private fun at(hour: Int, minute: Int, dayOfMonth: Int = 30): ZonedDateTime =
        ZonedDateTime.of(LocalDate.of(2026, 8, dayOfMonth), LocalTime.of(hour, minute), zone)

    @Test
    fun `指定時刻がまだ来ていなければ今日のその時刻`() {
        val next = nextReminderAt(LocalTime.of(21, 0), at(hour = 9, minute = 0))
        assertEquals(at(hour = 21, minute = 0), next)
    }

    @Test
    fun `指定時刻を過ぎていれば翌日のその時刻`() {
        val next = nextReminderAt(LocalTime.of(21, 0), at(hour = 22, minute = 30))
        assertEquals(at(hour = 21, minute = 0, dayOfMonth = 31), next)
    }

    @Test
    fun `ちょうど指定時刻なら翌日へ送る`() {
        // 通知を出した直後に次回を予約するため、同じ時刻をもう一度拾うと二重に鳴る
        val next = nextReminderAt(LocalTime.of(21, 0), at(hour = 21, minute = 0))
        assertEquals(at(hour = 21, minute = 0, dayOfMonth = 31), next)
    }

    @Test
    fun `秒以下は落として分ちょうどに合わせる`() {
        val now = at(hour = 9, minute = 0).withSecond(30).withNano(500)
        val next = nextReminderAt(LocalTime.of(21, 0, 45), now)
        assertEquals(at(hour = 21, minute = 0), next)
    }

    @Test
    fun `月末をまたいでも翌日の時刻になる`() {
        val next = nextReminderAt(LocalTime.of(7, 30), at(hour = 8, minute = 0, dayOfMonth = 31))
        assertEquals(
            ZonedDateTime.of(LocalDate.of(2026, 9, 1), LocalTime.of(7, 30), zone),
            next,
        )
    }
}
