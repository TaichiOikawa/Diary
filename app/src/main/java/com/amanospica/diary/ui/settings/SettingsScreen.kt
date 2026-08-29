package com.amanospica.diary.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amanospica.diary.R
import com.amanospica.diary.domain.model.BackupException
import com.amanospica.diary.domain.model.TextSpacing
import com.amanospica.diary.domain.model.ThemeMode
import com.amanospica.diary.ui.common.DiaryViewModelFactory
import com.amanospica.diary.ui.lock.canAuthenticateWithBiometrics
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = DiaryViewModelFactory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pinSetup by viewModel.pinSetup.collectAsStateWithLifecycle()
    val backup by viewModel.backup.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val biometricAvailable = context.canAuthenticateWithBiometrics()

    // SAF でファイルを選んでもらうので、ストレージ権限は要らない
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)
    ) { uri -> uri?.let { viewModel.exportTo(it.toString()) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.requestImport(it.toString()) } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_section_security))

            SettingRow(
                title = stringResource(R.string.settings_pin),
                description = if (settings.hasPin) {
                    stringResource(R.string.settings_pin_set)
                } else {
                    stringResource(R.string.settings_pin_unset)
                },
            ) {
                TextButton(onClick = viewModel::startPinSetup) {
                    Text(
                        stringResource(
                            if (settings.hasPin) R.string.settings_pin_change
                            else R.string.settings_pin_create
                        )
                    )
                }
            }

            SettingRow(
                title = stringResource(R.string.settings_lock),
                description = stringResource(R.string.settings_lock_description),
            ) {
                Switch(
                    checked = settings.isLockEnabled,
                    onCheckedChange = viewModel::setLockEnabled,
                    enabled = settings.hasPin,
                )
            }

            SettingRow(
                title = stringResource(R.string.settings_biometric),
                description = if (biometricAvailable) {
                    stringResource(R.string.settings_biometric_description)
                } else {
                    stringResource(R.string.settings_biometric_unavailable)
                },
            ) {
                Switch(
                    checked = settings.isBiometricEnabled,
                    onCheckedChange = viewModel::setBiometricEnabled,
                    enabled = settings.isLockEnabled && biometricAvailable,
                )
            }

            if (settings.hasPin) {
                TextButton(onClick = viewModel::clearPin) {
                    Text(
                        text = stringResource(R.string.settings_pin_clear),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            SectionTitle(stringResource(R.string.settings_section_appearance))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(stringResource(mode.labelRes())) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.settings_text_spacing),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.settings_text_spacing_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextSpacing.entries.forEach { spacing ->
                    FilterChip(
                        selected = settings.textSpacing == spacing,
                        onClick = { viewModel.setTextSpacing(spacing) },
                        label = { Text(stringResource(spacing.labelRes())) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            SectionTitle(stringResource(R.string.settings_section_data))

            SettingRow(
                title = stringResource(R.string.settings_export),
                description = stringResource(R.string.settings_export_description),
            ) {
                BackupAction(
                    label = stringResource(R.string.settings_export_action),
                    isRunning = backup.runningTask == BackupTask.EXPORT,
                    isEnabled = backup.runningTask == null,
                    onClick = { exportLauncher.launch(backupFileName()) },
                )
            }

            SettingRow(
                title = stringResource(R.string.settings_import),
                description = stringResource(R.string.settings_import_description),
            ) {
                BackupAction(
                    label = stringResource(R.string.settings_import_action),
                    isRunning = backup.runningTask == BackupTask.IMPORT,
                    isEnabled = backup.runningTask == null,
                    onClick = { importLauncher.launch(BACKUP_MIME_TYPES) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            SectionTitle(stringResource(R.string.settings_section_update))

            SettingRow(
                title = stringResource(R.string.settings_version),
                description = update.currentVersion,
            ) {
                if (update.isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = viewModel::checkForUpdate) {
                        Text(stringResource(R.string.settings_update_check_action))
                    }
                }
            }

            // 新しいバージョンがあった場合はダイアログが出るので、ここには何も出さない
            update.result?.let { result ->
                Text(
                    text = stringResource(result.messageRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (result) {
                        UpdateCheckResult.UP_TO_DATE -> MaterialTheme.colorScheme.onSurfaceVariant
                        UpdateCheckResult.FAILED -> MaterialTheme.colorScheme.error
                    },
                )
            }

            SettingRow(
                title = stringResource(R.string.settings_update_auto_check),
                description = stringResource(R.string.settings_update_auto_check_description),
            ) {
                Switch(
                    checked = settings.isAutoUpdateCheckEnabled,
                    onCheckedChange = viewModel::setAutoUpdateCheckEnabled,
                )
            }
        }
    }

    if (backup.pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text(stringResource(R.string.settings_import_confirm_title)) },
            text = { Text(stringResource(R.string.settings_import_confirm_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmImport) {
                    Text(stringResource(R.string.settings_import_action))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelImport) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    backup.result?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBackupResult,
            title = { Text(stringResource(result.titleRes())) },
            text = { Text(result.message()) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissBackupResult) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    if (pinSetup.isOpen) {
        PinSetupDialog(
            state = pinSetup,
            onInput = viewModel::onPinInput,
            onNext = viewModel::proceedToConfirm,
            onConfirm = viewModel::confirmPin,
            onDismiss = viewModel::cancelPinSetup,
        )
    }
}

@Composable
private fun PinSetupDialog(
    state: PinSetupState,
    onInput: (String) -> Unit,
    onNext: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val invalidFormat = stringResource(R.string.settings_pin_invalid)
    val mismatch = stringResource(R.string.settings_pin_mismatch)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (state.isConfirmStep) R.string.settings_pin_confirm_title
                    else R.string.settings_pin_new_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = if (state.isConfirmStep) state.confirmPin else state.pin,
                    onValueChange = onInput,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    supportingText = { Text(stringResource(R.string.settings_pin_hint)) },
                    isError = state.errorMessage != null,
                )
                state.errorMessage?.let { message ->
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (state.isConfirmStep) onConfirm(mismatch) else onNext(invalidFormat)
                }
            ) {
                Text(
                    stringResource(
                        if (state.isConfirmStep) R.string.action_confirm else R.string.action_next
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * 書き出し・読み込みのボタン。
 * 処理中はボタンごとくるくるに差し替えて、二重に押せないことが見て分かるようにする。
 */
@Composable
private fun BackupAction(
    label: String,
    isRunning: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    if (isRunning) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    } else {
        TextButton(onClick = onClick, enabled = isEnabled) { Text(label) }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

private fun UpdateCheckResult.messageRes(): Int = when (this) {
    UpdateCheckResult.UP_TO_DATE -> R.string.settings_update_up_to_date
    UpdateCheckResult.FAILED -> R.string.settings_update_check_failed
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

private fun TextSpacing.labelRes(): Int = when (this) {
    TextSpacing.SMALL -> R.string.settings_text_spacing_small
    TextSpacing.MEDIUM -> R.string.settings_text_spacing_medium
    TextSpacing.LARGE -> R.string.settings_text_spacing_large
}

private const val BACKUP_MIME_TYPE = "application/zip"

/**
 * 読み込みで選ばせるファイルの種類。
 * ZIP の MIME は端末や保存元のアプリによって揺れるので、代表的なものと汎用のバイナリを併記する。
 */
private val BACKUP_MIME_TYPES = arrayOf(
    BACKUP_MIME_TYPE,
    "application/x-zip-compressed",
    "application/octet-stream",
)

/** 保存ダイアログに最初から入れておくファイル名。日付を入れて、いつのものか一覧で分かるようにする。 */
private fun backupFileName(): String =
    "diary-backup-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.zip"

private fun BackupResult.titleRes(): Int = when (this) {
    is BackupResult.Exported -> R.string.settings_export_done_title
    is BackupResult.Imported -> R.string.settings_import_done_title
    is BackupResult.Failed -> when (task) {
        BackupTask.EXPORT -> R.string.settings_export_failed_title
        BackupTask.IMPORT -> R.string.settings_import_failed_title
    }
}

@Composable
private fun BackupResult.message(): String = when (this) {
    is BackupResult.Exported ->
        stringResource(R.string.settings_export_done, summary.diaryCount, summary.mediaCount)

    is BackupResult.Imported -> stringResource(
        R.string.settings_import_done,
        summary.added,
        summary.updated,
        summary.skipped,
        summary.mediaRestored,
    )

    is BackupResult.Failed -> stringResource(reason.messageRes())
}

private fun BackupException.Reason.messageRes(): Int = when (this) {
    BackupException.Reason.NOT_A_BACKUP -> R.string.settings_backup_error_not_backup
    BackupException.Reason.UNSUPPORTED_VERSION -> R.string.settings_backup_error_version
    BackupException.Reason.IO -> R.string.settings_backup_error_io
}
