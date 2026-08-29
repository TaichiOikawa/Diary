package com.amanospica.diary.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * 画面内で使い回す単一の [ExoPlayer]。
 *
 * ブロックごとにプレイヤーを持つとメモリとデコーダを食い潰すため、
 * 「いま再生しているブロック」だけがこのインスタンスを借りる方式にしている。
 */
@Composable
fun rememberExoPlayer(): ExoPlayer {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

/** 指定ファイルを読み込んで再生を開始する。 */
fun ExoPlayer.playFile(absolutePath: String) {
    setMediaItem(MediaItem.fromUri(File(absolutePath).toURI().toString()))
    prepare()
    playWhenReady = true
}

/**
 * インライン再生用のプレイヤービュー。操作パネル（再生・一時停止・シーク）は Media3 の標準 UI を使う。
 */
@Composable
fun VideoPlayerSurface(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
    useController: Boolean = true,
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                this.useController = useController
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        update = { view -> view.player = player },
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}

/** 全画面再生。インライン再生と同じプレイヤーを引き継ぐので、再生位置は途切れない。 */
@Composable
fun FullScreenVideoDialog(
    player: ExoPlayer,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            VideoPlayerSurface(player = player, modifier = Modifier.fillMaxSize())
        }
    }
}
