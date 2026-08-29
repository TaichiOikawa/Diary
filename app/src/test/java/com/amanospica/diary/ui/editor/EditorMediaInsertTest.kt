package com.amanospica.diary.ui.editor

import com.amanospica.diary.domain.model.CalendarDayMarker
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.domain.model.MonthlyCount
import com.amanospica.diary.domain.model.SavedMedia
import com.amanospica.diary.domain.repository.DiaryRepository
import com.amanospica.diary.domain.repository.MediaRepository
import com.amanospica.diary.domain.usecase.AttachImageUseCase
import com.amanospica.diary.domain.usecase.AttachVideoUseCase
import com.amanospica.diary.domain.usecase.CleanUpOrphanMediaUseCase
import com.amanospica.diary.domain.usecase.DeleteDiaryUseCase
import com.amanospica.diary.domain.usecase.GetDiaryUseCase
import com.amanospica.diary.domain.usecase.ResolveMediaPathUseCase
import com.amanospica.diary.domain.usecase.SaveDiaryUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth

/**
 * まとめて選んだ写真が、選んだ順どおりにカーソル位置へ並ぶことを確かめる。
 *
 * 1枚ずつ差し込むと画像とテキストが交互に増えてしまうため、
 * 「本文が割れるのは1か所だけか」を境目として見る。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorMediaInsertTest {

    private val dispatcher = StandardTestDispatcher()
    private val diaryRepository = StubDiaryStore()
    private val mediaRepository = StubMediaStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `複数の写真がカーソル位置へ選んだ順に並ぶ`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val blockId = viewModel.firstBlockId

        // 「まえ」と「うしろ」の境目にカーソルを置く
        viewModel.onTextChange(blockId, "まえうしろ", 2, 2)
        viewModel.insertImages(listOf("uri-a", "uri-b", "uri-c"))
        advanceUntilIdle()

        val blocks = viewModel.uiState.value.blocks
        assertEquals("まえ", (blocks[0] as DiaryBlock.TextBlock).text)
        assertEquals(
            listOf("images/uri-a.jpg", "images/uri-b.jpg", "images/uri-c.jpg"),
            blocks.subList(1, 4).map { (it as DiaryBlock.ImageBlock).localFilePath },
        )
        assertEquals("うしろ", (blocks[4] as DiaryBlock.TextBlock).text)
        assertEquals(5, blocks.size)
    }

    @Test
    fun `カーソルが無いときは本文の末尾へまとめて置かれる`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.insertImages(listOf("uri-a", "uri-b"))
        advanceUntilIdle()

        val blocks = viewModel.uiState.value.blocks
        assertEquals(
            listOf("images/uri-a.jpg", "images/uri-b.jpg"),
            blocks.filterIsInstance<DiaryBlock.ImageBlock>().map { it.localFilePath },
        )
        // 続きを書けるよう、末尾はテキストブロックで終わる
        assertEquals(DiaryBlock.TextBlock::class.java, blocks.last().javaClass)
    }

    @Test
    fun `一部が失敗しても取り込めた写真は残り、件数が知らされる`() = runTest(dispatcher) {
        mediaRepository.failingUris += "uri-b"
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.insertImages(listOf("uri-a", "uri-b", "uri-c"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            listOf("images/uri-a.jpg", "images/uri-c.jpg"),
            state.blocks.filterIsInstance<DiaryBlock.ImageBlock>().map { it.localFilePath },
        )
        assertEquals("1件のメディアを取り込めませんでした", state.errorMessage)
    }

    @Test
    fun `1枚だけ失敗したときは原因をそのまま知らせる`() = runTest(dispatcher) {
        mediaRepository.failingUris += "uri-a"
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.insertImages(listOf("uri-a"))
        advanceUntilIdle()

        assertEquals("読み込めません: uri-a", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `取り込みが終わるまで取り込み中のままになる`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // 圧縮に時間がかかる状況を模して、取り込みを途中で止める
        val gate = CompletableDeferred<Unit>()
        mediaRepository.gate = gate

        viewModel.insertImages(listOf("uri-a", "uri-b"))
        runCurrent()
        assertEquals(true, viewModel.uiState.value.isAttachingMedia)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isAttachingMedia)
        assertEquals(2, viewModel.uiState.value.blocks.filterIsInstance<DiaryBlock.ImageBlock>().size)
    }

    @Test
    fun `写真を選ばずに閉じても何も起きない`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val before = viewModel.uiState.value.blocks

        viewModel.insertImages(emptyList())
        advanceUntilIdle()

        assertEquals(before, viewModel.uiState.value.blocks)
        assertNull(viewModel.uiState.value.errorMessage)
        assertNotNull(viewModel.uiState.value.blocks.firstOrNull())
    }

    private fun createViewModel() = EditorViewModel(
        diaryId = null,
        initialDate = LocalDate.of(2026, 8, 10),
        getDiary = GetDiaryUseCase(diaryRepository),
        saveDiary = SaveDiaryUseCase(diaryRepository),
        deleteDiary = DeleteDiaryUseCase(diaryRepository),
        attachImage = AttachImageUseCase(mediaRepository),
        attachVideo = AttachVideoUseCase(mediaRepository),
        cleanUpOrphanMedia = CleanUpOrphanMediaUseCase(mediaRepository, diaryRepository),
        resolveMediaPath = ResolveMediaPathUseCase(mediaRepository),
    )

    private val EditorViewModel.firstBlockId: String
        get() = uiState.value.blocks.first().id
}

/**
 * 取り込みを模した置き換え実装。
 * [failingUris] に入れた URI だけ失敗し、[gate] を挟むと取り込みを途中で止められる。
 */
private class StubMediaStore : MediaRepository {

    val failingUris = mutableSetOf<String>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun saveImage(sourceUri: String): Result<SavedMedia> =
        save(sourceUri, "images/$sourceUri.jpg")

    override suspend fun saveVideo(sourceUri: String): Result<SavedMedia> =
        save(sourceUri, "videos/$sourceUri.mp4")

    private suspend fun save(sourceUri: String, relativePath: String): Result<SavedMedia> {
        gate?.await()
        return result(sourceUri, relativePath)
    }

    private fun result(sourceUri: String, relativePath: String): Result<SavedMedia> =
        if (sourceUri in failingUris) {
            Result.failure(IOException("読み込めません: $sourceUri"))
        } else {
            Result.success(
                SavedMedia(
                    relativePath = relativePath,
                    absolutePath = "/media/$relativePath",
                    sizeBytes = 1,
                )
            )
        }

    override fun resolveAbsolutePath(relativePath: String): String = relativePath
    override suspend fun delete(relativePath: String) = Unit
    override suspend fun deleteOrphans(referencedPaths: Set<String>): Int = 0
}

/** 保存された日記をそのまま持つだけの置き換え実装。 */
private class StubDiaryStore : DiaryRepository {

    private val saved = mutableListOf<Diary>()

    override suspend fun getDiary(id: String): Diary? = saved.firstOrNull { it.id == id }

    override suspend fun getAllDiaries(): List<Diary> = saved.toList()

    override suspend fun importDiaries(diaries: List<Diary>) = diaries.forEach { imported ->
        val index = saved.indexOfFirst { it.id == imported.id }
        if (index >= 0) saved[index] = imported else saved += imported
    }

    override suspend fun saveDiary(diary: Diary) {
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
