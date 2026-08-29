package com.amanospica.diary.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amanospica.diary.R
import com.amanospica.diary.domain.model.DiaryEmoji

/** プリセット絵文字から1つ選ぶシート。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(
    selectedEmoji: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.editor_emoji_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.heightIn(max = 360.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = DiaryEmoji.PRESETS, key = { it.emoji }) { preset ->
                    val isSelected = preset.emoji == selectedEmoji
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            )
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { onSelect(preset.emoji) }
                            .semantics { contentDescription = preset.label },
                    ) {
                        Text(text = preset.emoji, fontSize = 26.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
