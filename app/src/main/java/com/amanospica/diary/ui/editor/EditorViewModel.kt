package com.amanospica.diary.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.domain.model.DiaryEmoji
import com.amanospica.diary.domain.model.SpanType
import com.amanospica.diary.domain.usecase.AttachImageUseCase
import com.amanospica.diary.domain.usecase.AttachVideoUseCase
import com.amanospica.diary.domain.usecase.CleanUpOrphanMediaUseCase
import com.amanospica.diary.domain.usecase.DeleteDiaryUseCase
import com.amanospica.diary.domain.usecase.GetDiaryUseCase
import com.amanospica.diary.domain.usecase.ResolveMediaPathUseCase
import com.amanospica.diary.domain.usecase.SaveDiaryUseCase
import com.amanospica.diary.ui.richtext.TextSpanEditor
import com.amanospica.diary.ui.richtext.canonicalizeParagraphStyles
import com.amanospica.diary.ui.richtext.hasParagraphStyle
import com.amanospica.diary.ui.richtext.lineRangesCovering
import com.amanospica.diary.ui.richtext.toggleParagraphStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

/** 編集中のカーソル位置。装飾ツールバーの適用先になる。 */
data class BlockFocus(
    val blockId: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

/** 自動でカーソルを入れたいブロックと、その中での位置。 */
data class PendingFocus(
    val blockId: String,
    val selection: Int,
)

data class EditorUiState(
    val isLoading: Boolean = true,
    val isNewEntry: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val emoji: String = DiaryEmoji.DEFAULT.emoji,
    val title: String = "",
    val blocks: List<DiaryBlock> = listOf(DiaryBlock.TextBlock()),
    /** 書きかけ（下書き）として保存するか。新規の日記は「完了」を押すまで下書き。 */
    val isDraft: Boolean = true,
    val focus: BlockFocus? = null,
    /** ブロックの分割・結合の直後など、入力欄へ自動でカーソルを移したい位置。 */
    val pendingFocus: PendingFocus? = null,
    val isAttachingMedia: Boolean = false,
    val errorMessage: String? = null,
) {
    /** ツールバーで点灯させる装飾。 */
    val activeSpanTypes: Set<SpanType>
        get() {
            val focused = focus ?: return emptySet()
            val block = blocks.firstOrNull { it.id == focused.blockId } as? DiaryBlock.TextBlock
                ?: return emptySet()
            return SpanType.entries.filterTo(mutableSetOf()) { type ->
                if (type.isParagraphStyle) {
                    // 見出し・箇条書きは行ごとのスタイル。掛かっている行すべてに付いていれば点灯
                    val lines = block.text.lineRangesCovering(
                        focused.selectionStart,
                        focused.selectionEnd,
                    )
                    lines.isNotEmpty() && lines.all { block.spans.hasParagraphStyle(type, it) }
                } else {
                    TextSpanEditor.isApplied(
                        block.spans,
                        type,
                        focused.selectionStart,
                        focused.selectionEnd,
                    )
                }
            }
        }
}

/** 画面を閉じるなどの一度きりの通知。 */
sealed interface EditorEvent {
    data object Close : EditorEvent
}

class EditorViewModel(
    private val diaryId: String?,
    initialDate: LocalDate,
    private val getDiary: GetDiaryUseCase,
    private val saveDiary: SaveDiaryUseCase,
    private val deleteDiary: DeleteDiaryUseCase,
    private val attachImage: AttachImageUseCase,
    private val attachVideo: AttachVideoUseCase,
    private val cleanUpOrphanMedia: CleanUpOrphanMediaUseCase,
    private val resolveMediaPath: ResolveMediaPathUseCase,
) : ViewModel() {

    private val entryId: String = diaryId ?: UUID.randomUUID().toString()
    private var createdAt: Long = System.currentTimeMillis()

    /**
     * 開いた日記に付いていたお気に入り（星）。
     *
     * 保存では画面の内容から [Diary] を組み直すため、ここで持っておかないと
     * 書き足しただけで星が外れてしまう。星の付け外し自体は閲覧画面が受け持つ。
     */
    private var isFavorite: Boolean = false

    /** この日記の行が DB にあるか。自動保存で新規作成した直後も true になる。 */
    private var isPersisted: Boolean = diaryId != null

    /** 削除後に、待機していた自動保存が日記を書き戻してしまうのを防ぐ。 */
    private var isDeleted = false

    private var autoSaveJob: Job? = null

    /** 自動保存と明示的な保存が重ならないようにする。 */
    private val saveLock = Mutex()

    private val _uiState = MutableStateFlow(
        EditorUiState(isLoading = diaryId != null, isNewEntry = diaryId == null, date = initialDate)
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    init {
        if (diaryId != null) loadExisting(diaryId)
        startAutoSave()
    }

    private fun loadExisting(id: String) {
        viewModelScope.launch {
            val diary = getDiary(id)
            if (diary == null) {
                // 一覧から開いた直後に削除された等。新規扱いで開き直す
                isPersisted = false
                _uiState.update { it.copy(isLoading = false, isNewEntry = true) }
                return@launch
            }
            createdAt = diary.createdAt
            isFavorite = diary.isFavorite
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isNewEntry = false,
                    date = diary.date,
                    emoji = diary.emoji,
                    title = diary.title.orEmpty(),
                    blocks = diary.blocks.mergeAdjacentTextBlocks().withTrailingTextBlock(),
                    isDraft = diary.isDraft,
                )
            }
        }
    }

    fun resolvePath(relativePath: String): String = resolveMediaPath(relativePath)

    // --- メタデータ ---

    fun onTitleChange(title: String) = _uiState.update { it.copy(title = title) }

    fun onEmojiSelect(emoji: String) = _uiState.update { it.copy(emoji = emoji) }

    fun onDateSelect(date: LocalDate) = _uiState.update { it.copy(date = date) }

    /**
     * 下書き／書き終わりを手で切り替える。
     * 書き終えた日記に後から書き足したくなったときに、また下書きへ戻せるようにしておく。
     */
    fun onDraftToggle() = _uiState.update { it.copy(isDraft = !it.isDraft) }

    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null) }

    // --- テキストブロック ---

    /**
     * 本文の変更を取り込み、装飾範囲を編集内容に追従させる。
     *
     * 改行はそのまま本文の一部として持つ。ブロックを割るのは画像・動画を挟むときだけなので、
     * 「Enter を押しても入力欄は1つのまま」＝全選択もドラッグ選択も本文全体に効く。
     */
    fun onTextChange(blockId: String, newText: String, selectionStart: Int, selectionEnd: Int) {
        _uiState.update { state ->
            val blocks = state.blocks.map { block ->
                if (block.id != blockId || block !is DiaryBlock.TextBlock) {
                    block
                } else {
                    val change = TextSpanEditor.computeChange(block.text, newText)
                    block.copy(
                        text = newText,
                        spans = TextSpanEditor.remap(block.spans, change, newText.length)
                            .canonicalizeParagraphStyles(newText),
                    )
                }
            }
            state.copy(
                blocks = blocks,
                focus = BlockFocus(blockId, selectionStart, selectionEnd),
            )
        }
    }

    /**
     * ブロックの先頭でバックスペースを押したときの動作。
     *
     * 直前がテキストブロックならそこへ結合する。
     * 先頭ブロックでは何もしない。
     *
     * 直前が画像・動画の場合も何もしない。写真は文字と違って打ち直しが利かないため、
     * 消すのはサムネイルのゴミ箱ボタンからだけにして、キー操作では消えないようにしている。
     */
    fun onBackspaceAtBlockStart(blockId: String) {
        _uiState.update { state ->
            val index = state.blocks.indexOfFirst { it.id == blockId }
            if (index <= 0) return@update state
            val current = state.blocks[index] as? DiaryBlock.TextBlock ?: return@update state

            when (val previous = state.blocks[index - 1]) {
                is DiaryBlock.TextBlock -> {
                    val offset = previous.text.length
                    val mergedText = previous.text + current.text
                    val merged = previous.copy(
                        text = mergedText,
                        spans = TextSpanEditor.normalize(
                            spans = previous.spans + current.spans.map {
                                it.copy(start = it.start + offset, end = it.end + offset)
                            },
                            textLength = mergedText.length,
                        ).canonicalizeParagraphStyles(mergedText),
                    )
                    val blocks = state.blocks.toMutableList().apply {
                        set(index - 1, merged)
                        removeAt(index)
                    }
                    state.copy(
                        blocks = blocks,
                        focus = BlockFocus(merged.id, offset, offset),
                        // 結合前の境目にカーソルを置く
                        pendingFocus = PendingFocus(merged.id, offset),
                    )
                }

                // 画像・動画は消さずにそのまま残す
                else -> state
            }
        }
    }

    /**
     * ブロックの先頭で「←」を押したときの動作。
     * 直前のテキストブロックの末尾へカーソルを移す（画像・動画は飛び越える）。
     */
    fun onMoveToPreviousBlock(blockId: String) {
        _uiState.update { state ->
            val index = state.blocks.indexOfFirst { it.id == blockId }
            if (index <= 0) return@update state
            val target = state.blocks.take(index)
                .lastOrNull { it is DiaryBlock.TextBlock } as? DiaryBlock.TextBlock
                ?: return@update state

            state.copy(pendingFocus = PendingFocus(target.id, target.text.length))
        }
    }

    /**
     * ブロックの末尾で「→」を押したときの動作。
     * 次のテキストブロックの先頭へカーソルを移す（画像・動画は飛び越える）。
     */
    fun onMoveToNextBlock(blockId: String) {
        _uiState.update { state ->
            val index = state.blocks.indexOfFirst { it.id == blockId }
            if (index < 0) return@update state
            val target = state.blocks.drop(index + 1)
                .firstOrNull { it is DiaryBlock.TextBlock } as? DiaryBlock.TextBlock
                ?: return@update state

            state.copy(pendingFocus = PendingFocus(target.id, 0))
        }
    }

    /**
     * 本文より下の余白をタップしたときの動作。
     * 末尾がメディアなら書き足す先を作り、テキストならその末尾へカーソルを移す。
     */
    fun onTapBelowContent() {
        _uiState.update { state ->
            when (val last = state.blocks.lastOrNull()) {
                is DiaryBlock.TextBlock ->
                    state.copy(pendingFocus = PendingFocus(last.id, last.text.length))

                else -> {
                    val added = DiaryBlock.TextBlock()
                    state.copy(
                        blocks = state.blocks + added,
                        pendingFocus = PendingFocus(added.id, 0),
                    )
                }
            }
        }
    }

    fun onSelectionChange(blockId: String, selectionStart: Int, selectionEnd: Int) {
        _uiState.update { it.copy(focus = BlockFocus(blockId, selectionStart, selectionEnd)) }
    }

    /** 自動フォーカスを1回だけ効かせるための後始末。 */
    fun onFocusHandled(blockId: String) {
        _uiState.update {
            if (it.pendingFocus?.blockId == blockId) it.copy(pendingFocus = null) else it
        }
    }

    fun onFocusLost(blockId: String) {
        _uiState.update { if (it.focus?.blockId == blockId) it.copy(focus = null) else it }
    }

    /**
     * 装飾をオン／オフする。
     * 太字・斜体・下線は選択した文字だけに、見出し・箇条書きは選択が掛かっている行全体に効く。
     */
    fun toggleSpan(type: SpanType) {
        val focused = _uiState.value.focus ?: return
        _uiState.update { state ->
            val blocks = state.blocks.map { block ->
                if (block.id != focused.blockId || block !is DiaryBlock.TextBlock) {
                    block
                } else {
                    block.copy(
                        spans = if (type.isParagraphStyle) {
                            block.spans.toggleParagraphStyle(
                                type = type,
                                text = block.text,
                                selectionStart = focused.selectionStart,
                                selectionEnd = focused.selectionEnd,
                            )
                        } else {
                            TextSpanEditor.toggle(
                                spans = block.spans,
                                type = type,
                                start = focused.selectionStart,
                                end = focused.selectionEnd,
                                textLength = block.text.length,
                            )
                        }
                    )
                }
            }
            state.copy(blocks = blocks)
        }
    }

    // --- メディアブロック ---

    /** 選んだ写真をまとめて取り込む。1枚だけ選んだ場合もここを通る。 */
    fun insertImages(sourceUris: List<String>) = attachMedia(sourceUris) { attachImage(it) }

    fun insertVideo(sourceUri: String) = attachMedia(listOf(sourceUri)) { attachVideo(it) }

    /**
     * 選ばれたメディアを順に取り込み、取り込めた分をまとめてカーソル位置へ差し込む。
     *
     * 画像は1枚ずつ展開して圧縮するため、まとめて選ばれても並列には走らせない
     * （大きな写真を同時に開くとメモリを使い切る）。
     * 一部が失敗しても成功した分は残し、失敗した件数だけを知らせる。
     */
    private fun attachMedia(
        sourceUris: List<String>,
        attach: suspend (String) -> Result<DiaryBlock>,
    ) {
        if (sourceUris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAttachingMedia = true) }

            val attached = mutableListOf<DiaryBlock>()
            var firstError: Throwable? = null
            sourceUris.forEach { uri ->
                attach(uri)
                    .onSuccess { block -> attached += block }
                    .onFailure { error -> if (firstError == null) firstError = error }
            }
            if (attached.isNotEmpty()) insertBlocksAtCursor(attached)

            val failureCount = sourceUris.size - attached.size
            _uiState.update { state ->
                state.copy(
                    isAttachingMedia = false,
                    errorMessage = attachErrorMessage(failureCount, sourceUris.size, firstError)
                        ?: state.errorMessage,
                )
            }
        }
    }

    /** 取り込みに失敗したときの知らせ。1件だけなら原因を、複数選択なら件数を出す。 */
    private fun attachErrorMessage(
        failureCount: Int,
        totalCount: Int,
        firstError: Throwable?,
    ): String? = when {
        failureCount == 0 -> null
        totalCount == 1 -> firstError?.message ?: ATTACH_ERROR_MESSAGE
        else -> "${failureCount}件のメディアを取り込めませんでした"
    }

    /**
     * カーソル位置でテキストブロックを2つに割り、その間へメディアブロックを差し込む。
     * これが「文章の間に画像・動画を置く」操作の実体。
     * 複数まとめて差し込む場合も、選んだ順どおりに1か所へ並べる。
     */
    private fun insertBlocksAtCursor(newBlocks: List<DiaryBlock>) {
        _uiState.update { state ->
            val blocks = state.blocks.toMutableList()
            val focusIndex = state.focus
                ?.let { focused -> blocks.indexOfFirst { it.id == focused.blockId } }
                ?: -1
            val target = blocks.getOrNull(focusIndex) as? DiaryBlock.TextBlock

            if (target == null) {
                // 入力位置が分からないときは末尾へ。続きを書けるよう空ブロックも添える
                blocks += newBlocks
                blocks += DiaryBlock.TextBlock()
            } else {
                val cursor = state.focus!!.selectionEnd.coerceIn(0, target.text.length)
                val (beforeSpans, afterSpans) =
                    TextSpanEditor.split(target.spans, cursor, target.text.length)
                val beforeText = target.text.take(cursor)
                val afterText = target.text.substring(cursor)

                blocks[focusIndex] = target.copy(
                    text = beforeText,
                    spans = beforeSpans.canonicalizeParagraphStyles(beforeText),
                )
                blocks.addAll(focusIndex + 1, newBlocks)
                blocks.add(
                    focusIndex + 1 + newBlocks.size,
                    DiaryBlock.TextBlock(
                        text = afterText,
                        spans = afterSpans.canonicalizeParagraphStyles(afterText),
                    ),
                )
            }
            state.copy(blocks = blocks, focus = null)
        }
    }

    fun deleteBlock(blockId: String) {
        _uiState.update { state ->
            state.copy(
                blocks = state.blocks.filterNot { it.id == blockId }.withTrailingTextBlock(),
                focus = state.focus?.takeIf { it.blockId != blockId },
            )
        }
    }

    // --- 保存・破棄 ---

    /**
     * 編集内容が変わるたびに、入力が途切れてから [AUTO_SAVE_DELAY_MS] で保存する。
     *
     * アプリの強制終了やロックで書きかけが消えるのを防ぐための保険なので、
     * 1文字ごとには書かず、[collectLatest] で最後の変更だけを拾う。
     */
    private fun startAutoSave() {
        autoSaveJob = viewModelScope.launch {
            _uiState
                .filterNot { it.isLoading }
                .map { it.toContent() }
                .distinctUntilChanged()
                // 読み込み直後（＝まだ何も編集していない状態）では書き込まない
                .drop(1)
                .collectLatest { content ->
                    delay(AUTO_SAVE_DELAY_MS)
                    persist(content)
                }
        }
    }

    /**
     * いま画面にある内容をその場で保存する。
     * バックグラウンドへ回るときなど、自動保存の待ち時間を待てない場面から呼ぶ。
     */
    fun saveNow() {
        viewModelScope.launch { persist(_uiState.value.toContent()) }
    }

    /**
     * 内容を DB へ書き込む。空の日記は保存しない（[SaveDiaryUseCase] が判定）。
     *
     * 自動保存で作った行が空になった場合は消す。ここで消さないと、書きかけを消して
     * 画面を閉じたのに一覧へ残ってしまう。既存の日記は誤操作で失わないよう消さない。
     *
     * 保存中に画面が閉じられても書き込みが途中で終わらないよう [NonCancellable] で走らせる。
     */
    private suspend fun persist(content: EditorContent) = withContext(NonCancellable) {
        saveLock.withLock {
            if (isDeleted) return@withLock
            val diary = Diary(
                id = entryId,
                date = content.date,
                emoji = content.emoji,
                title = content.title.trim().ifBlank { null },
                blocks = content.blocks.dropTrailingEmptyText(),
                isDraft = content.isDraft,
                isFavorite = isFavorite,
                createdAt = createdAt,
            )
            if (diary.isBlank) {
                if (isPersisted && diaryId == null) {
                    deleteDiary(entryId)
                    isPersisted = false
                }
                return@withLock
            }
            if (saveDiary(diary)) isPersisted = true
        }
    }

    /**
     * 下書きのまま保存して画面を閉じる（戻る操作）。空の日記は保存せず破棄する。
     * 取り込んだが最終的に使われなかったメディアはここで回収する。
     */
    fun saveAndClose() {
        viewModelScope.launch {
            persist(_uiState.value.toContent())
            cleanUpOrphanMedia()
            _events.send(EditorEvent.Close)
        }
    }

    /**
     * 書き終わりにして保存し、画面を閉じる（チェックボタン）。
     * 下書きの印はここで外れ、一覧では通常の日記として並ぶ。
     */
    fun completeAndClose() {
        _uiState.update { it.copy(isDraft = false) }
        saveAndClose()
    }

    fun deleteAndClose() {
        // 待機中の自動保存が削除後に書き戻さないよう、先に止めてから消す
        isDeleted = true
        autoSaveJob?.cancel()
        viewModelScope.launch {
            // 自動保存で作られた行も消せるよう、画面上の新規／既存ではなく DB の有無で判断する
            if (isPersisted) deleteDiary(entryId)
            isPersisted = false
            cleanUpOrphanMedia()
            _events.send(EditorEvent.Close)
        }
    }
}

