package com.amanospica.diary.ui.search

import com.amanospica.diary.domain.model.CalendarDayMarker
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.domain.model.MonthlyCount
import com.amanospica.diary.domain.model.SavedMedia
import com.amanospica.diary.domain.repository.DiaryRepository
import com.amanospica.diary.domain.repository.MediaRepository
import com.amanospica.diary.domain.usecase.ResolveMediaPathUseCase
import com.amanospica.diary.domain.usecase.SearchDiariesUseCase
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
 * 検索画面の状態づくりを確かめる。
 *
 * 入力欄はすぐ追いつくが結果は少し待ってから作り直すこと、
 * 未入力のときに「0件」ではなく案内の状態でいることが要点。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSearchRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `未入力のうちは案内の状態`() = runTest(dispatcher) {
        repository.diaries.value = listOf(diary("a", body = "海までドライブ"))
        val viewModel = createViewModel()

        assertEquals(false, viewModel.uiState.value.hasQuery)
        assertEquals(false, viewModel.uiState.value.isEmpty)
        assertEquals(emptyList<String>(), viewModel.resultIds)
    }

    @Test
    fun `打った文字はすぐ入力欄へ反映される`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onQueryChange("海")

        // 結果を待たせている間も、打った文字は出したままにする
        assertEquals("海", viewModel.query.value)
    }

    @Test
    fun `打ち終わると一致した日記が並ぶ`() = runTest(dispatcher) {
        repository.diaries.value = listOf(
            diary("a", body = "海までドライブ"),
            diary("b", body = "山に登った"),
        )
        val viewModel = createViewModel()

        viewModel.onQueryChange("海")
        advanceUntilIdle()

        assertEquals(listOf("a"), viewModel.resultIds)
        assertEquals(true, viewModel.uiState.value.hasQuery)
    }

    @Test
    fun `見つからなければ空の結果になる`() = runTest(dispatcher) {
        repository.diaries.value = listOf(diary("a", body = "海までドライブ"))
        val viewModel = createViewModel()

        viewModel.onQueryChange("宇宙")
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isEmpty)
    }

    @Test
    fun `検索語を消すと案内の状態へ戻る`() = runTest(dispatcher) {
        repository.diaries.value = listOf(diary("a", body = "海までドライブ"))
        val viewModel = createViewModel()
        viewModel.onQueryChange("海")
        advanceUntilIdle()

        viewModel.clearQuery()
        advanceUntilIdle()

        assertEquals("", viewModel.query.value)
        assertEquals(false, viewModel.uiState.value.hasQuery)
        assertEquals(emptyList<String>(), viewModel.resultIds)
    }

    @Test
    fun `結果に一致箇所の抜粋が付く`() = runTest(dispatcher) {
        repository.diaries.value = listOf(
            diary("a", title = "海の日", body = "朝から海までドライブした"),
        )
        val viewModel = createViewModel()

        viewModel.onQueryChange("ドライブ")
        advanceUntilIdle()

        val result = viewModel.uiState.value.results.single()
        assertEquals("朝から海までドライブした", result.snippet.text)
        assertEquals(listOf(6..9), result.snippet.highlights)
        // タイトルに一致が無ければ、タイトル側は光らせない
        assertEquals(emptyList<IntRange>(), result.titleHighlights)
    }

    @Test
    fun `タイトルの一致箇所も光る`() = runTest(dispatcher) {
        repository.diaries.value = listOf(diary("a", title = "海の日", body = "よく晴れた"))
        val viewModel = createViewModel()

        viewModel.onQueryChange("海")
        advanceUntilIdle()

        assertEquals(listOf(0..0), viewModel.uiState.value.results.single().titleHighlights)
    }

    @Test
    fun `日記を消すと検索結果からも消える`() = runTest(dispatcher) {
        repository.diaries.value = listOf(diary("a", body = "海までドライブ"))
        val viewModel = createViewModel()
        viewModel.onQueryChange("海")
        advanceUntilIdle()
        assertEquals(listOf("a"), viewModel.resultIds)

        repository.diaries.value = emptyList()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), viewModel.resultIds)
    }

    private val SearchViewModel.resultIds: List<String>
        get() = uiState.value.results.map { it.id }

    /** [SearchViewModel.uiState] は購読されている間だけ流れるため、裏で collect し続ける。 */
    private fun TestScope.createViewModel(): SearchViewModel {
        val viewModel = SearchViewModel(
            searchDiaries = SearchDiariesUseCase(repository),
            resolveMediaPath = ResolveMediaPathUseCase(FakeMedia),
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    private fun diary(id: String, title: String? = null, body: String = "") = Diary(
        id = id,
        date = LocalDate.of(2026, 8, 10),
        emoji = "🙂",
        title = title,
        blocks = listOf(DiaryBlock.TextBlock(text = body)),
    )
}

/** タイムラインの一覧だけを流す置き換え実装。 */
private class FakeSearchRepository : DiaryRepository {

    val diaries = MutableStateFlow<List<Diary>>(emptyList())

    override fun observeTimeline(): Flow<List<Diary>> = diaries

    override suspend fun getDiary(id: String): Diary? = diaries.value.firstOrNull { it.id == id }
    override suspend fun getAllDiaries(): List<Diary> = diaries.value
    override suspend fun saveDiary(diary: Diary) = Unit
    override suspend fun importDiaries(diaries: List<Diary>) = Unit
    override suspend fun setFavorite(id: String, isFavorite: Boolean) = Unit
    override suspend fun deleteDiary(id: String) = Unit
    override suspend fun getReferencedMediaPaths(): Set<String> = emptySet()
    override fun observeByDate(date: LocalDate): Flow<List<Diary>> = diaries
    override fun observeMonths(start: YearMonth, end: YearMonth): Flow<List<CalendarDayMarker>> =
        flowOf(emptyList())

    override fun observeDiary(id: String): Flow<Diary?> =
        diaries.map { list -> list.firstOrNull { it.id == id } }

    override fun observeTotalCount(): Flow<Int> = diaries.map { it.size }
    override fun observeRecordedDates(): Flow<List<LocalDate>> =
        diaries.map { list -> list.map { it.date } }

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
