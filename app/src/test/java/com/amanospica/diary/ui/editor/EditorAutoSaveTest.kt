package com.amanospica.diary.ui.editor

import com.amanospica.diary.domain.model.CalendarDayMarker
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.domain.model.MonthlyCount
import com.amanospica.diary.domain.model.SavedMedia
import com.amanospica.diary.domain.model.SpanType
import com.amanospica.diary.domain.model.TextSpanAnnotation
import com.amanospica.diary.domain.repository.DiaryRepository
import com.amanospica.diary.domain.repository.MediaRepository
import com.amanospica.diary.domain.usecase.AttachImageUseCase
import com.amanospica.diary.domain.usecase.AttachVideoUseCase
import com.amanospica.diary.domain.usecase.CleanUpOrphanMediaUseCase
import com.amanospica.diary.domain.usecase.DeleteDiaryUseCase
import com.amanospica.diary.domain.usecase.GetDiaryUseCase
import com.amanospica.diary.domain.usecase.ResolveMediaPathUseCase
import com.amanospica.diary.domain.usecase.SaveDiaryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
 * 書きかけの日記が失われないための自動保存のふるまいを確かめる。
 *
 * 実際に「落ちる／ロックがかかる」状況は再現できないため、
 * 「入力が途切れたら DB に書かれているか」「その場保存が効くか」を境目として見る。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorAutoSaveTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeDiaryRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `入力が途切れると自動保存される`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onTextChange(viewModel.firstBlockId, "きょうの出来事", 7, 7)
        advanceUntilIdle()

        assertEquals("きょうの出来事", repository.saved.single().preview)
    }

    @Test
    fun `入力中は保存を待ち、止まってから1回だけ書き込む`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val blockId = viewModel.firstBlockId

        viewModel.onTextChange(blockId, "あ", 1, 1)
        advanceTimeBy(200)
        viewModel.onTextChange(blockId, "あい", 2, 2)
        advanceTimeBy(200)
        // まだ入力が続いているうちは書き込まない
        assertEquals(0, repository.saveCount)

        viewModel.onTextChange(blockId, "あいう", 3, 3)
        advanceUntilIdle()

        assertEquals(1, repository.saveCount)
        assertEquals("あいう", repository.saved.single().preview)
    }

    @Test
    fun `saveNow は待ち時間を待たずに書き込む`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onTextChange(viewModel.firstBlockId, "退避したい下書き", 8, 8)
        viewModel.saveNow()
        advanceTimeBy(50)

        assertEquals("退避したい下書き", repository.saved.single().preview)
    }

    @Test
    fun `自動保存した新規の日記を空にすると消える`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val blockId = viewModel.firstBlockId

        viewModel.onTextChange(blockId, "書きかけ", 4, 4)
        advanceUntilIdle()
        assertEquals(1, repository.saved.size)

        viewModel.onTextChange(blockId, "", 0, 0)
        advanceUntilIdle()

        assertEquals(emptyList<Diary>(), repository.saved)
    }

    @Test
    fun `既存の日記は空にしても消さない`() = runTest(dispatcher) {
        val existing = Diary(
            id = "existing",
            date = LocalDate.of(2026, 8, 10),
            emoji = "🙂",
            blocks = listOf(DiaryBlock.TextBlock(id = "body", text = "もとの本文")),
        )
        repository.saved += existing

        val viewModel = createViewModel(diaryId = existing.id)
        advanceUntilIdle()

        viewModel.onTextChange("body", "", 0, 0)
        advanceUntilIdle()

        assertEquals(existing, repository.saved.single())
    }

    @Test
    fun `星を付けた日記に書き足しても星は外れない`() = runTest(dispatcher) {
        repository.saved += Diary(
            id = "starred",
            date = LocalDate.of(2026, 8, 10),
            emoji = "🙂",
            blocks = listOf(DiaryBlock.TextBlock(id = "body", text = "もとの本文")),
            isFavorite = true,
        )

        val viewModel = createViewModel(diaryId = "starred")
        advanceUntilIdle()

        viewModel.onTextChange("body", "もとの本文と書き足し", 10, 10)
        advanceUntilIdle()

        val saved = repository.saved.single()
        assertEquals("もとの本文と書き足し", saved.preview)
        assertEquals(true, saved.isFavorite)
    }

    @Test
    fun `削除の直後に、待機していた自動保存が書き戻さない`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onTextChange(viewModel.firstBlockId, "消す日記", 4, 4)
        advanceUntilIdle()
        assertEquals(1, repository.saved.size)

        // 保存待ちを抱えたまま削除する
        viewModel.onTextChange(viewModel.firstBlockId, "消す日記です", 6, 6)
        viewModel.deleteAndClose()
        advanceUntilIdle()

        assertEquals(emptyList<Diary>(), repository.saved)
    }

    @Test
    fun `開いただけで何も編集しなければ書き込まない`() = runTest(dispatcher) {
        createViewModel()
        advanceUntilIdle()

        assertEquals(0, repository.saveCount)
    }

    @Test
    fun `改行してもブロックは増えず、1つの本文として保存される`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // 「いち」→ 空行 →「さん」と書く。Enter は本文の改行としてそのまま届く
        viewModel.type(viewModel.firstBlockId, "いち\n\nさん")
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.blocks.size)
        assertEquals(listOf("いち\n\nさん"), repository.saved.single().textBlockContents)
    }

    @Test
    fun `末尾の空行は保存対象から外れる`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.type(viewModel.firstBlockId, "ほんぶん\n\n")
        advanceUntilIdle()

        assertEquals(listOf("ほんぶん"), repository.saved.single().textBlockContents)
    }

    @Test
    fun `行ごとに分かれた古い日記は、開いた時点で1つの本文にまとまる`() = runTest(dispatcher) {
        // 改行でブロックを割っていた頃の保存形式（途中の空行も1ブロック）
        repository.saved += Diary(
            id = "legacy",
            date = LocalDate.of(2026, 8, 10),
            emoji = "🙂",
            blocks = listOf(
                DiaryBlock.TextBlock(text = "いち"),
                DiaryBlock.TextBlock(text = ""),
                DiaryBlock.TextBlock(text = "さん"),
            ),
        )

        val viewModel = createViewModel(diaryId = "legacy")
        advanceUntilIdle()

        assertEquals(listOf("いち\n\nさん"), viewModel.textBlockContents)
    }

    @Test
    fun `古い日記をまとめても、画像を挟んだ境目は残る`() = runTest(dispatcher) {
        repository.saved += Diary(
            id = "legacy",
            date = LocalDate.of(2026, 8, 10),
            emoji = "🙂",
            blocks = listOf(
                DiaryBlock.TextBlock(text = "まえ"),
                DiaryBlock.ImageBlock(localFilePath = "images/a.jpg"),
                DiaryBlock.TextBlock(text = "うしろ"),
                DiaryBlock.TextBlock(text = "つづき"),
            ),
        )

        val viewModel = createViewModel(diaryId = "legacy")
        advanceUntilIdle()

        val blocks = viewModel.uiState.value.blocks
        assertEquals(3, blocks.size)
        assertEquals(listOf("まえ", "うしろ\nつづき"), viewModel.textBlockContents)
        assertEquals(DiaryBlock.ImageBlock::class.java, blocks[1].javaClass)
    }

    @Test
    fun `古い日記の装飾は、まとめたあとの位置へずれる`() = runTest(dispatcher) {
        repository.saved += Diary(
            id = "legacy",
            date = LocalDate.of(2026, 8, 10),
            emoji = "🙂",
            blocks = listOf(
                // 「いち」全体が太字、「さん」は箇条書き（旧形式はブロック全体を覆う）
                DiaryBlock.TextBlock(
                    text = "いち",
                    spans = listOf(TextSpanAnnotation(0, 2, SpanType.BOLD)),
                ),
                DiaryBlock.TextBlock(
                    text = "さん",
                    spans = listOf(TextSpanAnnotation(0, 2, SpanType.LIST_ITEM)),
                ),
            ),
        )

        val viewModel = createViewModel(diaryId = "legacy")
        advanceUntilIdle()

        val body = viewModel.uiState.value.blocks.single() as DiaryBlock.TextBlock
        assertEquals("いち\nさん", body.text)
        assertEquals(
            listOf(
                TextSpanAnnotation(0, 2, SpanType.BOLD),
                TextSpanAnnotation(3, 5, SpanType.LIST_ITEM),
            ),
            body.spans,
        )
    }

    private fun createViewModel(diaryId: String? = null) = EditorViewModel(
        diaryId = diaryId,
        initialDate = LocalDate.of(2026, 8, 10),
        getDiary = GetDiaryUseCase(repository),
        saveDiary = SaveDiaryUseCase(repository),
        deleteDiary = DeleteDiaryUseCase(repository),
        attachImage = AttachImageUseCase(FakeMediaRepository),
        attachVideo = AttachVideoUseCase(FakeMediaRepository),
        cleanUpOrphanMedia = CleanUpOrphanMediaUseCase(FakeMediaRepository, repository),
        resolveMediaPath = ResolveMediaPathUseCase(FakeMediaRepository),
    )

    private val EditorViewModel.firstBlockId: String
        get() = uiState.value.blocks.first().id

    /** 画面に出ている本文を、テキストブロックの中身の並びとして見る。 */
    private val EditorViewModel.textBlockContents: List<String>
        get() = uiState.value.blocks.filterIsInstance<DiaryBlock.TextBlock>().map { it.text }

    /** 末尾にカーソルを置いたまま打ち込む。改行を含めれば Enter を押した1回ぶんになる。 */
    private fun EditorViewModel.type(blockId: String, text: String) =
        onTextChange(blockId, text, text.length, text.length)
}

