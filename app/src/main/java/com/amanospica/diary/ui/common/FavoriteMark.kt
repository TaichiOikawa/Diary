package com.amanospica.diary.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amanospica.diary.R

/**
 * お気に入り（星）を付けた日記に添える印。
 * タイムラインとカレンダーのシートで共用し、絞り込まずに眺めているときも
 * どれに星を付けたかが分かるようにする。
 *
 * 星そのものが意味を伝えるので、[DraftBadge] のような文字は添えない。
 */
@Composable
fun FavoriteMark(modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Icon(
        imageVector = Icons.Filled.Star,
        contentDescription = stringResource(R.string.diary_favorite),
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(size),
    )
}
