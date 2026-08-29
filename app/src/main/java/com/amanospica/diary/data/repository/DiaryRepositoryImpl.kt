package com.amanospica.diary.data.repository

import com.amanospica.diary.data.local.converter.DiaryTypeConverters
import com.amanospica.diary.data.local.dao.DiaryDao
import com.amanospica.diary.data.mapper.toDbDate
import com.amanospica.diary.data.mapper.toDomain
import com.amanospica.diary.data.mapper.toEntity
import com.amanospica.diary.data.mapper.toLocalDate
import com.amanospica.diary.domain.model.CalendarDayMarker
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.MonthlyCount
import com.amanospica.diary.domain.model.mediaFilePaths
import com.amanospica.diary.domain.repository.DiaryRepository
import com.amanospica.diary.domain.repository.MediaRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

class DiaryRepositoryImpl(
    private val dao: DiaryDao,
    private val mediaRepository: MediaRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DiaryRepository {

    private val converters = DiaryTypeConverters()

    override fun observeTimeline(): Flow<List<Diary>> =
        dao.observeTimeline().map { rows -> rows.map { it.toDomain() } }.flowOn(ioDispatcher)

    override fun observeByDate(date: LocalDate): Flow<List<Diary>> =
        dao.observeByDate(date.toDbDate())
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeMonths(start: YearMonth, end: YearMonth): Flow<List<CalendarDayMarker>> =
        dao.observeDayMarkers(
            startDate = start.atDay(1).toDbDate(),
            endDate = end.atEndOfMonth().toDbDate(),
        ).map { rows -> rows.map { it.toDomain() } }.flowOn(ioDispatcher)

    override fun observeDiary(id: String): Flow<Diary?> =
        dao.observeById(id).map { it?.toDomain() }.flowOn(ioDispatcher)

    override suspend fun getDiary(id: String): Diary? = withContext(ioDispatcher) {
        dao.findById(id)?.toDomain()
    }

    override suspend fun getAllDiaries(): List<Diary> = withContext(ioDispatcher) {
        dao.findAll().map { it.toDomain() }
    }

    override suspend fun importDiaries(diaries: List<Diary>) = withContext(ioDispatcher) {
        dao.upsertAll(diaries.map { it.toEntity() })
    }

    override suspend fun saveDiary(diary: Diary) = withContext(ioDispatcher) {
        val previousMedia = dao.findById(diary.id)?.blocks?.mediaFilePaths().orEmpty()
        dao.upsert(diary.toEntity())

        // 編集で本文から取り除かれた画像・動画の実ファイルを回収する
        val currentMedia = diary.blocks.mediaFilePaths().toSet()
        previousMedia.filterNot { it in currentMedia }.forEach { mediaRepository.delete(it) }
    }

    override suspend fun setFavorite(id: String, isFavorite: Boolean) = withContext(ioDispatcher) {
        dao.updateFavorite(id, isFavorite)
    }

    override suspend fun deleteDiary(id: String) = withContext(ioDispatcher) {
        val target = dao.findById(id) ?: return@withContext
        dao.deleteById(id)
        target.blocks.mediaFilePaths().forEach { mediaRepository.delete(it) }
    }

    override suspend fun getReferencedMediaPaths(): Set<String> = withContext(ioDispatcher) {
        dao.findAllBlocksJson()
            .flatMap { converters.jsonToBlocks(it).mediaFilePaths() }
            .toSet()
    }

    override fun observeTotalCount(): Flow<Int> = dao.observeTotalCount().flowOn(ioDispatcher)

    override fun observeRecordedDates(): Flow<List<LocalDate>> =
        dao.observeRecordedDates()
            .map { dates -> dates.map { it.toLocalDate() } }
            .flowOn(ioDispatcher)

    override fun observeEmojiCounts(): Flow<Map<String, Int>> =
        dao.observeEmojiCounts()
            .map { rows -> rows.associate { it.emoji to it.entryCount } }
            .flowOn(ioDispatcher)

    override fun observeMonthlyCounts(): Flow<List<MonthlyCount>> =
        dao.observeMonthlyCounts()
            .map { rows -> rows.map { MonthlyCount(YearMonth.parse(it.yearMonth), it.entryCount) } }
            .flowOn(ioDispatcher)
}