/** 保存された本文を、テキストブロックの中身の並びとして見る。 */
private val Diary.textBlockContents: List<String>
    get() = blocks.filterIsInstance<DiaryBlock.TextBlock>().map { it.text }

/** 保存された日記をそのまま持つだけの置き換え実装。 */
private class FakeDiaryRepository : DiaryRepository {

    val saved = mutableListOf<Diary>()
    var saveCount = 0
        private set

    override suspend fun getDiary(id: String): Diary? = saved.firstOrNull { it.id == id }

    override suspend fun getAllDiaries(): List<Diary> = saved.toList()

    override suspend fun importDiaries(diaries: List<Diary>) = diaries.forEach { imported ->
        val index = saved.indexOfFirst { it.id == imported.id }
        if (index >= 0) saved[index] = imported else saved += imported
    }

    override suspend fun saveDiary(diary: Diary) {
        saveCount++
        val index = saved.indexOfFirst { it.id == diary.id }
        if (index >= 0) saved[index] = diary else saved += diary
    }

    override suspend fun setFavorite(id: String, isFavorite: Boolean) {
        val index = saved.indexOfFirst { it.id == id }
        if (index >= 0) saved[index] = saved[index].copy(isFavorite = isFavorite)
    }

    override suspend fun deleteDiary(id: String) {
        saved.removeAll { it.id == id }
    }

