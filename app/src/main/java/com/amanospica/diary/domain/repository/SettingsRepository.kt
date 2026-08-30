package com.amanospica.diary.domain.repository

import com.amanospica.diary.domain.model.AppSettings
import com.amanospica.diary.domain.model.ReminderCondition
import com.amanospica.diary.domain.model.TextSpacing
import com.amanospica.diary.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

/** アプリ設定（ロック・外観）の読み書き契約。 */
interface SettingsRepository {

    val settings: Flow<AppSettings>

    suspend fun currentSettings(): AppSettings

    /**
     * PIN を設定する。形式が不正なら false を返し、何も保存しない。
     * 保存されるのはハッシュとソルトのみ。
     */
    suspend fun setPin(pin: String): Boolean

    suspend fun verifyPin(pin: String): Boolean

    /** PIN を削除し、ロックと生体認証も無効化する。 */
    suspend fun clearPin()

    suspend fun setLockEnabled(enabled: Boolean)

    suspend fun setBiometricEnabled(enabled: Boolean)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setTextSpacing(spacing: TextSpacing)

    suspend fun setAutoUpdateCheckEnabled(enabled: Boolean)

    suspend fun setLastUpdateCheckAt(epochMillis: Long)

    /** null を渡すとスキップ指定を取り消す。 */
    suspend fun setSkippedUpdateVersion(tagName: String?)

    suspend fun setReminderEnabled(enabled: Boolean)

    /** 秒以下は通知の精度に対して細かすぎるので、実装側で分単位に丸める。 */
    suspend fun setReminderTime(time: LocalTime)

    suspend fun setReminderCondition(condition: ReminderCondition)
}
