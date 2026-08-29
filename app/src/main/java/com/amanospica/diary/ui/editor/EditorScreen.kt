package com.amanospica.diary.ui.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amanospica.diary.R
import com.amanospica.diary.ui.common.DiaryBodyRow
import com.amanospica.diary.ui.common.DiaryViewModelFactory
import com.amanospica.diary.ui.common.toBodyRows
import com.amanospica.diary.ui.common.toEntryDateLabel
import com.amanospica.diary.ui.editor.block.MediaBlockGrid
import com.amanospica.diary.ui.editor.block.TextBlockEditor
import com.amanospica.diary.ui.media.FullScreenVideoDialog
import com.amanospica.diary.ui.media.ImageViewerDialog
import com.amanospica.diary.ui.media.playFile
import com.amanospica.diary.ui.media.rememberExoPlayer

/**
 * 日記の作成・編集画面（Notion 風ブロックエディタ）。
 *
 * 本文はブロックの列で、テキストブロックの間に画像・動画ブロックを挟める。
 * メディアはツールバーから選ぶと、そのときのカーソル位置でテキストが分割され、間に差し込まれる。
 *
 * ブロックが分かれるのはメディアを挟んだ位置だけで、改行では分かれない。
 * 1つのテキストブロックが入力欄1つに対応するため、メディアを挟まない日記なら
 * 全選択もドラッグ選択も本文全体に効く。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = viewModel(factory = DiaryViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showEmojiPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var viewerImagePath by remember { mutableStateOf<String?>(null) }
    var playingBlockId by remember { mutableStateOf<String?>(null) }
    var isFullScreenVideo by remember { mutableStateOf(false) }

    val player = rememberExoPlayer()

    // 写真は一度にまとめて選べる（枚数の上限は端末のフォトピッカーに任せる）
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> viewModel.insertImages(uris.map { it.toString() }) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.insertVideo(it.toString()) } }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.Close -> onNavigateUp()
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorShown()
        }
    }

    // 端末の戻る操作でも書きかけを失わないよう、保存してから閉じる
    BackHandler { viewModel.saveAndClose() }

    // アプリを離れた時点の内容を確実に残す。
    // 自動保存の待ち時間中にホームへ戻る・ロックがかかる・アプリが落とされる、を取りこぼさないため
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveNow() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isNewEntry) R.string.editor_title_new
                            else R.string.editor_title_edit
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = viewModel::saveAndClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (!uiState.isNewEntry) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.editor_delete_diary),
                            )
                        }
                    }
                    IconButton(onClick = viewModel::completeAndClose) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.editor_save),
                        )
                    }
                },
            )
        },
        bottomBar = {
            FormattingToolbar(
                activeSpanTypes = uiState.activeSpanTypes,
                isTextFocused = uiState.focus != null,
                isAttachingMedia = uiState.isAttachingMedia,
                onToggleSpan = viewModel::toggleSpan,
                onPickImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPickVideo = {
                    videoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                // キーボードとナビゲーションバーのよけ方はツールバー自身が持つ。
                // よけたぶんツールバーの高さが伸びるので、Scaffold が本文へ渡す余白も
                // 一緒に広がり、本文が隠れることはない
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
                onEmojiClick = { showEmojiPicker = true },
                onDateClick = { showDatePicker = true },
                onDraftClick = viewModel::onDraftToggle,
                onTitleChange = viewModel::onTitleChange,
                // 閲覧画面と同じだけ上に一息置く
                modifier = Modifier.padding(top = 12.dp),
            )

            // テキストブロックは行送りの余りを自前で持っているため、間隔を足さずに詰めて並べる。
            // これで「入力欄の中の改行」と「メディアを挟んだブロックの境目」の間隔が一致する
            val metrics = rememberBodyTextMetrics()
            Column {
                uiState.blocks.toBodyRows().forEach { row ->
                    when (row) {
                        is DiaryBodyRow.Text -> TextBlockEditor(
                            block = row.block,
                            placeholder = if (row.isFirstBlock) {
                                stringResource(R.string.editor_body_placeholder)
                            } else {
                                ""
                            },
                            onTextChange = { text, start, end ->
                                viewModel.onTextChange(row.block.id, text, start, end)
                            },
                            onSelectionChange = { start, end ->
                                viewModel.onSelectionChange(row.block.id, start, end)
                            },
                            onFocusLost = { viewModel.onFocusLost(row.block.id) },
                            requestFocusAt = uiState.pendingFocus
                                ?.takeIf { it.blockId == row.block.id }
                                ?.selection,
                            onFocusHandled = { viewModel.onFocusHandled(row.block.id) },
                            onBackspaceAtStart = {
                                viewModel.onBackspaceAtBlockStart(row.block.id)
                            },
                            onMoveToPreviousBlock = {
                                viewModel.onMoveToPreviousBlock(row.block.id)
                            },
                            onMoveToNextBlock = {
                                viewModel.onMoveToNextBlock(row.block.id)
                            },
                        )

                        is DiaryBodyRow.Media -> MediaBlockGrid(
                            blocks = row.blocks,
                            resolvePath = viewModel::resolvePath,
                            playingBlockId = playingBlockId,
                            player = player,
                            onOpenImage = { path -> viewerImagePath = path },
                            onPlayVideo = { block ->
                                playingBlockId = block.id
                                player.playFile(viewModel.resolvePath(block.localFilePath))
                            },
                            onFullScreen = { isFullScreenVideo = true },
                            onDelete = { block ->
                                if (playingBlockId == block.id) {
                                    player.stop()
                                    playingBlockId = null
                                }
                                viewModel.deleteBlock(block.id)
                            },
                            modifier = Modifier.padding(vertical = metrics.mediaGap),
                        )
                    }
                }
            }

            // 本文の下の空きをタップしたら書き足せるようにする。
            // 画像で終わっている日記でも、ここを触れば入力欄が現れる
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = viewModel::onTapBelowContent,
                    )
            )
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteAndClose()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    if (showEmojiPicker) {
        EmojiPickerSheet(
            selectedEmoji = uiState.emoji,
            onSelect = { emoji ->
                viewModel.onEmojiSelect(emoji)
                showEmojiPicker = false
            },
            onDismiss = { showEmojiPicker = false },
        )
    }

    if (showDatePicker) {
        EntryDatePickerDialog(
            date = uiState.date,
            onSelect = { date ->
                viewModel.onDateSelect(date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    viewerImagePath?.let { path ->
        ImageViewerDialog(absolutePath = path, onDismiss = { viewerImagePath = null })
    }

    if (isFullScreenVideo) {
        FullScreenVideoDialog(player = player, onDismiss = { isFullScreenVideo = false })
    }
}

/**
 * 削除は取り消せないうえ、写真・動画も一緒に消える。
 * ごみ箱の押し間違いで日記を失わないよう、一度確認を挟む。
 */
