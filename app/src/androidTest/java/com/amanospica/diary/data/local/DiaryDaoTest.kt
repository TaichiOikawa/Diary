package com.amanospica.diary.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amanospica.diary.data.local.dao.DiaryDao
import com.amanospica.diary.data.local.entity.DiaryEntity
import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.domain.model.SpanType
import com.amanospica.diary.domain.model.TextSpanAnnotation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiaryDaoTest {

    private lateinit var database: DiaryDatabase
    private lateinit var dao: DiaryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DiaryDatabase::class.java,
        ).build()
        dao = database.diaryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun blocksSurviveTheTypeConverterRoundTrip() = runTest {
        val blocks = listOf(
            DiaryBlock.TextBlock(
                id = "t1",
                text = "見出しつきの本文",
                spans = listOf(TextSpanAnnotation(0, 3, SpanType.HEADING)),
            ),
            DiaryBlock.ImageBlock(id = "i1", localFilePath = "images/photo.jpg", caption = "海"),
            DiaryBlock.VideoBlock(id = "v1", localFilePath = "videos/clip.mp4"),
        )
        dao.upsert(entity(id = "d1", date = "2026-08-02", blocks = blocks))

        assertEquals(blocks, dao.findById("d1")?.blocks)
    }

    @Test
    fun timelineIsOrderedByDateThenCreatedAtDescending() = runTest {
        dao.upsert(entity(id = "old", date = "2026-07-30", createdAt = 100))
        dao.upsert(entity(id = "sameDayFirst", date = "2026-08-02", createdAt = 200))
        dao.upsert(entity(id = "sameDayLatest", date = "2026-08-02", createdAt = 300))

        val ids = dao.observeTimeline().first().map { it.id }

        assertEquals(listOf("sameDayLatest", "sameDayFirst", "old"), ids)
    }

    @Test
    fun dayMarkerUsesTheLatestEntryEmojiAndCountsAllEntries() = runTest {
        dao.upsert(entity(id = "a", date = "2026-08-02", emoji = "😢", createdAt = 100))
        dao.upsert(entity(id = "b", date = "2026-08-02", emoji = "😊", createdAt = 500))
        dao.upsert(entity(id = "c", date = "2026-08-05", emoji = "🎉", createdAt = 900))
        // 月をまたぐエントリーは対象外
        dao.upsert(entity(id = "d", date = "2026-09-01", emoji = "✈️", createdAt = 999))

        val markers = dao.observeDayMarkers("2026-08-01", "2026-08-31").first()

        assertEquals(2, markers.size)
        assertEquals("2026-08-02", markers[0].date)
        assertEquals("😊", markers[0].emoji)
        assertEquals(2, markers[0].entryCount)
        assertEquals("2026-08-05", markers[1].date)
        assertEquals("🎉", markers[1].emoji)
        assertEquals(1, markers[1].entryCount)
    }

    @Test
    fun dayMarkerReportsWhetherTheDayHasADraft() = runTest {
        dao.upsert(entity(id = "a", date = "2026-08-02", isDraft = false, createdAt = 100))
        dao.upsert(entity(id = "b", date = "2026-08-02", isDraft = true, createdAt = 200))
        dao.upsert(entity(id = "c", date = "2026-08-05", isDraft = false, createdAt = 300))

        val markers = dao.observeDayMarkers("2026-08-01", "2026-08-31").first()

        assertEquals(1, markers[0].draftCount)
        assertEquals(0, markers[1].draftCount)
    }

    @Test
    fun draftFlagSurvivesTheRoundTrip() = runTest {
        dao.upsert(entity(id = "d1", date = "2026-08-02", isDraft = true))

        assertEquals(true, dao.findById("d1")?.isDraft)
    }

    @Test
    fun updateFavoriteTogglesTheStarWithoutTouchingTheContent() = runTest {
        dao.upsert(entity(id = "d1", date = "2026-08-02", title = "海までドライブ", createdAt = 100))

        dao.updateFavorite("d1", true)

        val favorited = dao.findById("d1")
        assertEquals(true, favorited?.isFavorite)
        // 星は本文を触らない操作なので、最終更新日時も本文もそのまま
        assertEquals("海までドライブ", favorited?.title)
        assertEquals(100L, favorited?.updatedAt)

        dao.updateFavorite("d1", false)

        assertEquals(false, dao.findById("d1")?.isFavorite)
    }

    @Test
    fun upsertReplacesTheExistingRow() = runTest {
        dao.upsert(entity(id = "d1", date = "2026-08-02", title = "初稿"))
        dao.upsert(entity(id = "d1", date = "2026-08-02", title = "改稿"))

        assertEquals(1, dao.observeTotalCount().first())
        assertEquals("改稿", dao.findById("d1")?.title)
    }

    @Test
    fun deleteByIdRemovesTheRow() = runTest {
        dao.upsert(entity(id = "d1", date = "2026-08-02"))
        dao.deleteById("d1")

        assertNull(dao.findById("d1"))
    }

    @Test
    fun recordedDatesAreDistinctAndDescending() = runTest {
        dao.upsert(entity(id = "a", date = "2026-08-02", createdAt = 100))
        dao.upsert(entity(id = "b", date = "2026-08-02", createdAt = 200))
        dao.upsert(entity(id = "c", date = "2026-08-01", createdAt = 300))

        assertEquals(listOf("2026-08-02", "2026-08-01"), dao.observeRecordedDates().first())
    }

    @Test
    fun blocksJsonCanBeReadRawForMediaCleanup() = runTest {
        dao.upsert(
            entity(
                id = "d1",
                date = "2026-08-02",
                blocks = listOf(DiaryBlock.ImageBlock(localFilePath = "images/photo.jpg")),
            )
        )

        val raw = dao.findAllBlocksJson()

        assertEquals(1, raw.size)
        assert(raw.first().contains("images/photo.jpg"))
    }

    private fun entity(
        id: String,
        date: String,
        emoji: String = "😊",
        title: String? = null,
        blocks: List<DiaryBlock> = listOf(DiaryBlock.TextBlock(text = "本文")),
        isDraft: Boolean = false,
        isFavorite: Boolean = false,
        createdAt: Long = 0L,
    ) = DiaryEntity(
        id = id,
        date = date,
        emoji = emoji,
        title = title,
        blocks = blocks,
        isDraft = isDraft,
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
