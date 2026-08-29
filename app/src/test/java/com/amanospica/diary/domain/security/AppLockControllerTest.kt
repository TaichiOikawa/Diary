package com.amanospica.diary.domain.security

import com.amanospica.diary.domain.model.AppSettings
import com.amanospica.diary.domain.model.TextSpacing
import com.amanospica.diary.domain.model.ThemeMode
import com.amanospica.diary.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ロック画面を出すかどうかの判定を確かめる。
 *
 * 設定は DataStore から非同期で届くため、「まだ読めていない」状態で
 * 日記の中身を出してしまわないことがここでの肝。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppLockControllerTest {

    private val settings = MutableSharedFlow<AppSettings>(replay = 1)

    @Test
    fun `設定を読み終えるまでは判定を保留する`() = runTest {
        val controller = createController()

        assertEquals(LockState.UNKNOWN, controller.lockState.value)
    }

    @Test
    fun `ロック設定が有効なら、起動直後は施錠されている`() = runTest {
        val controller = createController()

        emitSettings(isLockEnabled = true)

        assertEquals(LockState.LOCKED, controller.lockState.value)
    }

    @Test
    fun `ロック設定が無効なら解錠状態になる`() = runTest {
        val controller = createController()

        emitSettings(isLockEnabled = false)

        assertEquals(LockState.UNLOCKED, controller.lockState.value)
    }

    @Test
    fun `解錠すると中身が見えるようになる`() = runTest {
        val controller = createController()
        emitSettings(isLockEnabled = true)

        controller.unlock()
        runCurrent()

        assertEquals(LockState.UNLOCKED, controller.lockState.value)
    }

    @Test
    fun `バックグラウンドへ回ると施錠し直す`() = runTest {
        val controller = createController()
        emitSettings(isLockEnabled = true)
        controller.unlock()
        runCurrent()

        controller.lock()
        runCurrent()

        assertEquals(LockState.LOCKED, controller.lockState.value)
    }

    @Test
    fun `ロック設定が無効なら、バックグラウンドへ回っても施錠しない`() = runTest {
        val controller = createController()
        emitSettings(isLockEnabled = false)

        controller.lock()
        runCurrent()

        assertEquals(LockState.UNLOCKED, controller.lockState.value)
    }

    @Test
    fun `設定を読む前にバックグラウンドへ回っても、判定は保留のまま`() = runTest {
        val controller = createController()

        controller.lock()
        runCurrent()

        assertEquals(LockState.UNKNOWN, controller.lockState.value)
    }

    private fun TestScope.createController() =
        AppLockController(FakeSettingsRepository(settings), backgroundScope)
            .also { runCurrent() }

    private fun TestScope.emitSettings(isLockEnabled: Boolean) {
        settings.tryEmit(AppSettings(isLockEnabled = isLockEnabled, hasPin = isLockEnabled))
        runCurrent()
    }
}

private class FakeSettingsRepository(
    override val settings: Flow<AppSettings>,
) : SettingsRepository {
    override suspend fun currentSettings(): AppSettings = AppSettings()
    override suspend fun setPin(pin: String): Boolean = false
    override suspend fun verifyPin(pin: String): Boolean = false
    override suspend fun clearPin() = Unit
    override suspend fun setLockEnabled(enabled: Boolean) = Unit
    override suspend fun setBiometricEnabled(enabled: Boolean) = Unit
    override suspend fun setThemeMode(mode: ThemeMode) = Unit
    override suspend fun setTextSpacing(spacing: TextSpacing) = Unit
    override suspend fun setAutoUpdateCheckEnabled(enabled: Boolean) = Unit
    override suspend fun setLastUpdateCheckAt(epochMillis: Long) = Unit
    override suspend fun setSkippedUpdateVersion(tagName: String?) = Unit
}