@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_delete_confirm_title)) },
        text = { Text(stringResource(R.string.editor_delete_confirm_message)) },
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

/**
 * 日付・下書きの印とタイトルを左に積み、その日の気分の絵文字を右上に大きく置く見出し。
 * 絵文字・日付・下書きはそれぞれタップで変えられる。
 *
 * 置き方を閲覧画面の見出しに合わせてあるので、書いているときと読み返すときで
 * 絵文字の位置が動かない。タイトルまで含めて一つの行にするのも同じ理由で、
 * 絵文字だけの行を作ると日付と本文の間が間延びしてしまう。
 */
@Composable
private fun EntryHeader(
    emoji: String,
    dateLabel: String,
    title: String,
    isDraft: Boolean,
    onEmojiClick: () -> Unit,
    onDateClick: () -> Unit,
    onDraftClick: () -> Unit,
    onTitleChange: (String) -> Unit,
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(onClick = onDateClick, label = { Text(dateLabel) })
                FilterChip(
                    selected = isDraft,
                    onClick = onDraftClick,
                    label = {
                        Text(
                            stringResource(
                                if (isDraft) R.string.editor_status_draft
                                else R.string.editor_status_done
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isDraft) Icons.Filled.EditNote else Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                )
            }
            TitleField(title = title, onTitleChange = onTitleChange)
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onEmojiClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji, fontSize = 36.sp)
        }
    }
}

/** 改行文字（`\n` / `\r\n` のどちらも）。 */
private val NEWLINES = Regex("""\R""")

/**
 * タイトルの入力欄。幅に収まらないタイトルは折り返して全部見せる。
 * 横に流れて端が隠れると、書いている途中で頭が読めなくなるため。
 *
 * 折り返して表示はするが、改行そのものは入れさせない。閲覧画面や一覧では
 * 1 つの見出しとして扱うので、キーボードの改行キーは「完了」にして入力欄から抜け、
 * 貼り付けに混ざった改行は取り除く。
 */
@Composable
private fun TitleField(
    title: String,
    onTitleChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textStyle = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val focusManager = LocalFocusManager.current
    Box(modifier = modifier.fillMaxWidth()) {
        if (title.isEmpty()) {
            Text(
                text = stringResource(R.string.editor_title_placeholder),
                style = textStyle,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        BasicTextField(
            value = title,
            onValueChange = { onTitleChange(it.replace(NEWLINES, "")) },
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
