package com.amanospica.diary.ui.editor

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.amanospica.diary.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 日記の日付を選ぶダイアログ。
 * Material3 の DatePicker は UTC ミリ秒で値をやり取りするため、境界の日付がずれないよう
 * [LocalDate] ↔ UTC ミリ秒の変換をここで閉じ込める。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDatePickerDialog(
    date: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = state.selectedDateMillis
                        ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        ?: date
                    onSelect(selected)
                }
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    ) {
        DatePicker(state = state)
    }
}