/** 自動保存で見る「日記の中身」。カーソル位置などの編集用の状態は含めない。 */
private data class EditorContent(
    val date: LocalDate,
    val emoji: String,
    val title: String,
    val blocks: List<DiaryBlock>,
    val isDraft: Boolean,
)

private fun EditorUiState.toContent(): EditorContent =
    EditorContent(date = date, emoji = emoji, title = title, blocks = blocks, isDraft = isDraft)

/** 入力が途切れてから自動保存するまでの待ち時間。 */
private const val AUTO_SAVE_DELAY_MS = 800L

/** 取り込みに失敗した理由が分からないときの知らせ。 */
private const val ATTACH_ERROR_MESSAGE = "メディアを取り込めませんでした"

/**
 * 隣り合うテキストブロックを、改行を挟んで1つにまとめる。
 *
 * 改行のたびにブロックを割っていた頃に保存した日記は「1行＝1ブロック」で入っている。
 * そのままだと入力欄が行数ぶん並び、全選択が1行にしか掛からないので、開くときにここを通して
 * 今の形（割れるのは画像・動画を挟んだ位置だけ）へ揃える。
 *
 * 画像・動画で隔てられたブロックはまとめない。まとめた結果は元の日記と読み方が変わらない
 * （見た目のうえでもブロックの境目の余白は行間と一致している）。
 */
