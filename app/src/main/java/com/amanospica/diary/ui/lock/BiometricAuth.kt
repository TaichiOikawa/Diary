package com.amanospica.diary.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** 端末で指紋・顔認証が使える状態かどうか。 */
fun Context.canAuthenticateWithBiometrics(): Boolean =
    BiometricManager.from(this).canAuthenticate(ALLOWED_AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS

private const val ALLOWED_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

/**
 * 生体認証ダイアログを出す。
 * 端末の PIN/パターンへのフォールバックは付けず、アプリ独自の PIN 入力へ戻す
 * （端末ロック解除と日記のロックを別物にしておきたいため）。
 */
fun FragmentActivity.showBiometricPrompt(
    title: String,
    subtitle: String,
    negativeButtonText: String,
    onSuccess: () -> Unit,
) {
    val prompt = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()
    )
}
