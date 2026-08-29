package com.amanospica.diary.domain.usecase

import com.amanospica.diary.domain.model.ArchiveContents
import com.amanospica.diary.domain.model.BackupException
import com.amanospica.diary.domain.model.CalendarDayMarker
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.domain.model.ExportSummary
import com.amanospica.diary.domain.model.MonthlyCount
import com.amanospica.diary.domain.model.SavedMedia
import com.amanospica.diary.domain.repository.BackupRepository
import com.amanospica.diary.domain.repository.DiaryRepository
import com.amanospica.diary.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * バックアップ読み込みの突き合わせ方を確かめる。
 *
 * 要点は、端末側の日記を消さないこと、同じ日記なら新しい方が残ること、
 * そして読み込みで浮いた写真を必ず掃除すること。
 */
class ImportDiariesUseCaseTest {

    private val backupRepository = FakeBackupRepository()
    private val diaryRepository = FakeDiaryStore()
    private val mediaRepository = CountingMediaRepository()
    private val importDiaries = ImportDiariesUseCase(
        backupRepository = backupRepository,
        diaryRepository = diaryRepository,
        cleanUpOrphanMedia = CleanUpOrphanMediaUseCase(mediaRepository, diaryRepository),
    )

    @Test
    fun `端末に無い日記は追加される`() = runTest {
        backupRepository.contents = contentsOf(diary("a", updatedAt = 100))

        val summary = importDiaries(SOURCE_URI).getOrThrow()

        assertEquals(1, summary.added)
        assertEquals(0, summary.updated)
        assertEquals(0, summary.skipped)
        assertEquals(listOf("a"), diaryRepository.imported.map { it.id })
    }

    @Test
    fun `同じ日記はバックアップの方が新しければ置き換わる`() = runTest {
        diaryRepository.stored += diary("a", updatedAt = 100, body = "端末側の本文")
        backupRepository.contents = contentsOf(diary("a", updatedAt = 200, body = "バックアップの本文"))

        val summary = importDiaries(SOURCE_URI).getOrThrow()

        assertEquals(0, summary.added)
        assertEquals(1, summary.updated)
        assertEquals(0, summary.skipped)
        assertEquals(listOf("バックアップの本文"), diaryRepository.imported.map { it.preview })
    }

    @Test
    fun `端末側が新しければ触らない`() = runTest {
        diaryRepository.stored += diary("a", updatedAt = 200, body = "書きたての本文")
        backupRepository.contents = contentsOf(diary("a", updatedAt = 100, body = "古いバックアップ"))

        val summary = importDiaries(SOURCE_URI).getOrThrow()

        assertEquals(0, summary.added)
        assertEquals(0, summary.updated)
        assertEquals(1, summary.skipped)
        // 古い内容で上書きされて書きたてが巻き戻らないこと
        assertEquals(emptyList<String>(), diaryRepository.imported.map { it.id })
    }

    @Test
    fun `更新日時が同じなら端末側を残す`() = runTest {
        diaryRepository.stored += diary("a", updatedAt = 100)
        backupRepository.contents = contentsOf(diary("a", updatedAt = 100))

        val summary = importDiaries(SOURCE_URI).getOrThrow()

        assertEquals(1, summary.skipped)
        assertEquals(emptyList<String>(), diaryRepository.imported.map { it.id })
    }

    @Test
    fun `追加と更新と据え置きが混ざっても数え分けられる`() = runTest {
        diaryRepository.stored += listOf(diary("old", updatedAt = 100), diary("mine", updatedAt = 300))
        backupRepository.contents = contentsOf(
            diary("new", updatedAt = 100),
            diary("old", updatedAt = 200),
            diary("mine", updatedAt = 100),
        )

        val summary = importDiaries(SOURCE_URI).getOrThrow()

        assertEquals(1, summary.added)
        assertEquals(1, summary.updated)
        assertEquals(1, summary.skipped)
        assertEquals(listOf("new", "old"), diaryRepository.imported.map { it.id })
    }

    @Test
    fun `読み込みの後に孤児メディアを掃除する`() = runTest {
        // ZIP 内の写真は日記より先に復元されるので、採用しなかった日記の分が浮いたままになる
        diaryRepository.stored += diary("a", updatedAt = 200)
        backupRepository.contents = contentsOf(diary("a", updatedAt = 100))

        importDiaries(SOURCE_URI).getOrThrow()

        assertEquals(1, mediaRepository.orphanCleanUpCount)
    }