private fun List<DiaryBlock>.mergeAdjacentTextBlocks(): List<DiaryBlock> {
    val merged = mutableListOf<DiaryBlock>()
    forEach { block ->
        val previous = merged.lastOrNull()
        if (block is DiaryBlock.TextBlock && previous is DiaryBlock.TextBlock) {
            merged[merged.lastIndex] = previous.followedBy(block)
        } else {
            merged += block
        }
    }
    return merged
}

/** [next] を改行1つを挟んで後ろへつなぐ。装飾はつないだ先の位置へずらす。 */
private fun DiaryBlock.TextBlock.followedBy(next: DiaryBlock.TextBlock): DiaryBlock.TextBlock {
    val offset = text.length + 1
    val joined = text + "\n" + next.text
    return copy(
        text = joined,
        spans = TextSpanEditor.normalize(
            spans = spans + next.spans.map {
                it.copy(start = it.start + offset, end = it.end + offset)
            },
            textLength = joined.length,
        ).canonicalizeParagraphStyles(joined),
    )
}

/**
 * 末尾が必ずテキストブロックになるように整える。
 *
 * 保存時に末尾の空テキストブロックは捨てられるため、画像や動画で終わる日記を開き直すと
 * 書き足す先が無くなってしまう。開いた直後とブロック削除後にこれを通しておくことで、
 * 「本文を追加」のような専用ボタンを置かなくても常に文章を続けられる。
 */
