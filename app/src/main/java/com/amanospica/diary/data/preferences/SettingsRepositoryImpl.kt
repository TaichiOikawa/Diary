package com.amanospica.diary.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.amanospica.diary.domain.model.AppSettings
import com.amanospica.diary.domain.model.PinCredential
import com.amanospica.diary.domain.model.ReminderCondition
import com.amanospica.diary.domain.model.TextSpacing
import com.amanospica.diary.domain.model.ThemeMode
import com.amanospica.diary.domain.repository.SettingsRepository
import com.amanospica.diary.domain.security.PinHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 設定を DataStore に保存する実装。
 *
 * PIN はハッシュとソルトだけを持つ。アプリ専用ディレクトリなので他アプリからは読めず、
 * 万一吸い出されても PBKDF2 の反復回数が総当たりの壁になる。
 */
class SettingsRepositoryImpl(context: Context) : SettingsRepository {

    private val dataStore = context.applicationContext.settingsDataStore

    override val settings: Flow<AppSettings> = dataStore.data.map { it.toAppSettings() }

    override suspend fun currentSettings(): AppSettings = settings.first()

    override suspend fun setPin(pin: String): Boolean {
        if (!PinHasher.isValidFormat(pin)) return false
        val credential = PinHasher.create(pin)
        dataStore.edit { preferences ->
            preferences[KEY_PIN_HASH] = credential.hash
            preferences[KEY_PIN_SALT] = credential.salt
            // 桁数はハッシュから復元できないので別に控える（ロック画面の丸の数に使う）
            preferences[KEY_PIN_LENGTH] = pin.length
            // PIN を設定した時点でロックを有効にしないと、設定した意味が伝わらない
            preferences[KEY_LOCK_ENABLED] = true
        }
        return true
    }

    override suspend fun verifyPin(pin: String): Boolean {
        val preferences = dataStore.data.first()
        val credential = preferences.toPinCredential() ?: return false
        return PinHasher.verify(pin, credential)
    }

    override suspend fun clearPin() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_PIN_HASH)
            preferences.remove(KEY_PIN_SALT)
            preferences.remove(KEY_PIN_LENGTH)
            preferences[KEY_LOCK_ENABLED] = false
            preferences[KEY_BIOMETRIC_ENABLED] = false
        }
    }

    override suspend fun setLockEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            // PIN 未設定でロックだけ有効にすると解除できなくなるため無視する
            if (enabled && preferences.toPinCredential() == null) return@edit
            preferences[KEY_LOCK_ENABLED] = enabled
            if (!enabled) preferences[KEY_BIOMETRIC_ENABLED] = false
        }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            if (enabled && preferences.toPinCredential() == null) return@edit
            preferences[KEY_BIOMETRIC_ENABLED] = enabled
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences -> preferences[KEY_THEME_MODE] = mode.name }
    }

    override suspend fun setTextSpacing(spacing: TextSpacing) {
        dataStore.edit { preferences -> preferences[KEY_TEXT_SPACING] = spacing.name }
    }

    override suspend fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_AUTO_UPDATE_CHECK] = enabled }
    }

    override suspend fun setLastUpdateCheckAt(epochMillis: Long) {
        dataStore.edit { preferences -> preferences[KEY_LAST_UPDATE_CHECK_AT] = epochMillis }
    }

    override suspend fun setSkippedUpdateVersion(tagName: String?) {
        dataStore.edit { preferences ->
            if (tagName == null) {
                preferences.remove(KEY_SKIPPED_UPDATE_VERSION)
            } else {
                preferences[KEY_SKIPPED_UPDATE_VERSION] = tagName
            }
        }
    }

    override suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_REMINDER_ENABLED] = enabled }
    }

    override suspend fun setReminderTime(time: LocalTime) {
        // 時刻は「0時からの分数」で持つ。タイムゾーンをまたいでも意味が変わらず、
        // 日をまたぐ計算（次に通知する日時）も分の足し算だけで済む。
        dataStore.edit { preferences ->
            preferences[KEY_REMINDER_MINUTE_OF_DAY] = time.hour * 60 + time.minute
        }
    }

    override suspend fun setReminderCondition(condition: ReminderCondition) {
        dataStore.edit { preferences -> preferences[KEY_REMINDER_CONDITION] = condition.name }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val hasPin = toPinCredential() != null
        return AppSettings(
            isLockEnabled = hasPin && (this[KEY_LOCK_ENABLED] ?: false),
            isBiometricEnabled = hasPin && (this[KEY_BIOMETRIC_ENABLED] ?: false),
            hasPin = hasPin,
            pinLength = if (hasPin) this[KEY_PIN_LENGTH] ?: 0 else 0,
            themeMode = this[KEY_THEME_MODE]
                ?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            textSpacing = this[KEY_TEXT_SPACING]
                ?.let { name -> runCatching { TextSpacing.valueOf(name) }.getOrNull() }
                ?: TextSpacing.MEDIUM,
            isAutoUpdateCheckEnabled = this[KEY_AUTO_UPDATE_CHECK] ?: true,
            lastUpdateCheckAt = this[KEY_LAST_UPDATE_CHECK_AT] ?: 0L,
            skippedUpdateVersion = this[KEY_SKIPPED_UPDATE_VERSION],
            isReminderEnabled = this[KEY_REMINDER_ENABLED] ?: false,
            reminderTime = this[KEY_REMINDER_MINUTE_OF_DAY]
                ?.takeIf { it in 0 until MINUTES_PER_DAY }
                ?.let { LocalTime.of(it / 60, it % 60) }
                ?: AppSettings.DEFAULT_REMINDER_TIME,
            reminderCondition = this[KEY_REMINDER_CONDITION]
                ?.let { name -> runCatching { ReminderCondition.valueOf(name) }.getOrNull() }
                ?: ReminderCondition.WHEN_UNWRITTEN,
        )
    }

    private fun Preferences.toPinCredential(): PinCredential? {
        val hash = this[KEY_PIN_HASH] ?: return null
        val salt = this[KEY_PIN_SALT] ?: return null
        return PinCredential(hash = hash, salt = salt)
    }

    private companion object {
        val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
        val KEY_PIN_LENGTH = intPreferencesKey("pin_length")
        val KEY_LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_TEXT_SPACING = stringPreferencesKey("text_spacing")
        val KEY_AUTO_UPDATE_CHECK = booleanPreferencesKey("auto_update_check")
        val KEY_LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
        val KEY_SKIPPED_UPDATE_VERSION = stringPreferencesKey("skipped_update_version")
        val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_REMINDER_MINUTE_OF_DAY = intPreferencesKey("reminder_minute_of_day")
        val KEY_REMINDER_CONDITION = stringPreferencesKey("reminder_condition")

        const val MINUTES_PER_DAY = 24 * 60
    }
}
