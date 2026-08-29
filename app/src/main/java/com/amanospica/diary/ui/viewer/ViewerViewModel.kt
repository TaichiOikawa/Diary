package com.amanospica.diary.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.domain.model.DiaryEmoji
import com.amanospica.diary.domain.usecase.ObserveDiaryUseCase
import com.amanospica.diary.domain.usecase.ResolveMediaPathUseCase
import com.amanospica.diary.domain.usecase.SetFavoriteUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ViewerUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val emoji: String = DiaryEmoji.DEFAULT.emoji,
    val title: String? = null,
    val blocks: List<DiaryBlock> = emptyList(),
    /** 編集画面で下書きへ戻された日記もそのまま読めるので、印だけ出す。 */
    val isDraft: Boolean = false,
    /** お気に入り（星）が付いているか。上部バーの星の見た目に使う。 */
    val isFavorite: Boolean = false,
)

/** 画面を閉じるなどの一度きりの通知。 */
sealed interface ViewerEvent {
    data object Close : ViewerEvent
}

/**
 * 書き終わった日記を読むだけの画面の状態を持つ。
 *
 * 中身は購読しているので、鉛筆から編集画面へ移って書き直し、戻ってきたときも
 * そのまま新しい内容が出る。編集画面で日記が削除された場合は読むものが無いため画面を閉じる。
 */
class ViewerViewModel(
    val diaryId: String,
    observeDiary: ObserveDiaryUseCase,
    private val setFavorite: SetFavoriteUseCase,
    private val resolveMediaPath: ResolveMediaPathUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private val _events = Channel<ViewerEvent>(Channel.BUFFERED)
    val events: Flow<ViewerEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeDiary(diaryId).collect { diary ->
                if (diary == null) {
                    _events.send(ViewerEvent.Close)
                    return@collect
                }
                _uiState.value = ViewerUiState(
                    isLoading = false,
                    date = diary.date,
                    emoji = diary.emoji,
                    title = diary.title?.takeIf { it.isNotBlank() },
                    blocks = diary.blocks,
                    isDraft = diary.isDraft,
                    isFavorite = diary.isFavorite,
                )
            }
        }
    }

    /**
     * お気に入り（星）を付け外しする。
     *
     * 書き込むのは星だけなので、読んでいる本文はそのまま。
     * 画面の表示は購読している日記の更新に任せる。
     */
    fun toggleFavorite() {
        val next = !_uiState.value.isFavorite
        viewModelScope.launch { setFavorite(diaryId, next) }
    }

    fun resolvePath(relativePath: String): String = resolveMediaPath(relativePath)
}
