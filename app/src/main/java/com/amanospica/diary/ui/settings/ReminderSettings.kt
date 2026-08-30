package com.amanospica.diary.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amanospica.diary.R
import com.amanospica.diary.domain.model.AppSettings
import com.amanospica.diary.domain.model.ReminderCondition
import com.amanospica.diary.notification.canPostDiaryNotifications
import java.time.LocalTime

/**
 * 設定画面の「通知」セクション。
 *
 * 時刻と条件はスイッチが入っているときにだけ出す。
 * 通知を使わない人に、効かない設定を並べて見せないため。
 */
@Composable
fun ReminderSection(
    settings: AppSettings,
    onEnabledChange: (Boolean) -> Unit,
    onConditionChange: (ReminderCondition) -> Unit,
    onOpenTimePicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notificationsAllowed = rememberNotificationsAllowed()
    var isPermissionDenied by remember { mutableStateOf(false) }

    // Android 13 以降は通知に実行時許可が要る。
    // 許可されないまま設定だけ有効にしても鳴らないので、許可が下りてから有効にする。
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isPermissionDenied = !granted
        if (granted) onEnabledChange(true)
    }

    Column(modifier = modifier) {
        SettingRow(
            title = stringResource(R.string.settings_reminder),
            description = stringResource(R.string.settings_reminder_description),
        ) {
            Switch(
                checked = settings.isReminderEnabled,
                onCheckedChange = { checked ->
                    when {
                        !checked -> onEnabledChange(false)
                        needsPermissionRequest(notificationsAllowed) ->
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

                        else -> onEnabledChange(true)
                    }
                },
            )
        }

        // 許可を断られたあとや、端末の設定で通知を止められている場合は、
        // スイッチを入れても鳴らない。理由が分からないままにしない。
        if (!notificationsAllowed && (isPermissionDenied || settings.isReminderEnabled)) {
            Text(
                text = stringResource(R.string.settings_reminder_blocked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (settings.isReminderEnabled) {
            SettingRow(
                title = stringResource(R.string.settings_reminder_time),
                description = settings.reminderTime.formatted(),
            ) {
                TextButton(onClick = onOpenTimePicker) {
                    Text(stringResource(R.string.settings_reminder_time_change))
                }
            }

            Text(
                text = stringResource(R.string.settings_reminder_condition),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReminderCondition.entries.forEach { condition ->
                    FilterChip(
                        selected = settings.reminderCondition == condition,
                        onClick = { onConditionChange(condition) },
                        label = { Text(stringResource(condition.labelRes())) },
                    )
                }
            }
            Text(
                text = stringResource(settings.reminderCondition.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 通知する時刻を選ぶダイアログ。
 *
 * 24時間表示に固定する。設定した時刻はそのまま通知の予約に使うので、
 * 午前・午後の取り違えが起きない形にしておきたい。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimePickerDialog(
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reminder_time_title)) },
        text = {
            // 文字盤ではなく数字入力にするのは、ダイアログの中に収まる高さで、
            // 分単位の指定も一度で済むため。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TimeInput(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** 実行時許可を求める必要があるか。Android 12 以前には求める先が無い。 */
private fun needsPermissionRequest(notificationsAllowed: Boolean): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAllowed

/**
 * 通知を出せる状態かどうか。
 *
 * 端末の設定画面で許可を変えて戻ってくることがあるので、
 * 画面が前面へ戻るたびに見直す（一度読んだ結果を持ち続けない）。
 */
@Composable
private fun rememberNotificationsAllowed(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(context.canPostDiaryNotifications()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = context.canPostDiaryNotifications()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return allowed
}

@Composable
private fun LocalTime.formatted(): String =
    stringResource(R.string.settings_reminder_time_format, hour, minute)

private fun ReminderCondition.labelRes(): Int = when (this) {
    ReminderCondition.WHEN_UNWRITTEN -> R.string.settings_reminder_condition_when_unwritten
    ReminderCondition.ALWAYS -> R.string.settings_reminder_condition_always
}

private fun ReminderCondition.descriptionRes(): Int = when (this) {
    ReminderCondition.WHEN_UNWRITTEN ->
        R.string.settings_reminder_condition_when_unwritten_description

    ReminderCondition.ALWAYS -> R.string.settings_reminder_condition_always_description
}
