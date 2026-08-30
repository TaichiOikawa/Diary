package com.amanospica.diary.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * このアプリが通知を出せる状態か。
 *
 * Android 13 以降は実行時許可が要る。加えて、許可があっても端末の設定で
 * アプリごと通知を止められていることがあるので、その両方を見る。
 */
fun Context.canPostDiaryNotifications(): Boolean =
    hasNotificationPermission() && NotificationManagerCompat.from(this).areNotificationsEnabled()

/**
 * POST_NOTIFICATIONS が許可されているか。
 * この許可は Android 13 で追加されたので、それ以前の端末では常に許可扱いにする。
 */
fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
