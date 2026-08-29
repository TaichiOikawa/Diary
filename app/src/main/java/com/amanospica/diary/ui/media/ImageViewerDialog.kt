package com.amanospica.diary.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.amanospica.diary.R
import java.io.File
import kotlin.math.abs

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f

private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * 黒地に画像を1枚だけ大きく置く領域。
 * ピンチイン・アウトとドラッグ移動、ダブルタップでの拡大／等倍戻しに対応する。
 *
 * 閉じるボタンなどの被せ物は持たないので、呼び出し側が [Box] で重ねる。
 * 全画面ダイアログ（[ImageViewerDialog]）とフォトタブの閲覧で共有している。
 */
@Composable
fun ZoomableImage(
    absolutePath: String,
    modifier: Modifier = Modifier,
) {
    var scale by remember(absolutePath) { mutableFloatStateOf(MIN_SCALE) }
    var offset by remember(absolutePath) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectZoomGestures(isZoomed = { scale > MIN_SCALE }) { pan, zoom ->
                    scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    // 等倍まで戻したら位置もリセットして、画像が画面外に取り残されないようにする
                    offset = if (scale == MIN_SCALE) Offset.Zero else offset + pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > MIN_SCALE) {
                            scale = MIN_SCALE
                            offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_SCALE
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = File(absolutePath),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
    }
}

/**
 * 拡大・移動のジェスチャを拾う。標準の `detectTransformGestures` の代わり。
 *
 * 標準版は指の移動が slop を越えた時点で必ずイベントを消費してしまうため、
 * 横スワイプでページを送るページャの中に置くと、ページが一切動かなくなる。
 * ここでは「2本指（＝拡大操作）」か「すでに拡大済み（＝移動操作）」のときだけ消費し、
 * 等倍で1本指のときは何もせず親へ流して、横スワイプをページ送りに使わせる。
 *
 * 裏を返すと拡大中はページを送れない。指1本の動きを移動と送りのどちらに使うかは
 * 二択なので、拡大中は移動を優先している（ダブルタップかピンチで等倍に戻せば送れる）。
 */
private suspend fun PointerInputScope.detectZoomGestures(
    isZoomed: () -> Boolean,
    onGesture: (pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        var accumulatedZoom = 1f
        var accumulatedPan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            // 親や兄弟が先に処理したジェスチャには手を出さない
            val canceled = event.changes.fastAny { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    accumulatedZoom *= zoomChange
                    accumulatedPan += panChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1f - accumulatedZoom) * centroidSize
                    if (zoomMotion > touchSlop || accumulatedPan.getDistance() > touchSlop) {
                        pastTouchSlop = true
                    }
                }

                val isOurs = event.changes.count { it.pressed } > 1 || isZoomed()
                if (pastTouchSlop && isOurs && (zoomChange != 1f || panChange != Offset.Zero)) {
                    onGesture(panChange, zoomChange)
                    event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (!canceled && event.changes.fastAny { it.pressed })
    }
}

/**
 * 画像の拡大表示。右上の×だけを被せた、いちばん素朴な全画面表示。
 */
@Composable
fun ImageViewerDialog(
    absolutePath: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ZoomableImage(
                absolutePath = absolutePath,
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = Color.White,
                )
            }
        }
    }
}
