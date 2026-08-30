package com.amanospica.diary.notification

import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * [time] に通知するとして、[now] の次にその時刻が来るのはいつかを返す。
 *
 * ちょうど [time] の瞬間は「今回の分」として扱わず翌日へ送る。通知を出した直後に
 * 次回を予約すると、丸め方によっては同じ時刻をもう一度拾って二重に鳴りかねないため。
 *
 * 夏時間の切り替えで指定時刻がその日に存在しない／2回あるといった揺れは
 * [ZonedDateTime.with] の解決に任せる（存在しない時刻は後ろへずれる）。
 */
fun nextReminderAt(time: LocalTime, now: ZonedDateTime): ZonedDateTime {
    val todayAt = now.with(time.truncatedToMinute())
    return if (todayAt.isAfter(now)) todayAt else now.plusDays(1).with(time.truncatedToMinute())
}

/** 秒以下は通知の精度に対して細かすぎるので落とす。 */
private fun LocalTime.truncatedToMinute(): LocalTime = LocalTime.of(hour, minute)
