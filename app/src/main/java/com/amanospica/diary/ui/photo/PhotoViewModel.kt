package com.amanospica.diary.ui.photo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.domain.usecase.ObserveTimelineUseCase
import com.amanospica.diary.domain.usecase.ResolveMediaPathUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * フォト一覧のマス目1つ分。
 *
 * 元の日記へ戻れるよう、どの日記のどのブロックだったかを持ち歩く。
 * 日付の文字起こしは表示側（string リソース）に任せ、ここでは [date] のまま渡す。
 */
data class PhotoItemUiState(
    /** 「日記ID:ブロックID」。グリッドのキーに使うので一覧の中で必ず一意になる。 */
    val id: String,
    val absolutePath: String,
    val isVideo: Boolean,
    val diaryId: String,
    /** 貼り付け元の日記が書きかけかどうか（開くときの行き先が編集／閲覧で変わる）。 */
    val isDraft: Boolean,
    val date: LocalDate,
)

/** 月ごとの区切り。 */
data class PhotoMonthSection(
    val year: Int,
    val month: Int,
    val items: List<PhotoItemUiState>,
) {
    val key: String get() = "$year-$month"
}

data class PhotoUiState(
    val isLoading: Boolean = true,
    val sections: List<PhotoMonthSection> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && sections.isEmpty()
}

/**
 * フォトタブ。全日記に貼り付けられた写真・動画を、日記をまたいで新しい順に並べる。
 *
 * タイムラインと同じ購読を使い回すので、日記を消せば一覧からも自然に消える。
 */
class PhotoViewModel(
    observeTimeline: ObserveTimelineUseCase,
    resolveMediaPath: ResolveMediaPathUseCase,
) : ViewModel() {

    val uiState: StateFlow<PhotoUiState> = observeTimeline()
        .map { diaries ->
            PhotoUiState(
                isLoading = false,
                sections = diaries.toPhotoSections(resolveMediaPath::invoke),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PhotoUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * 日記の一覧を、月ごとに区切った写真・動画の一覧へ組み替える。
 *
 * 受け取る日記は日付降順（`ObserveTimelineUseCase` の並び）である前提。
 * 同じ月の写真は必ず連続して現れるので、`groupBy` の並びがそのまま表示順になる。
 * 1件の日記の中では、本文に現れる順＝貼り付けた順に並ぶ。
 */
internal fun List<Diary>.toPhotoSections(
    resolveMediaPath: (String) -> String,
): List<PhotoMonthSection> =
    flatMap { diary -> diary.toPhotoItems(resolveMediaPath) }
        .groupBy { it.date.year to it.date.monthValue }
        .map { (yearMonth, items) ->
            PhotoMonthSection(year = yearMonth.first, month = yearMonth.second, items = items)
        }

private fun Diary.toPhotoItems(
    resolveMediaPath: (String) -> String,
): List<PhotoItemUiState> = blocks.mapNotNull { block ->
    val (relativePath, isVideo) = when (block) {
        is DiaryBlock.ImageBlock -> block.localFilePath to false
        is DiaryBlock.VideoBlock -> block.localFilePath to true
        is DiaryBlock.TextBlock -> return@mapNotNull null
    }
    PhotoItemUiState(
        id = "$id:${block.id}",
        absolutePath = resolveMediaPath(relativePath),
        isVideo = isVideo,
        diaryId = id,
        isDraft = isDraft,
        date = date,
    )
}
