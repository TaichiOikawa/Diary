package com.amanospica.diary.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amanospica.diary.R

/**
 * 書きかけ（下書き）の日記に付ける印。
 * タイムラインとカレンダーのシートで共用し、一覧の中から続きを書く日記を見つけられるようにする。
 */
@Composable
fun DraftBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.EditNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.diary_draft),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
