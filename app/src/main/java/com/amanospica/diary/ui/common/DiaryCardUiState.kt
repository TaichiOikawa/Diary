package com.amanospica.diary.ui.common

import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.DiaryBlock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** カード左のサムネイル。動画は再生アイコンを重ねて表示する。 */
data class MediaThumbnail(
    val absolutePath: String,
    val isVideo: Boolean,
)

/**
 * 日記カード1枚分の表示状態。
 * ファイルパスの解決や時刻整形は ViewModel 側で済ませ、Compose 側は描画だけに専念させる。
 */
data class DiaryCardUiState(
    val id: String,
    val emoji: String,
    val title: String?,
    val preview: String,
    val time: String,
    val thumbnail: MediaThumbnail?,
    val mediaCount: Int,
    /** 書きかけの日記。カードに「下書き」の印を出す。 */
    val isDraft: Boolean = false,
    /** お気に入り（星）を付けた日記。カードに星の印を出す。 */
    val isFavorite: Boolean = false,
)

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * @param resolveMediaPath 相対パス → 絶対パスの解決関数（`ResolveMediaPathUseCase`）。
 */
fun Diary.toCardUiState(resolveMediaPath: (String) -> String): DiaryCardUiState {
    val mediaBlocks = blocks.filter { it is DiaryBlock.ImageBlock || it is DiaryBlock.VideoBlock }
    // 画像があれば画像を優先。動画しかない場合は先頭フレームをサムネイルにする
    val thumbnailBlock = mediaBlocks.firstOrNull { it is DiaryBlock.ImageBlock } ?: mediaBlocks.firstOrNull()

    return DiaryCardUiState(
        id = id,
        emoji = emoji,
        title = title?.takeIf { it.isNotBlank() },
        preview = preview,
        time = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).format(TIME_FORMATTER),
        thumbnail = when (thumbnailBlock) {
            is DiaryBlock.ImageBlock -> MediaThumbnail(resolveMediaPath(thumbnailBlock.localFilePath), isVideo = false)
            is DiaryBlock.VideoBlock -> MediaThumbnail(resolveMediaPath(thumbnailBlock.localFilePath), isVideo = true)
            else -> null
        },
        mediaCount = mediaBlocks.size,
        isDraft = isDraft,
        isFavorite = isFavorite,
    )
}
