package com.amanospica.diary.ui.timeline

import com.amanospica.diary.domain.model.CalendarDayMarker
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.domain.model.MonthlyCount
import com.amanospica.diary.domain.model.SavedMedia
import com.amanospica.diary.domain.repository.DiaryRepository
import com.amanospica.diary.domain.repository.MediaRepository
import com.amanospica.diary.domain.usecase.DeleteDiaryUseCase
import com.amanospica.diary.domain.usecase.ObserveTimelineUseCase
import com.amanospica.diary.domain.usecase.ResolveMediaPathUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * タイムラインの「お気に入りだけ表示」の絞り込みを確かめる。
 *
 * 絞り込みは購読済みの一覧に掛けるので、星の付け外しがそのまま並びへ届くこと、
 * 隠れた日記が選択に残らないことが要点。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineFavoritesTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeTimelineRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `既定ではすべての日記が並ぶ`() = runTest(dispatcher) {
        repository.diaries.value = listOf(diary("a", isFavorite = true), diary("b"))
        val viewModel = createViewModel()

        assertEquals(listOf("a", "b"), viewModel.visibleIds)
    }

    @Test
    fun `絞り込むと星を付けた日記だけが並ぶ`() = runTest(dispatcher) {
        repository.diaries.value = listOf(diary("a", isFavorite = true), diary("b"))
        val viewModel = createViewModel()

        viewModel.toggleFavoritesOnly()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isFavoritesOnly)
        assertEquals(listOf("a"), viewModel.visibleIds)
    }

    @Test
    fun `絞り込みをやめるとすべて戻る`() = runTest(dispatcher) {
        repository.diaries.value = listOf(diary("a", isFavorite = true), diary("b"))
        val viewModel = createViewModel()

        viewModel.toggleFavoritesOnly()
        viewModel.toggleFavoritesOnly()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isFavoritesOnly)
        assertEquals(listOf("a", "b"), viewModel.visibleIds)
    }

    @Test
    fun `絞り込み中に星が外れると一覧からも消える`() = runTest(dispatcher) {
        repository.diaries.value = listOf(diary("a", isFavorite = true), diary("b"))
        val viewModel = createViewModel()
        viewModel.toggleFavoritesOnly()
        advanceUntilIdle()

        repository.diaries.value = listOf(diary("a"), diary("b"))
        advanceUntilIdle()

        assertEquals(emptyList<String>(), viewModel.visibleIds)
        assertEquals(true, viewModel.uiState.value.isEmpty)
    }

    @Test
    fun `絞り込みを切り替えると選択はやめる`() = runTest(dispatcher) {
        repository.diaries.value = listOf(diary("a", isFavorite = true), diary("b"))
        val viewModel = createViewModel()

        // 星の付いていない日記を選んだまま絞り込むと、見えない日記が選択に残ってしまう
        viewModel.toggleSelection("b")
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.isSelectionMode)

        viewModel.toggleFavoritesOnly()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isSelectionMode)
    }

    /** 画面に出ている日記の ID を、年の区切りをまたいで並び順どおりに見る。 */
    private val TimelineViewModel.visibleIds: List<String>
        get() = uiState.value.sections.flatMap { section -> section.entries.map { it.id } }

    /** [TimelineViewModel.uiState] は購読されている間だけ流れるため、裏で collect し続ける。 */
    private fun TestScope.createViewModel(): TimelineViewModel {
        val viewModel = TimelineViewModel(
            observeTimeline = ObserveTimelineUseCase(repository),
            deleteDiary = DeleteDiaryUseCase(repository),
            resolveMediaPath = ResolveMediaPathUseCase(FakeMedia),
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    private fun diary(id: String, isFavorite: Boolean = false) = Diary(
        id = id,
        date = LocalDate.of(2026, 8, 10),
        emoji = "🙂",
        blocks = listOf(DiaryBlock.TextBlock(text = "本文")),
        isFavorite = isFavorite,
    )
}

/** タイムラインの一覧だけを流す置き換え実装。 */
private class FakeTimelineRepository : DiaryRepository {

    val diaries = MutableStateFlow<List<Diary>>(emptyList())

    override fun observeTimeline(): Flow<List<Diary>> = diaries

    override suspend fun setFavorite(id: String, isFavorite: Boolean) {
        diaries.value = diaries.value.map {
            if (it.id == id) it.copy(isFavorite = isFavorite) else it
        }
    }

    override suspend fun deleteDiary(id: String) {
        diaries.value = diaries.value.filterNot { it.id == id }
    }

    override suspend fun getDiary(id: String): Diary? = diaries.value.firstOrNull { it.id == id }
    override suspend fun getAllDiaries(): List<Diary> = diaries.value
    override suspend fun saveDiary(diary: Diary) = Unit
    override suspend fun importDiaries(diaries: List<Diary>) = Unit
    override suspend fun getReferencedMediaPaths(): Set<String> = emptySet()
    override fun observeByDate(date: LocalDate): Flow<List<Diary>> = diaries
    override fun observeMonths(start: YearMonth, end: YearMonth): Flow<List<CalendarDayMarker>> =
        flowOf(emptyList())

    override fun observeDiary(id: String): Flow<Diary?> =
        diaries.map { list -> list.firstOrNull { it.id == id } }

    override fun observeTotalCount(): Flow<Int> = diaries.map { it.size }
    override fun observeRecordedDates(): Flow<List<LocalDate>> = diaries.map { it.map { d -> d.date } }
    override fun observeEmojiCounts(): Flow<Map<String, Int>> = flowOf(emptyMap())
    override fun observeMonthlyCounts(): Flow<List<MonthlyCount>> = flowOf(emptyList())
}

private object FakeMedia : MediaRepository {
    override suspend fun saveImage(sourceUri: String): Result<SavedMedia> =
        Result.failure(UnsupportedOperationException())

    override suspend fun saveVideo(sourceUri: String): Result<SavedMedia> =
        Result.failure(UnsupportedOperationException())

    override fun resolveAbsolutePath(relativePath: String): String = relativePath
    override suspend fun delete(relativePath: String) = Unit
    override suspend fun deleteOrphans(referencedPaths: Set<String>): Int = 0
}
