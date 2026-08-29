package com.amanospica.diary.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amanospica.diary.domain.model.AppSettings
import com.amanospica.diary.domain.model.BackupException
import com.amanospica.diary.domain.model.ExportSummary
import com.amanospica.diary.domain.model.ImportSummary
import com.amanospica.diary.domain.model.TextSpacing
import com.amanospica.diary.domain.model.ThemeMode
import com.amanospica.diary.domain.repository.SettingsRepository
import com.amanospica.diary.domain.usecase.ExportDiariesUseCase
import com.amanospica.diary.domain.usecase.ImportDiariesUseCase
import com.amanospica.diary.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** PIN 設定ダイアログの入力状態。 */
data class PinSetupState(
    val isOpen: Boolean = false,
    val pin: String = "",
    val confirmPin: String = "",
    val isConfirmStep: Boolean = false,
    val errorMessage: String? = null,
)

/** いま動いているバックアップ処理。書き出しと読み込みは同時に走らせない。 */
enum class BackupTask { EXPORT, IMPORT }

/** 書き出し・読み込みが終わったときに見せる内容。 */
sealed interface BackupResult {
    data class Exported(val summary: ExportSummary) : BackupResult
    data class Imported(val summary: ImportSummary) : BackupResult
    /** [task] を持つのは、失敗の見出しを「書き出し」「読み込み」で言い分けるため。 */
    data class Failed(val task: BackupTask, val reason: BackupException.Reason) : BackupResult
}

/** 手動で更新を確認したあとに、この画面で伝えること。 */
enum class UpdateCheckResult { UP_TO_DATE, FAILED }

/** 設定画面から見た更新確認の状態。 */
data class UpdateCheckState(
    val currentVersion: String,
    val isChecking: Boolean = false,
    /** 新しいバージョンが見つかった場合はダイアログに任せるので null のまま。 */
    val result: UpdateCheckResult? = null,
)

/** 書き出し・読み込みの進行状況。 */
data class BackupState(
    val runningTask: BackupTask? = null,
    /** 読み込むファイルを選び終え、実行の確認を待っている状態。 */
    val pendingImportUri: String? = null,
    val result: BackupResult? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val exportDiaries: ExportDiariesUseCase,
    private val importDiaries: ImportDiariesUseCase,
    private val updateManager: UpdateManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _pinSetup = MutableStateFlow(PinSetupState())
    val pinSetup: StateFlow<PinSetupState> = _pinSetup.asStateFlow()

    private val _backup = MutableStateFlow(BackupState())
    val backup: StateFlow<BackupState> = _backup.asStateFlow()

    private val _update = MutableStateFlow(UpdateCheckState(currentVersion = updateManager.versionName))
    val update: StateFlow<UpdateCheckState> = _update.asStateFlow()

    fun startPinSetup() {
        _pinSetup.value = PinSetupState(isOpen = true)
    }

    fun cancelPinSetup() {
        _pinSetup.value = PinSetupState()
    }

    fun onPinInput(value: String) {
        val digits = value.filter { it.isDigit() }.take(8)
        _pinSetup.value = _pinSetup.value.let { state ->
            if (state.isConfirmStep) {
                state.copy(confirmPin = digits, errorMessage = null)
            } else {
                state.copy(pin = digits, errorMessage = null)
            }
        }
    }

    /** 1回目の入力を確定し、確認入力へ進む。 */
    fun proceedToConfirm(invalidFormatMessage: String) {
        val state = _pinSetup.value
        if (state.pin.length !in 4..8) {
            _pinSetup.value = state.copy(errorMessage = invalidFormatMessage)
            return
        }
        _pinSetup.value = state.copy(isConfirmStep = true, errorMessage = null)
    }

    fun confirmPin(mismatchMessage: String) {
        val state = _pinSetup.value
        if (state.pin != state.confirmPin) {
            _pinSetup.value = state.copy(confirmPin = "", errorMessage = mismatchMessage)
            return
        }
        viewModelScope.launch {
            settingsRepository.setPin(state.pin)
            _pinSetup.value = PinSetupState()
        }
    }

    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLockEnabled(enabled) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBiometricEnabled(enabled) }
    }

    fun clearPin() {
        viewModelScope.launch { settingsRepository.clearPin() }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setTextSpacing(spacing: TextSpacing) {
        viewModelScope.launch { settingsRepository.setTextSpacing(spacing) }
    }

    // --- アプリの更新 ---

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoUpdateCheckEnabled(enabled)
            // 自動確認に戻したなら、以前スキップしたバージョンもまた知らせてよい
            if (enabled) settingsRepository.setSkippedUpdateVersion(null)
        }
    }

    /**
     * 手動で更新を確認する。
     *
     * 新しいバージョンが見つかった場合は [UpdateManager] が全画面共通のダイアログを出すので、
     * ここでは何も言わない。この画面で伝えるのは「最新だった」「確認に失敗した」だけ。
     */
    fun checkForUpdate() {
        if (_update.value.isChecking) return
        _update.value = _update.value.copy(isChecking = true, result = null)
        viewModelScope.launch {
            val result = updateManager.checkForUpdate(manual = true).fold(
                onSuccess = { release -> if (release == null) UpdateCheckResult.UP_TO_DATE else null },
                onFailure = { UpdateCheckResult.FAILED },
            )
            _update.value = _update.value.copy(isChecking = false, result = result)
        }
    }

    // --- 書き出し / 読み込み ---

    fun exportTo(destinationUri: String) = runBackup(BackupTask.EXPORT) {
        exportDiaries(destinationUri).fold(
            onSuccess = { BackupResult.Exported(it) },
            onFailure = { BackupResult.Failed(BackupTask.EXPORT, it.toReason()) },
        )
    }

    /**
     * 読み込むファイルが選ばれた。
     * 同じ日記が上書きされることがあるので、すぐには実行せず確認を挟む。
     */
    fun requestImport(sourceUri: String) {
        _backup.value = BackupState(pendingImportUri = sourceUri)
    }

    fun cancelImport() {
        _backup.value = BackupState()
    }

    fun confirmImport() {
        val sourceUri = _backup.value.pendingImportUri ?: return
        runBackup(BackupTask.IMPORT) {
            importDiaries(sourceUri).fold(
                onSuccess = { BackupResult.Imported(it) },
                onFailure = { BackupResult.Failed(BackupTask.IMPORT, it.toReason()) },
            )
        }
    }

    fun dismissBackupResult() {
        _backup.value = BackupState()
    }

    /** 実行中は次の要求を受け付けない。書き出しと読み込みが同じファイルを取り合うのを防ぐ。 */
    private fun runBackup(task: BackupTask, block: suspend () -> BackupResult) {
        if (_backup.value.runningTask != null) return
        _backup.value = BackupState(runningTask = task)
        viewModelScope.launch { _backup.value = BackupState(result = block()) }
    }

    /** 想定していない例外も、利用者から見れば「読み書きに失敗した」なのでそこへ畳む。 */
    private fun Throwable.toReason(): BackupException.Reason =
        (this as? BackupException)?.reason ?: BackupException.Reason.IO
}
