package com.amanospica.diary.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.domain.usecase.DeleteDiaryUseCase
import com.amanospica.diary.domain.usecase.ObserveTimelineUseCase
import com.amanospica.diary.domain.usecase.ResolveMediaPathUseCase
import com.amanospica.diary.ui.common.MediaThumbnail
import com.amanospica.diary.ui.common.toCardUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** タイムラインのカード1枚分。日付はカードの中に大きく置く。 */
data class TimelineEntryUiState(
    val id: String,
    /** 2桁ゼロ埋めの日。 */
    val day: String,
    val month: String,
    val emoji: String,
    val title: String?,
    val preview: String,
    /** 書いた時刻。カードには出さないが、並べ替えや将来の表示のために保持しておく。 */
    val time: String,
    /** カード下部に並べるプレビュー（先頭から最大 [MAX_TIMELINE_THUMBNAILS] 枚）。 */
    val thumbnails: List<MediaThumbnail>,
    /** 日記が持つ写真・動画の総数。[thumbnails] に収まらない分は「+n」で示す。 */
    val mediaCount: Int,
    /** 書きかけの日記。カードに「下書き」の印を出す。 */
    val isDraft: Boolean = false,
    /** お気に入り（星）を付けた日記。カードに星の印を出す。 */
    val isFavorite: Boolean = false,
)

/** カードに並べるプレビューの最大枚数。 */
const val MAX_TIMELINE_THUMBNAILS = 4

/** 年ごとの区切り。 */
data class TimelineYearSection(
    val year: Int,
    val entries: List<TimelineEntryUiState>,
)

data class TimelineUiState(
    val isLoading: Boolean = true,
    val sections: List<TimelineYearSection> = emptyList(),
    /** 星を付けた日記だけに絞り込んでいるか。 */
    val isFavoritesOnly: Boolean = false,
    /** 長押しで選んだ日記のID。1件でも入っているあいだが選択モード。 */
    val selectedIds: Set<String> = emptySet(),
    /** まとめて削除する前の確認ダイアログを出しているか。 */
    val isDeleteConfirmVisible: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && sections.isEmpty()

    /** 選択モード中はカードのタップが「開く」ではなく「選ぶ／外す」になる。 */
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

class TimelineViewModel(
    observeTimeline: ObserveTimelineUseCase,
    private val deleteDiary: DeleteDiaryUseCase,
    private val resolveMediaPath: ResolveMediaPathUseCase,
) : ViewModel() {

    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val deleteConfirmVisible = MutableStateFlow(false)
    private val favoritesOnly = MutableStateFlow(false)

    val uiState: StateFlow<TimelineUiState> =
        combine(
            observeTimeline(),
            favoritesOnly,
            selectedIds,
            deleteConfirmVisible,
        ) { diaries, onlyFavorites, selected, confirming ->
            // 絞り込みは購読済みの一覧に掛ける。切り替えのたびに読み直さないので、
            // 星を付け外ししてもその場で並びが変わる
            val shown = if (onlyFavorites) diaries.filter { it.isFavorite } else diaries
            // 別の画面で消された日記が選択に残り続けないよう、今出ている日記だけに絞る
            val alive = selected.intersect(shown.mapTo(mutableSetOf()) { it.id })
            TimelineUiState(
                isLoading = false,
                isFavoritesOnly = onlyFavorites,
                // DAO が date DESC, createdAt DESC で返すため、groupBy の順序がそのまま表示順になる
                sections = shown.groupBy { it.date.year }.map { (year, entries) ->
                    TimelineYearSection(
                        year = year,
                        entries = entries.map { diary ->
                            diary.toTimelineEntry(resolveMediaPath = resolveMediaPath::invoke)
                        },
                    )
                },
                selectedIds = alive,
                // 消し終えて選択が空になったらダイアログも用済み
                isDeleteConfirmVisible = confirming && alive.isNotEmpty(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = TimelineUiState(),
        )

    /**
     * 星を付けた日記だけを出す／すべて出すを切り替える。
     *
     * 絞り込むと選択したカードが隠れることがあるので、切り替えは選択をやめる合図として扱う
     * （見えていない日記をまとめ削除してしまわないようにする）。
     */
    fun toggleFavoritesOnly() {
        favoritesOnly.update { !it }
        clearSelection()
    }

    /** 長押し・選択モード中のタップで、その日記の選択を切り替える。 */
    fun toggleSelection(diaryId: String) {
        selectedIds.update { current ->
            if (diaryId in current) current - diaryId else current + diaryId
        }
    }

    /** 選択をすべて外して通常のタイムラインへ戻す。 */
    fun clearSelection() {
        selectedIds.value = emptySet()
        deleteConfirmVisible.value = false
    }

    fun requestDeleteSelected() {
        if (selectedIds.value.isNotEmpty()) deleteConfirmVisible.value = true
    }

    fun dismissDeleteConfirm() {
        deleteConfirmVisible.value = false
    }

    /**
     * 選択中の日記を、貼り付けてあった写真・動画ごとまとめて消す。
     * 消し終えると一覧から居なくなるので、選択も自然と空になり選択モードから抜ける。
     */
    fun deleteSelected() {
        val targets = selectedIds.value
        deleteConfirmVisible.value = false
        if (targets.isEmpty()) return
        viewModelScope.launch {
            targets.forEach { id -> deleteDiary(id) }
            selectedIds.value = emptySet()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun Diary.toTimelineEntry(
    resolveMediaPath: (String) -> String,
): TimelineEntryUiState {
    val card = toCardUiState(resolveMediaPath)
    // 本文に現れる順（＝書いた順）でメディアを拾う
    val media = blocks.mapNotNull { block ->
        when (block) {
            is DiaryBlock.ImageBlock ->
                MediaThumbnail(resolveMediaPath(block.localFilePath), isVideo = false)

            is DiaryBlock.VideoBlock ->
                MediaThumbnail(resolveMediaPath(block.localFilePath), isVideo = true)

            is DiaryBlock.TextBlock -> null
        }
    }

    return TimelineEntryUiState(
        id = card.id,
        day = "%02d".format(date.dayOfMonth),
        month = "${date.monthValue}月",
        emoji = card.emoji,
        title = card.title,
        preview = card.preview,
        time = card.time,
        thumbnails = media.take(MAX_TIMELINE_THUMBNAILS),
        mediaCount = media.size,
        isDraft = card.isDraft,
        isFavorite = card.isFavorite,
    )
}
