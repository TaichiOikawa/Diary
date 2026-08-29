package com.amanospica.diary.ui.editor.block

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.amanospica.diary.R
import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.ui.media.VideoPlayerSurface
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/** メディア同士のすき間。 */
private val MEDIA_SPACING = 4.dp

/**
 * 続けて並んだ画像・動画を、1行に複数枚ずつ敷き詰めて表示する。
 *
 * 1枚を横いっぱいに出すと写真ばかりの日記が縦に伸びてしまうため、
 * 枚数に応じて列数を変え、1枚あたりを小さくして本文の流れを保つ。
 *
 * @param onDelete 削除の合図。null なら右上のごみ箱を出さない（閲覧画面のように消させない場面）。
 */
@Composable
fun MediaBlockGrid(
    blocks: List<DiaryBlock>,
    resolvePath: (String) -> String,
    playingBlockId: String?,
    player: ExoPlayer,
    onOpenImage: (String) -> Unit,
    onPlayVideo: (DiaryBlock.VideoBlock) -> Unit,
    onFullScreen: () -> Unit,
    onDelete: ((DiaryBlock) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val columns = mediaColumns(blocks.size)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MEDIA_SPACING),
    ) {
        blocks.chunked(columns).forEach { rowBlocks ->
            Row(horizontalArrangement = Arrangement.spacedBy(MEDIA_SPACING)) {
                rowBlocks.forEach { block ->
                    when (block) {
                        is DiaryBlock.ImageBlock -> ImageBlockView(
                            absolutePath = resolvePath(block.localFilePath),
                            caption = block.caption,
                            onOpenViewer = { onOpenImage(resolvePath(block.localFilePath)) },
                            onDelete = onDelete?.let { delete -> { delete(block) } },
                            modifier = Modifier.weight(1f),
                        )

                        is DiaryBlock.VideoBlock -> VideoBlockView(
                            absolutePath = resolvePath(block.localFilePath),
                            caption = block.caption,
                            isPlaying = playingBlockId == block.id,
                            player = player,
                            onPlay = { onPlayVideo(block) },
                            onFullScreen = onFullScreen,
                            onDelete = onDelete?.let { delete -> { delete(block) } },
                            modifier = Modifier.weight(1f),
                        )

                        is DiaryBlock.TextBlock -> Unit
                    }
                }
                // 端数の枠は空けておく。埋めないと最後の1枚だけ横いっぱいに伸びてしまう
                repeat(columns - rowBlocks.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** 1行に並べる枚数。増えるほど1枚を小さくして、縦に伸びすぎないようにする。 */
private fun mediaColumns(count: Int): Int = if (count >= 5) 3 else 2

/** 本文中に置かれた画像ブロック。タップで拡大表示する。 */
@Composable
private fun ImageBlockView(
    absolutePath: String,
    caption: String?,
    onOpenViewer: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    MediaBlockFrame(onDelete = onDelete, modifier = modifier) {
        AsyncImage(
            model = File(absolutePath),
            contentDescription = caption,
            // 枠が正方形なので、縦横どちらの写真も切り取って敷き詰める（全体は拡大表示で見る）
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onOpenViewer),
        )
    }
}

/**
 * 本文中に置かれた動画ブロック。
 * 再生前はサムネイル（Coil が先頭フレームを取り出す）、再生中はその場でプレイヤーに切り替わる。
 */
@Composable
private fun VideoBlockView(
    absolutePath: String,
    caption: String?,
    isPlaying: Boolean,
    player: ExoPlayer,
    onPlay: () -> Unit,
    onFullScreen: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    MediaBlockFrame(onDelete = onDelete, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (isPlaying) {
                VideoPlayerSurface(player = player, modifier = Modifier.fillMaxSize())
                // 削除ボタンが右上に重なるので、全画面ボタンは左上へ逃がす
                IconButton(
                    onClick = onFullScreen,
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fullscreen,
                        contentDescription = stringResource(R.string.editor_video_fullscreen),
                        tint = Color.White,
                    )
                }
            } else {
                AsyncImage(
                    model = File(absolutePath),
                    contentDescription = caption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(onClick = onPlay) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.editor_video_play),
                        tint = Color.White,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

/**
 * 画像・動画で共通の枠（正方形の升目と、右上に重ねる削除ボタン）。
 * [onDelete] が null のときは削除ボタンを置かない。
 */
@Composable
private fun MediaBlockFrame(
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.editor_delete_block),
                    tint = Color.White,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .padding(6.dp),
                )
            }
        }
    }
}
