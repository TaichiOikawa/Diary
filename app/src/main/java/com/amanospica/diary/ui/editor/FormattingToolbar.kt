package com.amanospica.diary.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.amanospica.diary.R
import com.amanospica.diary.domain.model.SpanType

/**
 * 本文の装飾とメディア挿入をまとめた下部ツールバー。
 * 装飾ボタンは本文にカーソルがあるときだけ有効になる。
 *
 * 写真を複数選ぶと取り込みに時間がかかるため、その間は挿入ボタンを進捗表示に差し替える。
 * 待っている間に押し直して二重に取り込まれるのを防ぐ意味もある。
 */
@Composable
fun FormattingToolbar(
    activeSpanTypes: Set<SpanType>,
    isTextFocused: Boolean,
    isAttachingMedia: Boolean,
    onToggleSpan: (SpanType) -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val attachingLabel = stringResource(R.string.editor_media_attaching)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            // キーボードが出ていればその上、出ていなければナビゲーションバーの上に置く。
            // どちらも画面下端からの余白なので、足し合わせずに大きいほうだけを取る
            // （足すとキーボードとの間にナビゲーションバーの分だけ隙間が空く）。
            //
            // 余白を Surface の外ではなく内側で取っているのは、背景色を画面の下端まで
            // 伸ばしたいため。外で取るとジェスチャーバーの後ろだけ地の色が覗く
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.ime
                    .union(WindowInsets.navigationBars)
                    .only(WindowInsetsSides.Bottom)
            ),
        ) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SpanToggle(SpanType.BOLD, Icons.Filled.FormatBold, R.string.editor_format_bold, activeSpanTypes, isTextFocused, onToggleSpan)
                SpanToggle(SpanType.ITALIC, Icons.Filled.FormatItalic, R.string.editor_format_italic, activeSpanTypes, isTextFocused, onToggleSpan)
                SpanToggle(SpanType.UNDERLINE, Icons.Filled.FormatUnderlined, R.string.editor_format_underline, activeSpanTypes, isTextFocused, onToggleSpan)
                SpanToggle(SpanType.HEADING, Icons.Filled.Title, R.string.editor_format_heading, activeSpanTypes, isTextFocused, onToggleSpan)
                SpanToggle(SpanType.LIST_ITEM, Icons.AutoMirrored.Filled.FormatListBulleted, R.string.editor_format_list, activeSpanTypes, isTextFocused, onToggleSpan)

                VerticalDivider(modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .height(24.dp))

                if (isAttachingMedia) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .semantics {
                                    contentDescription = attachingLabel
                                },
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    IconButton(onClick = onPickImage) {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = stringResource(R.string.editor_insert_image),
                        )
                    }
                }
                IconButton(onClick = onPickVideo, enabled = !isAttachingMedia) {
                    Icon(
                        imageVector = Icons.Filled.Videocam,
                        contentDescription = stringResource(R.string.editor_insert_video),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpanToggle(
    type: SpanType,
    icon: ImageVector,
    @StringRes labelRes: Int,
    activeSpanTypes: Set<SpanType>,
    enabled: Boolean,
    onToggleSpan: (SpanType) -> Unit,
) {
    FilledIconToggleButton(
        checked = type in activeSpanTypes,
        onCheckedChange = { onToggleSpan(type) },
        enabled = enabled,
        modifier = Modifier.width(44.dp),
    ) {
        Icon(imageVector = icon, contentDescription = stringResource(labelRes))
    }
}
