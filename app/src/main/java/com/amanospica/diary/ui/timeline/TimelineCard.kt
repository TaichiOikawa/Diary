package com.amanospica.diary.ui.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.amanospica.diary.R
import com.amanospica.diary.ui.common.DraftBadge
import com.amanospica.diary.ui.common.FavoriteMark
import com.amanospica.diary.ui.common.MediaThumbnail
import com.amanospica.diary.ui.theme.DiaryTheme
import java.io.File

/**
 * タイムラインのカード。
 *
 * 日付を大きく左肩に置き、その下に短いアンダーラインを敷いて視線の起点にする。
 * 気分の絵文字は日付と同じ行の右端に置き、本文はカード幅いっぱいを使って
 * タイトル→プレビューの順に流す。
 *
 * 長押し（[onLongClick]）でまとめ削除のための選択が始まる。選択中（[selectionMode]）は
 * 日付の行の右端にチェックボックスが出て、選ばれたカードは枠と地色で示す。
 */
@Composable
fun TimelineCard(
    entry: TimelineEntryUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
) {
    val shape = RoundedCornerShape(20.dp)
    Card(
        // 長押しも取るため Card 自身のクリックは使わず、波紋が角丸に収まるよう先に切り抜く
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        // 絵文字を日付の行へ入れたので、本文はカード幅いっぱいを使える
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            DateHeadline(
                day = entry.day,
                month = entry.month,
                emoji = entry.emoji,
                isDraft = entry.isDraft,
                isFavorite = entry.isFavorite,
                selectionMode = selectionMode,
                selected = selected,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = entry.title ?: entry.preview.ifBlank {
                    stringResource(R.string.timeline_untitled)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            // タイトルを本文代わりに使った場合はプレビューを重ねて出さない
            if (entry.title != null && entry.preview.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = entry.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // プレビューは本文の下へ回し、本文の幅を削らないようにする
            if (entry.thumbnails.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                MediaStrip(thumbnails = entry.thumbnails, totalCount = entry.mediaCount)
            }
        }
    }
}

@Composable
private fun DateHeadline(
    day: String,
    month: String,
    emoji: String,
    isDraft: Boolean,
    isFavorite: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = month,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 5.dp, end = 5.dp),
            )
            Text(
                text = day,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            // 星を付けた日記は、日付の行の右端に印を出す
            if (isFavorite) {
                FavoriteMark(modifier = Modifier.padding(start = 8.dp, bottom = 5.dp), size = 20.dp)
            }
            // 書きかけの日記は、日付の行の右端で「下書き」と示す
            if (isDraft) {
                DraftBadge(modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
            }
            Text(
                text = emoji,
                fontSize = 26.sp,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
            )
            // 選択の切り替えはカード全体で受けるので、ここは印を出すだけ
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
            }
        }
        // 日付の下に敷くアクセント。カード内の視線の起点になる
        Box(
            modifier = Modifier
                .width(66.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        )
    }
}

/**
 * カード下部に並べる写真・動画のプレビュー。
 *
 * 常に4分割の枠で描き、枚数が足りないところは空けておく。
 * こうすると枚数が違うカードが並んでもサムネイルの大きさが揃う。
 * 4枚に収まらない分は最後の1枚に「+n」を重ねて示す。
 */
@Composable
private fun MediaStrip(
    thumbnails: List<MediaThumbnail>,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val shown = thumbnails.take(MAX_TIMELINE_THUMBNAILS)
    val overflow = totalCount - shown.size

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shown.forEachIndexed { index, thumbnail ->
            MediaTile(
                thumbnail = thumbnail,
                overflowCount = if (index == shown.lastIndex) overflow else 0,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
            )
        }
        repeat(MAX_TIMELINE_THUMBNAILS - shown.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MediaTile(
    thumbnail: MediaThumbnail,
    overflowCount: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = File(thumbnail.absolutePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        when {
            overflowCount > 0 -> Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$overflowCount",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }

            thumbnail.isVideo -> Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(4.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimelineCardPreview() {
    DiaryTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TimelineCard(
                entry = TimelineEntryUiState(
                    id = "1",
                    day = "01",
                    month = "8月",
                    emoji = "😀",
                    title = "サンプル日記1",
                    preview = "",
                    time = "09:15",
                    thumbnails = emptyList(),
                    mediaCount = 0,
                    isDraft = true,
                ),
                onClick = {},
            )
            TimelineCard(
                entry = TimelineEntryUiState(
                    id = "2",
                    day = "30",
                    month = "7月",
                    emoji = "😊",
                    title = "サンプル日記2",
                    preview = "本文プレビュー",
                    time = "18:20",
                    // プレビュー描画では実ファイルが無いため枠だけが出る
                    thumbnails = List(MAX_TIMELINE_THUMBNAILS) {
                        MediaThumbnail(absolutePath = "", isVideo = it == 1)
                    },
                    mediaCount = 7,
                    isFavorite = true,
                ),
                onClick = {},
                selectionMode = true,
                selected = true,
            )
        }
    }
}
