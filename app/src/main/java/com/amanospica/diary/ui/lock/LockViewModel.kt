package com.amanospica.diary.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amanospica.diary.domain.repository.SettingsRepository
import com.amanospica.diary.domain.security.AppLockController
import com.amanospica.diary.domain.security.PinHasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LockUiState(
    val pin: String = "",
    /** 登録済み PIN の桁数。0 は桁数不明（この機能より前に設定された PIN）。 */
    val pinLength: Int = 0,
    val isBiometricEnabled: Boolean = false,
    val isVerifying: Boolean = false,
    val hasError: Boolean = false,
) {
    /** 入力欄に並べる丸の数。桁数が分からないときは入力できる最大桁ぶん出す。 */
    val indicatorCount: Int
        get() = if (pinLength > 0) pinLength else PinHasher.MAX_LENGTH

    /** 桁数が分かっていればちょうどその桁で、分からなければ最短桁から照合する。 */
    fun isReadyToVerify(input: String): Boolean =
        if (pinLength > 0) input.length == pinLength else input.length >= PinHasher.MIN_LENGTH
}

class LockViewModel(
    private val settingsRepository: SettingsRepository,
    private val appLockController: AppLockController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    init {
        // ViewModel は Activity と同じ寿命なので、一度読むだけだと
        // 同じ起動中に PIN を変えたときに桁数が古いままになる。設定は購読して追従させる
        settingsRepository.settings
            .onEach { settings ->
                _uiState.update {
                    it.copy(
                        pinLength = settings.pinLength,
                        isBiometricEnabled = settings.isBiometricEnabled,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun appendDigit(digit: Char) {
        val current = _uiState.value
        if (current.isVerifying || current.pin.length >= current.indicatorCount) return

        val pin = current.pin + digit
        _uiState.update { it.copy(pin = pin, hasError = false) }
        // 桁が埋まったら自動で照合し、確定ボタンを押す手間をなくす
        if (current.isReadyToVerify(pin)) verify(pin)
    }

    fun deleteDigit() {
        _uiState.update { it.copy(pin = it.pin.dropLast(1), hasError = false) }
    }

    fun onBiometricSucceeded() = appLockController.unlock()

    private fun verify(pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifying = true) }
            val isCorrect = settingsRepository.verifyPin(pin)
            val knowsLength = _uiState.value.pinLength > 0

            when {
                isCorrect -> {
                    appLockController.unlock()
                    _uiState.update { it.copy(pin = "", isVerifying = false, hasError = false) }
                }
                // 桁数が分かっていれば、その桁で外れた時点で入力をやり直させる。
                // 分からない場合だけ、上限桁まで入力を続けさせる
                knowsLength || pin.length >= PinHasher.MAX_LENGTH ->
                    _uiState.update { it.copy(pin = "", isVerifying = false, hasError = true) }

                else -> _uiState.update { it.copy(isVerifying = false) }
            }
        }
    }
}
