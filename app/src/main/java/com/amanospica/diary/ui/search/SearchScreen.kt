package com.amanospica.diary.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.amanospica.diary.R
import com.amanospica.diary.ui.common.DiaryViewModelFactory
import com.amanospica.diary.ui.common.DraftBadge
import com.amanospica.diary.ui.common.EmptyState
import com.amanospica.diary.ui.common.FavoriteMark
import com.amanospica.diary.ui.common.MediaThumbnail
import java.io.File

/**
 * 検索画面。上部の入力欄に打った言葉で、タイトルと本文から日記を探す。
 *
 * 結果のカードでは一致した箇所を光らせ、本文はそのまわりだけを抜き出して見せる。
 * 一覧を眺めて探すタイムラインに対して、言葉を覚えているときの近道にあたる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateUp: () -> Unit,
    onOpenDiary: (diaryId: String, isDraft: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(factory = DiaryViewModelFactory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    SearchField(
                        query = query,
                        onQueryChange = viewModel::onQueryChange,
                        onClear = viewModel::clearQuery,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                !uiState.hasQuery -> EmptyState(
                    emoji = "🔍",
                    title = stringResource(R.string.search_prompt_title),
                    message = stringResource(R.string.search_prompt_message),
                    modifier = Modifier.fillMaxSize(),
                )

                uiState.isEmpty -> EmptyState(
                    emoji = "🫧",
                    title = stringResource(R.string.search_empty_title),
                    message = stringResource(R.string.search_empty_message),
                    modifier = Modifier.fillMaxSize(),
                )

                else -> SearchResults(
                    results = uiState.results,
                    onOpenDiary = onOpenDiary,
                )
            }
        }
    }
}

/**
 * 上部バーに置く入力欄。画面を開いた時点で文字を打ち始められるようにする。
 *
 * 下線や枠を消して、バーの一部として見えるようにしている。
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.search_clear),
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // 打つそばから結果が出ているので、確定は入力を終える（キーボードを畳む）合図として扱う
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SearchResults(
    results: List<SearchResultUiState>,
    onOpenDiary: (diaryId: String, isDraft: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "count") {
            Text(
                text = stringResource(R.string.search_result_count, results.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            )
        }
        items(items = results, key = { it.id }) { result ->
            SearchResultCard(
                result = result,
                onClick = { onOpenDiary(result.id, result.isDraft) },
            )
        }
    }
}

/**
 * 結果1件分のカード。
 *
 * タイムラインのカードと違い、日付を文字で出して抜粋を主役にする。
 * 「どの日記か」より「どこが引っかかったか」が先に知りたい場面だから。
 */
@Composable
private fun SearchResultCard(
    result: SearchResultUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = result.emoji, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.search_date_format,
                            result.date.year,
                            result.date.monthValue,
                            result.date.dayOfMonth,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (result.isFavorite) {
                        FavoriteMark(size = 14.dp)
                    }
                    if (result.isDraft) {
                        DraftBadge()
                    }
                }
                Text(
                    text = highlighted(
                        text = result.title ?: stringResource(R.string.timeline_untitled),
                        highlights = result.titleHighlights,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (result.title != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (result.snippet.text.isNotEmpty()) {
                    Text(
                        text = highlighted(
                            text = result.snippet.text,
                            highlights = result.snippet.highlights,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            result.thumbnail?.let { thumbnail ->
                Spacer(Modifier.width(12.dp))
                ResultThumbnail(thumbnail)
            }
        }
    }
}

/** 一致した箇所に色を敷いた文字列を作る。 */
@Composable
private fun highlighted(text: String, highlights: List<IntRange>): AnnotatedString {
    if (highlights.isEmpty()) return AnnotatedString(text)

    val style = SpanStyle(
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        background = MaterialTheme.colorScheme.primaryContainer,
        fontWeight = FontWeight.Bold,
    )
    return remember(text, highlights, style) {
        AnnotatedString.Builder(text).apply {
            highlights.forEach { range ->
                // 表示側は終端を含まない位置で扱うので、両端を含む範囲から1つ広げる
                addStyle(style, range.first, (range.last + 1).coerceAtMost(text.length))
            }
        }.toAnnotatedString()
    }
}

@Composable
private fun ResultThumbnail(thumbnail: MediaThumbnail, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = File(thumbnail.absolutePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        if (thumbnail.isVideo) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.photo_video),
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(4.dp),
            )
        }
    }
}
