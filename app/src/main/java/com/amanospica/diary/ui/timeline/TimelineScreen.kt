package com.amanospica.diary.ui.timeline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amanospica.diary.R
import com.amanospica.diary.ui.common.DiaryViewModelFactory
import com.amanospica.diary.ui.common.EmptyState

/**
 * ホーム（タイムライン）画面。
 * 冒頭に飾りの風景を置き、その下に年ごとの区切りと日記カードを新しい順に並べる。
 *
 * カードを長押しすると選択モードに入り、複数の日記をまとめて削除できる。
 * 選択中の上部バーは [TimelineSelectionTopBar] として切り出してあり、
 * このタブを載せている [com.amanospica.diary.ui.main.MainScreen] が差し替える。
 */
@Composable
fun TimelineScreen(
    onOpenDiary: (diaryId: String, isDraft: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = viewModel(factory = DiaryViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 選択中の「戻る」は画面を閉じずに、まず選択の解除にあてる
    BackHandler(enabled = uiState.isSelectionMode) { viewModel.clearSelection() }

    if (uiState.isDeleteConfirmVisible) {
        DeleteSelectedDialog(
            count = uiState.selectedIds.size,
            onConfirm = viewModel::deleteSelected,
            onDismiss = viewModel::dismissDeleteConfirm,
        )
    }

    if (uiState.isEmpty) {
        // 絞り込んだ結果が空なのか、そもそも日記が無いのかで案内を変える
        if (uiState.isFavoritesOnly) {
            EmptyState(
                emoji = "⭐",
                title = stringResource(R.string.timeline_favorites_empty_title),
                message = stringResource(R.string.timeline_favorites_empty_message),
                modifier = modifier.fillMaxSize(),
            )
        } else {
            EmptyState(
                emoji = "📔",
                title = stringResource(R.string.timeline_empty_title),
                message = stringResource(R.string.timeline_empty_message),
                modifier = modifier.fillMaxSize(),
            )
        }
        return
    }

    TimelineContent(
        uiState = uiState,
        onOpenDiary = onOpenDiary,
        onToggleSelection = viewModel::toggleSelection,
        modifier = modifier,
    )
}

/**
 * 選択モード中に通常のタイトルバーと差し替える上部バー。
 * 左の×で選択をやめ、右のごみ箱でまとめ削除の確認へ進む。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineSelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.timeline_selection_count, selectedCount)) },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.timeline_selection_clear),
                )
            }
        },
        actions = {
            IconButton(onClick = onDeleteSelected) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.timeline_delete_selected),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
private fun TimelineContent(
    uiState: TimelineUiState,
    onOpenDiary: (diaryId: String, isDraft: Boolean) -> Unit,
    onToggleSelection: (diaryId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // 飾りの風景を左右いっぱいに出すため、横の余白はカード側で付ける
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        uiState.sections.forEach { section ->
            item(key = "year-${section.year}") {
                Text(
                    text = section.year.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 20.dp, top = 4.dp),
                )
            }
            items(items = section.entries, key = { it.id }) { entry ->
                TimelineCard(
                    entry = entry,
                    // 選択中はタップで日記を開かず、選ぶ／外すにあてる
                    onClick = {
                        if (uiState.isSelectionMode) {
                            onToggleSelection(entry.id)
                        } else {
                            onOpenDiary(entry.id, entry.isDraft)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    onLongClick = { onToggleSelection(entry.id) },
                    selectionMode = uiState.isSelectionMode,
                    selected = entry.id in uiState.selectedIds,
                )
            }
        }
    }
}

@Composable
private fun DeleteSelectedDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timeline_delete_confirm_title, count)) },
        text = { Text(stringResource(R.string.timeline_delete_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