    @Test
    fun `復元した写真の件数はそのまま結果に出る`() = runTest {
        backupRepository.contents = ArchiveContents(diaries = emptyList(), mediaRestored = 3)

        assertEquals(3, importDiaries(SOURCE_URI).getOrThrow().mediaRestored)
    }

    @Test
    fun `ファイルが読めなければDBには何も書かない`() = runTest {
        backupRepository.failure = BackupException(BackupException.Reason.NOT_A_BACKUP)

        val result = importDiaries(SOURCE_URI)

        assertTrue(result.isFailure)
        assertEquals(
            BackupException.Reason.NOT_A_BACKUP,
            (result.exceptionOrNull() as BackupException).reason,
        )
        assertEquals(emptyList<Diary>(), diaryRepository.imported)
        assertEquals(0, mediaRepository.orphanCleanUpCount)
    }

    private fun contentsOf(vararg diaries: Diary) =
        ArchiveContents(diaries = diaries.toList(), mediaRestored = 0)

    private fun diary(id: String, updatedAt: Long, body: String = "本文") = Diary(
        id = id,
        date = LocalDate.of(2026, 8, 13),
        emoji = "🙂",
        blocks = listOf(DiaryBlock.TextBlock(text = body)),
        createdAt = 50L,
        updatedAt = updatedAt,
    )

    private companion object {
        const val SOURCE_URI = "content://backup.zip"
    }
}

/** 読み出す中身をテストから差し込めるようにした置き換え実装。 */
private class FakeBackupRepository : BackupRepository {

    var contents: ArchiveContents = ArchiveContents(emptyList(), 0)
    var failure: BackupException? = null

    override suspend fun exportTo(
        destinationUri: String,
        diaries: List<Diary>,
    ): Result<ExportSummary> = Result.success(ExportSummary(diaries.size, 0))

    override suspend fun readArchive(sourceUri: String): Result<ArchiveContents> =
        failure?.let { Result.failure(it) } ?: Result.success(contents)
}

/** 既にある日記を持ち、読み込みで書き込まれた分を記録するだけの置き換え実装。 */
private class FakeDiaryStore : DiaryRepository {

    val stored = mutableListOf<Diary>()
    val imported = mutableListOf<Diary>()

    override suspend fun getAllDiaries(): List<Diary> = stored.toList()

    override suspend fun importDiaries(diaries: List<Diary>) {
        imported += diaries
        diaries.forEach { incoming ->
            val index = stored.indexOfFirst { it.id == incoming.id }
            if (index >= 0) stored[index] = incoming else stored += incoming
        }
    }

    override suspend fun getReferencedMediaPaths(): Set<String> =
        stored.flatMap { it.mediaPaths }.toSet()

    override suspend fun getDiary(id: String): Diary? = stored.firstOrNull { it.id == id }
    override suspend fun saveDiary(diary: Diary) = Unit
    override suspend fun setFavorite(id: String, isFavorite: Boolean) = Unit
    override suspend fun deleteDiary(id: String) = Unit
    override fun observeTimeline(): Flow<List<Diary>> = flowOf(stored)
    override fun observeByDate(date: LocalDate): Flow<List<Diary>> = flowOf(stored)
    override fun observeDiary(id: String): Flow<Diary?> = flowOf(getDiaryOrNull(id))
    override fun observeMonths(start: YearMonth, end: YearMonth): Flow<List<CalendarDayMarker>> =
        flowOf(emptyList())

    override fun observeTotalCount(): Flow<Int> = flowOf(stored.size)
    override fun observeRecordedDates(): Flow<List<LocalDate>> = flowOf(stored.map { it.date })
    override fun observeEmojiCounts(): Flow<Map<String, Int>> = flowOf(emptyMap())
    override fun observeMonthlyCounts(): Flow<List<MonthlyCount>> = flowOf(emptyList())

    private fun getDiaryOrNull(id: String): Diary? = stored.firstOrNull { it.id == id }
}

/** 孤児メディアの掃除が呼ばれた回数だけを数える置き換え実装。 */
private class CountingMediaRepository : MediaRepository {

    var orphanCleanUpCount = 0
        private set

    override suspend fun deleteOrphans(referencedPaths: Set<String>): Int {
        orphanCleanUpCount++
        return 0
    }

    override suspend fun saveImage(sourceUri: String): Result<SavedMedia> =
        Result.failure(UnsupportedOperationException())

    override suspend fun saveVideo(sourceUri: String): Result<SavedMedia> =
        Result.failure(UnsupportedOperationException())

    override fun resolveAbsolutePath(relativePath: String): String = relativePath
    override suspend fun delete(relativePath: String) = Unit
}