    override suspend fun getReferencedMediaPaths(): Set<String> = emptySet()

    override fun observeTimeline(): Flow<List<Diary>> = flowOf(saved)
    override fun observeByDate(date: LocalDate): Flow<List<Diary>> = flowOf(saved)
    override fun observeMonths(start: YearMonth, end: YearMonth): Flow<List<CalendarDayMarker>> =
        flowOf(emptyList())
    override fun observeDiary(id: String): Flow<Diary?> = flowOf(saved.firstOrNull { it.id == id })
    override fun observeTotalCount(): Flow<Int> = flowOf(saved.size)
    override fun observeRecordedDates(): Flow<List<LocalDate>> = flowOf(saved.map { it.date })
    override fun observeEmojiCounts(): Flow<Map<String, Int>> = flowOf(emptyMap())
    override fun observeMonthlyCounts(): Flow<List<MonthlyCount>> = flowOf(emptyList())
}

private object FakeMediaRepository : MediaRepository {
    override suspend fun saveImage(sourceUri: String): Result<SavedMedia> =
        Result.failure(UnsupportedOperationException())

    override suspend fun saveVideo(sourceUri: String): Result<SavedMedia> =
        Result.failure(UnsupportedOperationException())

    override fun resolveAbsolutePath(relativePath: String): String = relativePath
    override suspend fun delete(relativePath: String) = Unit
    override suspend fun deleteOrphans(referencedPaths: Set<String>): Int = 0
}