private fun List<DiaryBlock>.withTrailingTextBlock(): List<DiaryBlock> =
    if (lastOrNull() is DiaryBlock.TextBlock) this else this + DiaryBlock.TextBlock()

/**
 * 末尾の書き足し用の余りを保存対象から外す。
 *
 * 落とすのは2種類。[withTrailingTextBlock] やメディア挿入が自動で添える空のテキストブロックと、
 * 本文の最後に残った空行（Enter を押したまま画面を離れたときの余り）。
 * 本文の途中の空行は書き手が入れた区切りなので、末尾からたどれる分だけに限って落とす。
 *
 * すべて空になった場合は1つだけ残し、日記自体が空であることは [Diary.isBlank] に判定させる。
 */
private fun List<DiaryBlock>.dropTrailingEmptyText(): List<DiaryBlock> {
    val kept = dropLastWhile { it is DiaryBlock.TextBlock && it.text.isBlank() }
    val last = kept.lastOrNull()
    if (last !is DiaryBlock.TextBlock) return kept.ifEmpty { listOf(DiaryBlock.TextBlock()) }

    val trimmed = last.text.trimEnd()
    if (trimmed == last.text) return kept

    return kept.dropLast(1) + last.copy(
        text = trimmed,
        spans = TextSpanEditor.normalize(last.spans, trimmed.length)
            .canonicalizeParagraphStyles(trimmed),
    )
}
