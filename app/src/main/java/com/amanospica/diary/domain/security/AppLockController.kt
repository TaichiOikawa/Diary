package com.amanospica.diary.domain.security

import com.amanospica.diary.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** ロック画面を出すべきかどうか。 */
enum class LockState {
    /** ロック設定をまだ読み終えていない。日記の中身を出してはいけない。 */
    UNKNOWN,
    LOCKED,
    UNLOCKED,
}

/**
 * 「いまロック画面を出すべきか」を一元管理する。
 *
 * 実際のロック状態はプロセス内のフラグとして持ち、永続化しない。
 * アプリがバックグラウンドへ回るたびに施錠し、次に前面へ来たときに認証を求める。
 */
class AppLockController(
    settingsRepository: SettingsRepository,
    scope: CoroutineScope,
) {
    private val isLockedInternal = MutableStateFlow(true)

    /** ロック設定。DataStore から届くまでは null（＝有効か無効かまだ分からない）。 */
    private val lockEnabled = MutableStateFlow<Boolean?>(null)

    /**
     * ロック設定が有効かどうかと現在の施錠状態を合わせた、画面表示用の判定。
     *
     * 設定は DataStore から非同期で届くため、読み終える前に「ロック不要」と答えてしまうと、
     * 施錠中でも起動直後の1フレームだけ日記の中身が覗いてしまう。
     * それを避けるため、判定できるまでは [LockState.UNKNOWN] を流す。
     */
    val lockState: StateFlow<LockState> =
        combine(lockEnabled, isLockedInternal) { enabled, locked ->
            when {
                enabled == null -> LockState.UNKNOWN
                enabled && locked -> LockState.LOCKED
                else -> LockState.UNLOCKED
            }
        }.stateIn(scope, SharingStarted.Eagerly, LockState.UNKNOWN)

    init {
        settingsRepository.settings
            .onEach { settings ->
                lockEnabled.value = settings.isLockEnabled
                // ロックを解除設定にしたら、その場で開錠状態へ倒す
                if (!settings.isLockEnabled) isLockedInternal.value = false
            }
            .launchIn(scope)
    }

    fun unlock() {
        isLockedInternal.value = false
    }

    /** バックグラウンドへ回ったときに呼ぶ。 */
    fun lock() {
        if (lockEnabled.value == true) isLockedInternal.value = true
    }
}
