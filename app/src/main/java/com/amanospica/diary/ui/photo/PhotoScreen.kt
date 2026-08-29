package com.amanospica.diary.ui.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.amanospica.diary.R
import com.amanospica.diary.ui.common.DiaryViewModelFactory
import com.amanospica.diary.ui.common.EmptyState
import com.amanospica.diary.ui.media.VideoPlayerSurface
import com.amanospica.diary.ui.media.ZoomableImage
import com.amanospica.diary.ui.media.playFile
import com.amanospica.diary.ui.media.rememberExoPlayer
import java.io.File
import java.time.LocalDate

private const val PHOTO_GRID_COLUMNS = 3

/**
 * フォトタブ。日記に貼り付けた写真・動画を、日記をまたいで新しい順に並べる。
 *
 * マス目を押すと全画面で開き、そこから貼り付け元の日記へも移れる。
 * 「あの写真いつだっけ」から日記へ辿る道を用意するのが、このタブの主な役目。
 */
@Composable
fun PhotoScreen(
    onOpenDiary: (diaryId: String, isDraft: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhotoViewModel = viewModel(factory = DiaryViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 全画面はスワイプで隣へ移れるので、月の区切りを畳んだ一本の並びも要る
    val allItems = remember(uiState.sections) { uiState.sections.flatMap { it.items } }

    // 全画面で開いた1枚。ここが起点になるだけで、以降どこまで送ったかは全画面側が持つ
    var openedItemId by rememberSaveable { mutableStateOf<String?>(null) }

    if (uiState.isEmpty) {
        EmptyState(
            emoji = "🖼️",
            title = stringResource(R.string.photo_empty_title),
            message = stringResource(R.string.photo_empty_message),
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    PhotoGrid(
        sections = uiState.sections,
        onOpenItem = { openedItemId = it.id },
        modifier = modifier,
    )

    // 開いている間に元の日記が消えたら、見せる相手がいないので全画面も閉じる
    val openedIndex = allItems.indexOfFirst { it.id == openedItemId }
    if (openedIndex >= 0) {
        PhotoViewerDialog(
            items = allItems,
            initialIndex = openedIndex,
            onDismiss = { openedItemId = null },
            // 日記へ移るときは全画面を畳んでおく。戻ってきたときに写真が被っていると邪魔になる
            onOpenDiary = { item ->
                openedItemId = null
                onOpenDiary(item.diaryId, item.isDraft)
            },
        )
    }
}

@Composable
private fun PhotoGrid(
    sections: List<PhotoMonthSection>,
    onOpenItem: (PhotoItemUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(PHOTO_GRID_COLUMNS),
        modifier = modifier.fillMaxSize(),
        // 下端は FAB に隠れないだけの余白を空けておく
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sections.forEach { section ->
            item(
                key = "month-${section.key}",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Text(
                    text = stringResource(R.string.photo_month_format, section.year, section.month),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
                )
            }
            items(items = section.items, key = { it.id }) { item ->
                PhotoTile(
                    item = item,
                    onClick = { onOpenItem(item) },
                    modifier = Modifier.aspectRatio(1f),
                )
            }
        }
    }
}

/** グリッドのマス目1つ。動画は先頭フレームに再生アイコンを重ねて見分けられるようにする。 */
@Composable
private fun PhotoTile(
    item: PhotoItemUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = File(item.absolutePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        if (item.isVideo) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.photo_video),
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(4.dp),
            )
        }
    }
}

/**
 * 一覧から開く全画面表示。横スワイプで隣の写真・動画へ送れる。
 *
 * 一覧では「いつの写真か」が分からないままなので、上部に日付を出し、
 * そのまま貼り付け元の日記へ移れるようにしている。送るたびに日付は差し替わるので、
 * 上部バーは常に「いま見ているもの」を指す。
 */
@Composable
private fun PhotoViewerDialog(
    items: List<PhotoItemUiState>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onOpenDiary: (PhotoItemUiState) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // 開いた1枚を起点に、以降どこまで送ったかはページャが持つ
        val pagerState = rememberPagerState(initialPage = initialIndex) { items.size }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { items[it].id },
                // 送っている最中に隣が真っ黒のまま出てこないよう、1枚先まで用意しておく
                beyondViewportPageCount = 1,
                // 送っている最中に隣の写真と地続きに見えないよう、間を空ける
                pageSpacing = 16.dp,
            ) { page ->
                val item = items[page]
                when {
                    !item.isVideo -> ZoomableImage(
                        absolutePath = item.absolutePath,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // 動画は止まったページの分だけ再生する。先読みの分まで
                    // プレイヤーを持たせると、見えない動画がデコーダを掴んでしまう
                    pagerState.settledPage == page -> VideoContent(
                        absolutePath = item.absolutePath,
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> VideoFirstFrame(
                        absolutePath = item.absolutePath,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // ページャの外に置いて、送っても位置が動かないようにする
            items.getOrNull(pagerState.currentPage)?.let { current ->
                PhotoViewerTopBar(
                    date = current.date,
                    onDismiss = onDismiss,
                    onOpenDiary = { onOpenDiary(current) },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

/** 全画面表示に被せる上部バー。写真が明るくても読めるよう、黒の薄膜を敷く。 */
@Composable
private fun PhotoViewerTopBar(
    date: LocalDate,
    onDismiss: () -> Unit,
    onOpenDiary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = Color.White,
            )
        }
        Text(
            text = stringResource(
                R.string.photo_date_format,
                date.year,
                date.monthValue,
                date.dayOfMonth,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenDiary) {
            Text(
                text = stringResource(R.string.photo_open_diary),
                color = Color.White,
            )
        }
    }
}

/**
 * 送っている最中の動画ページに出す静止画。
 * 一覧のマス目と同じく先頭フレームなので、送りながらでも何の動画か分かる。
 */
@Composable
private fun VideoFirstFrame(
    absolutePath: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = File(absolutePath),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = stringResource(R.string.photo_video),
            tint = Color.White,
            modifier = Modifier
                .size(56.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                .padding(10.dp),
        )
    }
}

/**
 * 全画面での動画再生。開いた時点で再生を始め、閉じるとプレイヤーごと片付く。
 *
 * 一覧から開く動画は1本ずつなので、ここで専用のプレイヤーを持たせている
 * （エディタ・閲覧画面のように、画面で1つを貸し借りする必要がない）。
 */
@Composable
private fun VideoContent(
    absolutePath: String,
    modifier: Modifier = Modifier,
) {
    val player = rememberExoPlayer()
    LaunchedEffect(absolutePath) { player.playFile(absolutePath) }
    VideoPlayerSurface(player = player, modifier = modifier)
}
