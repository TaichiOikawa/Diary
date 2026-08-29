package com.amanospica.diary.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.amanospica.diary.R
import com.amanospica.diary.ui.theme.DiaryTheme
import java.io.File

/**
 * タイムライン・カレンダーシートで共用する日記カード。
 * 絵文字・タイトル・本文プレビュー・メディアサムネイルを1枚にまとめる。
 */
@Composable
fun DiaryCard(
    state: DiaryCardUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmojiBadge(state.emoji)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title ?: stringResource(R.string.timeline_untitled),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.title != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.preview.isNotBlank()) {
                    Text(
                        text = state.preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.size(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.isFavorite) {
                        FavoriteMark(size = 16.dp)
                    }
                    if (state.isDraft) {
                        DraftBadge()
                    }
                    Text(
                        text = state.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (state.mediaCount > 0) {
                        Text(
                            text = stringResource(R.string.timeline_media_count, state.mediaCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            state.thumbnail?.let { thumbnail ->
                Spacer(Modifier.width(12.dp))
                Thumbnail(thumbnail)
            }
        }
    }
}

@Composable
private fun EmojiBadge(emoji: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = 22.sp)
    }
}

@Composable
private fun Thumbnail(thumbnail: MediaThumbnail, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = File(thumbnail.absolutePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp),
        )
        if (thumbnail.isVideo) {
            Icon(
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
private fun DiaryCardPreview() {
    DiaryTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DiaryCard(
                state = DiaryCardUiState(
                    id = "1",
                    emoji = "😊",
                    title = "海までドライブ",
                    preview = "朝から天気が良かったので、思い立って海まで走ってきた。潮風が気持ちいい。",
                    time = "10:24",
                    thumbnail = null,
                    mediaCount = 2,
                    isFavorite = true,
                ),
                onClick = {},
            )
            DiaryCard(
                state = DiaryCardUiState(
                    id = "2",
                    emoji = "😴",
                    title = null,
                    preview = "今日はとにかく眠かった。",
                    time = "23:58",
                    thumbnail = null,
                    mediaCount = 0,
                    isDraft = true,
                ),
                onClick = {},
            )
        }
    }
}
