package com.amanospica.diary.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import com.amanospica.diary.R
import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.ui.common.DiaryBodyRow
import com.amanospica.diary.ui.common.DiaryViewModelFactory
import com.amanospica.diary.ui.common.DraftBadge
import com.amanospica.diary.ui.common.toBodyRows
import com.amanospica.diary.ui.common.toEntryDateLabel
import com.amanospica.diary.ui.editor.block.MediaBlockGrid
import com.amanospica.diary.ui.editor.rememberBodyTextMetrics
import com.amanospica.diary.ui.media.FullScreenVideoDialog
import com.amanospica.diary.ui.media.ImageViewerDialog
import com.amanospica.diary.ui.media.playFile
import com.amanospica.diary.ui.media.rememberExoPlayer
import com.amanospica.diary.ui.richtext.buildRichText

/**
 * 書き終わった日記を読む画面。
 *
 * 本文には触れず、写真は拡大、動画は再生だけができる。書き直したくなったときは
 * 右上の鉛筆から編集画面へ移る。編集を終えて戻ると、購読しているので新しい内容がそのまま出る。
 *
 * 本文の組み立て（ブロックの畳み方・装飾の重ね方・行送り）は編集画面と同じ道具を使うので、
 * 書いているときと読んでいるときで見え方が変わらない。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    onNavigateUp: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ViewerViewModel = viewModel(factory = DiaryViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var viewerImagePath by remember { mutableStateOf<String?>(null) }
    var playingBlockId by remember { mutableStateOf<String?>(null) }
    var isFullScreenVideo by remember { mutableStateOf(false) }

    val player = rememberExoPlayer()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ViewerEvent.Close -> onNavigateUp()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.viewer_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    // 星は塗りつぶしの有無で状態を示す。読み返したい日記の目印
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            imageVector = if (uiState.isFavorite) {
                                Icons.Filled.Star
                            } else {
                                Icons.Outlined.StarBorder
                            },
                            contentDescription = stringResource(
                                if (uiState.isFavorite) R.string.favorite_remove
                                else R.string.favorite_add
                            ),
                            tint = if (uiState.isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            },
                        )
                    }
                    IconButton(onClick = { onEdit(viewModel.diaryId) }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.viewer_edit),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EntryHeader(
                emoji = uiState.emoji,
                dateLabel = uiState.date.toEntryDateLabel(),
                title = uiState.title,
                isDraft = uiState.isDraft,
                // 上のバーとくっつかないよう、読み始めの前に一息置く
                modifier = Modifier.padding(top = 12.dp),
            )

            DiaryBody(
                blocks = uiState.blocks,
                resolvePath = viewModel::resolvePath,
                playingBlockId = playingBlockId,
                player = player,
                onOpenImage = { path -> viewerImagePath = path },
                onPlayVideo = { block ->
                    playingBlockId = block.id
                    player.playFile(viewModel.resolvePath(block.localFilePath))
                },
                onFullScreen = { isFullScreenVideo = true },
            )

            // 最後まで読んだところで指がかからないよう、下に余白を残す
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    viewerImagePath?.let { path ->
        ImageViewerDialog(absolutePath = path, onDismiss = { viewerImagePath = null })
    }

    if (isFullScreenVideo) {
        FullScreenVideoDialog(player = player, onDismiss = { isFullScreenVideo = false })
    }
}

/**
 * 本文の表示。編集画面と同じ [toBodyRows] で畳み、テキストは
 * [buildRichText] で装飾を重ねてそのまま描く（入力欄は置かない）。
 */
@Composable
private fun DiaryBody(
    blocks: List<DiaryBlock>,
    resolvePath: (String) -> String,
    playingBlockId: String?,
    player: ExoPlayer,
    onOpenImage: (String) -> Unit,
    onPlayVideo: (DiaryBlock.VideoBlock) -> Unit,
    onFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = rememberBodyTextMetrics()

    // 行送りの余りをテキスト自身が持つため、編集画面と同じく間隔を足さずに詰めて並べる
    Column(modifier = modifier) {
        blocks.toBodyRows().forEach { row ->
            when (row) {
                is DiaryBodyRow.Text -> {
                    // 空のテキストブロックは「書き足す先」として置かれたものなので、
                    // 読むときは空行にせず飛ばす
                    if (row.block.text.isNotEmpty()) {
                        Text(
                            text = buildRichText(
                                source = row.block.text,
                                spans = row.block.spans,
                                headingStyle = metrics.headingSpanStyle,
                                headingLineHeight = metrics.headingLineHeight,
                            ).text,
                            style = metrics.paragraphStyle,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                is DiaryBodyRow.Media -> MediaBlockGrid(
                    blocks = row.blocks,
                    resolvePath = resolvePath,
                    playingBlockId = playingBlockId,
                    player = player,
                    onOpenImage = onOpenImage,
                    onPlayVideo = onPlayVideo,
                    onFullScreen = onFullScreen,
                    // 閲覧中は消させないので、写真の右上にごみ箱を出さない
                    onDelete = null,
                    modifier = Modifier.padding(vertical = metrics.mediaGap),
                )
            }
        }
    }
}

/**
 * 日付・下書きの印とタイトルを左に積み、その日の気分の絵文字を右上に大きく置く見出し。
 * 閲覧中は触っても何も起きない。
 *
 * タイトルまで含めて一つの行にするのは、絵文字を大きくしても縦の隙間が空かないようにするため。
 * 絵文字だけの行を作ると、その背の高さのぶん日付と本文の間が間延びしてしまう。
 */
@Composable
private fun EntryHeader(
    emoji: String,
    dateLabel: String,
    title: String?,
    isDraft: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        // 絵文字の方が背が高いので、日付は上端に揃えて並びの重心をずらさない
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isDraft) {
                    DraftBadge()
                }
            }
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji, fontSize = 36.sp)
        }
    }
}
