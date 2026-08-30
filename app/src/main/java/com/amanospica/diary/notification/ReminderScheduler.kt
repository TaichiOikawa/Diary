package com.amanospica.diary.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * リマインダーの時刻を AlarmManager に予約する。
 *
 * 繰り返しアラーム（setRepeating）ではなく、鳴るたびに次の1回を入れ直す方式を採る。
 * 繰り返しアラームは登録時の間隔で固定されるため、夏時間や日付をまたぐ設定変更で
 * 指定時刻から少しずつずれていくため。
 */
class ReminderScheduler(context: Context) {

    private val appContext: Context = context.applicationContext

    private val alarmManager: AlarmManager? = appContext.getSystemService()

    /**
     * 次に [time] が来るときの通知を予約する。すでに予約があれば置き換える。
     *
     * 正確なアラーム（setExactAndAllowWhileIdle）は Android 12 以降で追加の許可が要る。
     * 日記のリマインダーは数分ずれても困らないので許可を求めず、
     * Doze 中でも動く [AlarmManager.setAndAllowWhileIdle] を使う。
     */
    fun schedule(time: LocalTime, now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())) {
        val manager = alarmManager ?: return
        val triggerAt = nextReminderAt(time, now).toInstant().toEpochMilli()
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, remindIntent())
    }

    fun cancel() {
        alarmManager?.cancel(remindIntent())
    }

    /**
     * 予約と取り消しで同じものを指す必要があるため、
     * リクエストコードと Intent の中身を1箇所で作る。
     */
    private fun remindIntent(): PendingIntent {
        val intent = Intent(appContext, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMIND)
        return PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_REMIND,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_CODE_REMIND = 3001
    }
}
