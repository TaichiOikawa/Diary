package com.amanospica.diary.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.amanospica.diary.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 予約した時刻に AlarmManager から呼ばれる受信側。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val container = context.appContainer
        // 日記の読み出しと DataStore の読み出しが要るので、onReceive の中では終わらない
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                container.reminderController.onReminderFired()
            } catch (error: Throwable) {
                Log.w(TAG, "リマインダーの処理に失敗", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /** このアプリの中だけで使う。予約した通知の時刻が来たことを表す。 */
        const val ACTION_REMIND = "com.amanospica.diary.action.REMIND"

        private const val TAG = "ReminderReceiver"
    }
}

/**
 * 端末の再起動とアプリ更新のあとに予約を入れ直す受信側。
 *
 * AlarmManager の予約はどちらでも消えるため、ここで復元しないと
 * 「設定したのに鳴らない」状態のまま気づけない。
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return
        val container = context.appContainer
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                container.reminderController.reschedule()
            } catch (error: Throwable) {
                Log.w(TAG, "リマインダーの再予約に失敗", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )

        const val TAG = "ReminderBootReceiver"
    }
}
