package com.amanospica.diary.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amanospica.diary.R
import com.amanospica.diary.ui.common.DiaryViewModelFactory

/**
 * 起動時・復帰時に出すロック画面。
 * 生体認証が有効なら自動でプロンプトを出し、失敗・キャンセル時は PIN 入力へ落とす。
 */
@Composable
fun LockScreen(
    modifier: Modifier = Modifier,
    viewModel: LockViewModel = viewModel(factory = DiaryViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val biometricTitle = stringResource(R.string.lock_biometric_title)
    val biometricSubtitle = stringResource(R.string.lock_biometric_subtitle)
    val biometricNegative = stringResource(R.string.lock_use_pin)

    // ロック中の戻る操作が、裏で開いたままの画面へ届かないようにする。
    // アプリを閉じずに離れるだけにして、書きかけの日記と開いていた画面を残す
    BackHandler { activity?.moveTaskToBack(true) }

    // 日記の入力中に施錠されると、キーボードがロック画面の上に残ることがある。
    // 変換候補に本文が覗くのを避けるため、入力欄のフォーカスごと畳む
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    fun requestBiometric() {
        if (activity != null && context.canAuthenticateWithBiometrics()) {
            activity.showBiometricPrompt(
                title = biometricTitle,
                subtitle = biometricSubtitle,
                negativeButtonText = biometricNegative,
                onSuccess = viewModel::onBiometricSucceeded,
            )
        }
    }

    LaunchedEffect(uiState.isBiometricEnabled) {
        if (uiState.isBiometricEnabled) requestBiometric()
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "🔒", fontSize = 44.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (uiState.hasError) {
                    stringResource(R.string.lock_error)
                } else {
                    stringResource(R.string.lock_message)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.hasError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))
            PinIndicator(
                filledCount = uiState.pin.length,
                totalCount = uiState.indicatorCount,
            )
            Spacer(Modifier.height(32.dp))

            PinPad(
                onDigit = viewModel::appendDigit,
                onDelete = viewModel::deleteDigit,
                onBiometric = if (uiState.isBiometricEnabled) ::requestBiometric else null,
            )
        }
    }
}

/**
 * PIN の入力状況を示す丸。
 * 丸の数は登録されている PIN の桁数に合わせる（[totalCount]）。
 */
@Composable
private fun PinIndicator(
    filledCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(totalCount) { index ->
            val filled = index < filledCount
            val scale by animateFloatAsState(
                targetValue = if (filled) 1f else 0.6f,
                label = "pin-dot",
            )
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        if (filled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    )
            )
        }
    }
}

@Composable
private fun PinPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onBiometric: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(0.8f),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { digit ->
                    PinKey(
                        modifier = Modifier.weight(1f),
                        onClick = { onDigit(digit) },
                    ) {
                        Text(text = digit.toString(), style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBiometric != null) {
                PinKey(modifier = Modifier.weight(1f), onClick = onBiometric) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = stringResource(R.string.lock_biometric_title),
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            PinKey(modifier = Modifier.weight(1f), onClick = { onDigit('0') }) {
                Text(text = "0", style = MaterialTheme.typography.headlineSmall)
            }
            PinKey(modifier = Modifier.weight(1f), onClick = onDelete) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.lock_delete),
                )
            }
        }
    }
}

@Composable
private fun PinKey(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
    ) {
        content()
    }
}
