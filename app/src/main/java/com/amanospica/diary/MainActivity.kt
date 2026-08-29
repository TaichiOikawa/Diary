package com.amanospica.diary

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amanospica.diary.domain.model.AppSettings
import com.amanospica.diary.domain.model.ThemeMode
import com.amanospica.diary.domain.security.LockState
import com.amanospica.diary.ui.editor.LocalTextSpacing
import com.amanospica.diary.ui.lock.LockScreen
import com.amanospica.diary.ui.navigation.DiaryNavHost
import com.amanospica.diary.ui.theme.DiaryTheme
import com.amanospica.diary.ui.update.AppUpdateDialog

/**
 * BiometricPrompt が FragmentActivity を要求するため [FragmentActivity] を継承する
 * （Compose の setContent は ComponentActivity 系ならそのまま使える）。
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val container = (application as DiaryApplication).container

        // 更新の確認は結果が返り次第ダイアログに出る。ここでは待たない
        container.updateManager.checkOnStartup()

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            val lockState by container.appLockController.lockState.collectAsStateWithLifecycle()

            DiaryTheme(darkTheme = settings.themeMode.isDark()) {
                CompositionLocalProvider(LocalTextSpacing provides settings.textSpacing) {
                    // ロック判定を待つ間に何も出ない時間があるため、
                    // テーマの背景色で塗っておく（素の白が一瞬覗くのを防ぐ）
                    Surface(modifier = Modifier.fillMaxSize()) {
                        // ロック画面は本体と差し替えず上に重ねる。
                        // 差し替えると画面の状態（開いていた日記や入力欄）ごと作り直しになり、
                        // 解錠後にホームへ戻されてしまうため
                        Box(modifier = Modifier.fillMaxSize()) {
                            // ロックの要否が決まるまでは、施錠されている前提で中身を出さない
                            if (lockState != LockState.UNKNOWN) DiaryNavHost()
                            if (lockState == LockState.LOCKED) LockScreen()
                            // 施錠中に更新を勧めても操作できないので、開いているときだけ出す
                            if (lockState == LockState.UNLOCKED) AppUpdateDialog()
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
