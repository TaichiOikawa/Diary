package com.amanospica.diary.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.amanospica.diary.MainActivity
import com.amanospica.diary.R

/**
 * リマインダー通知の組み立てと送信。
 *
 * 通知チャンネルは「送る直前」に作る。設定画面を開いたこともない利用者の端末に、
 * 使われないチャンネルだけが並ぶのを避けるため。作成は同じ ID なら何度呼んでも無害。
 */
class DiaryNotifier(context: Context) {

    private val appContext: Context = context.applicationContext

    /**
     * 通知を出す。OS 側で通知が止められている場合は何もせず false を返す。
     */
    fun notifyReminder(): Boolean {
        if (!canPostNotifications()) return false
        createChannel()

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.reminder_notification_title))
            .setContentText(appContext.getString(R.string.reminder_notification_message))
            .setContentIntent(openAppIntent())
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // タップしたら用は済むので、通知領域に残さない
            .setAutoCancel(true)
            .build()

        // 直前に確認していても、許可はいつでも取り消されうる。
        // 通知1つのために落とす価値はないので、失敗は握って false を返す。
        return runCatching {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        }.isSuccess
    }

    /**
     * 通知を出せる状態か。
     *
     * Android 13 以降は実行時許可が要る。設定画面でチャンネルごと切られている場合も
     * [NotificationManagerCompat.areNotificationsEnabled] が false になる。
     */
    fun canPostNotifications(): Boolean {
        // POST_NOTIFICATIONS は Android 13 で追加された。それ以前の端末では
        // OS に存在しない許可なので、問い合わせずに「許可あり」として扱う。
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = appContext.getString(R.string.reminder_channel_description)
        }
        NotificationManagerCompat.from(appContext).createNotificationChannel(channel)
    }

    /**
     * 通知をタップしたときの遷移先。
     *
     * すでにアプリが開いていれば、そのときの画面をそのまま前面へ出す
     * （書きかけの編集画面が作り直されて入力が飛ぶのを避ける）。
     */
    private fun openAppIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            appContext,
            REQUEST_CODE_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val CHANNEL_ID = "diary_reminder"

        /** 通知は常に1つだけ。前の分が残っていたら新しい内容で置き換える。 */
        const val NOTIFICATION_ID = 1001
        const val REQUEST_CODE_OPEN_APP = 2001
    }
}
