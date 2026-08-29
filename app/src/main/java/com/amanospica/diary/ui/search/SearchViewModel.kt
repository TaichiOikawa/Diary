package com.amanospica.diary.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.usecase.ResolveMediaPathUseCase
import com.amanospica.diary.domain.usecase.SearchDiariesUseCase
import com.amanospica.diary.domain.usecase.bodyText
import com.amanospica.diary.domain.usecase.toSearchTerms
import com.amanospica.diary.ui.common.MediaThumbnail
import com.amanospica.diary.ui.common.toCardUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** 検索結果1件分。日付の文字起こしは表示側（string リソース）に任せる。 */
data class SearchResultUiState(
    val id: String,
    val date: LocalDate,
    val emoji: String,
    val title: String?,
    /** [title] の中で検索語に一致した範囲。 */
    val titleHighlights: List<IntRange>,
    /** 一致箇所まわりの抜粋。本文が空なら文字列も空。 */
    val snippet: SearchSnippet,
    val thumbnail: MediaThumbnail?,
    val isDraft: Boolean,
    val isFavorite: Boolean,
)

data class SearchUiState(
    /** 検索語が入力されているか。未入力のうちは「0件」ではなく案内を出す。 */
    val hasQuery: Boolean = false,
    val results: List<SearchResultUiState> = emptyList(),
) {
    /** 探したが1件も無い状態。 */
    val isEmpty: Boolean get() = hasQuery && results.isEmpty()
}

/**
 * 検索画面。入力欄の文字列を検索語に切り出し、一致した日記を新しい順に並べる。
 *
 * 入力欄の値（[query]）と結果（[uiState]）を分けているのは、打っている文字は
 * そのまま出しつつ、結果の作り直しだけを少し待たせるため。1文字ごとに全件を
 * 走査し直すと、長い日記が増えたときに入力が引っかかる。
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    searchDiaries: SearchDiariesUseCase,
    resolveMediaPath: ResolveMediaPathUseCase,
) : ViewModel() {

    private val _query = MutableStateFlow("")

    /** 入力欄に出ている文字列。打った端から反映される。 */
    val query: StateFlow<String> = _query.asStateFlow()

    val uiState: StateFlow<SearchUiState> = _query
        .map { it.toSearchTerms() }
        .distinctUntilChanged()
        // 消し切ったときは待たずに案内へ戻す（待たせても見せるものが無い）
        .debounce { terms -> if (terms.isEmpty()) 0L else QUERY_DEBOUNCE_MILLIS }
        .flatMapLatest { terms ->
            if (terms.isEmpty()) {
                flowOf(SearchUiState())
            } else {
                searchDiaries(terms).map { diaries ->
                    SearchUiState(
                        hasQuery = true,
                        results = diaries.map { diary ->
                            diary.toSearchResult(terms, resolveMediaPath::invoke)
                        },
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SearchUiState(),
        )

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun clearQuery() {
        _query.value = ""
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** 打ち終わりを待つ時間。次の1文字が来ないと分かってから探し直す。 */
        const val QUERY_DEBOUNCE_MILLIS = 200L
    }
}

private fun Diary.toSearchResult(
    terms: List<String>,
    resolveMediaPath: (String) -> String,
): SearchResultUiState {
    val card = toCardUiState(resolveMediaPath)
    return SearchResultUiState(
        id = card.id,
        date = date,
        emoji = card.emoji,
        title = card.title,
        titleHighlights = card.title?.let { highlightRanges(it, terms) }.orEmpty(),
        // 抜粋は本文だけから作る。タイトルはカードに別で出ているので、
        // 抜粋にも混ぜると同じ文字が二度並ぶ
        snippet = buildSearchSnippet(bodyText(), terms),
        thumbnail = card.thumbnail,
        isDraft = card.isDraft,
        isFavorite = card.isFavorite,
    )
}
