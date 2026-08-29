package com.amanospica.diary.domain.usecase

import com.amanospica.diary.domain.model.CalendarDayMarker
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.domain.model.MonthlyCount
import com.amanospica.diary.domain.repository.DiaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日記の検索（タイトル・本文の絞り込み）を確かめる。
 *
 * 空白区切りの語をすべて含む日記だけが残ること、検索語が無いときに全件が漏れ出さないことが要点。
 */
class SearchDiariesUseCaseTest {

    private val repository = FakeSearchRepository()
    private val searchDiaries = SearchDiariesUseCase(repository)

    @Test
    fun `本文に含まれる語で見つかる`() = runTest {
        repository.diaries.value = listOf(
            diary("a", body = "朝から海までドライブした"),
            diary("b", body = "一日中ねむかった"),
        )

        assertEquals(listOf("a"), search("ドライブ"))
    }

    @Test
    fun `タイトルに含まれる語でも見つかる`() = runTest {
        repository.diaries.value = listOf(
            diary("a", title = "海までドライブ", body = "潮風が気持ちいい"),
            diary("b", body = "特に何もない一日"),
        )

        assertEquals(listOf("a"), search("海"))
    }

    @Test
    fun `大文字小文字は区別しない`() = runTest {
        repository.diaries.value = listOf(diary("a", body = "Kotlin の勉強をした"))

        assertEquals(listOf("a"), search("kotlin"))
    }

    @Test
    fun `空白で区切った語はすべて含む日記だけが残る`() = runTest {
        repository.diaries.value = listOf(
            diary("a", title = "海までドライブ", body = "潮風が気持ちいい"),
            diary("b", body = "海で泳いだ"),
        )

        assertEquals(listOf("a"), search("海 ドライブ"))
    }

    @Test
    fun `全角空白でも語を区切れる`() = runTest {
        repository.diaries.value = listOf(
            diary("a", title = "海までドライブ"),
            diary("b", body = "海で泳いだ"),
        )

        assertEquals(listOf("a"), search("海　ドライブ"))
    }

    @Test
    fun `検索語が無ければ何も返さない`() = runTest {
        repository.diaries.value = listOf(diary("a", body = "本文"))

        // 空の入力で全件が出ると、検索していないのに一覧が現れて紛らわしい
        assertEquals(emptyList<String>(), search(""))
        assertEquals(emptyList<String>(), search("   "))
    }

    @Test
    fun `写真だけの日記は本文が無くても落ちない`() = runTest {
        repository.diaries.value = listOf(
            Diary(
                id = "a",
                date = LocalDate.of(2026, 8, 10),
                emoji = "📷",
                blocks = listOf(DiaryBlock.ImageBlock(localFilePath = "media/1.jpg")),
            ),
        )

        assertEquals(emptyList<String>(), search("海"))
    }

    @Test
    fun `一覧の並び（日付降順）はそのまま保たれる`() = runTest {
        repository.diaries.value = listOf(
            diary("new", body = "海を見た", date = LocalDate.of(2026, 8, 10)),
            diary("old", body = "海で泳いだ", date = LocalDate.of(2026, 8, 1)),
        )

        assertEquals(listOf("new", "old"), search("海"))
    }

    private suspend fun search(query: String): List<String> =
        searchDiaries(query.toSearchTerms()).first().map { it.id }

    private fun diary(
        id: String,
        title: String? = null,
        body: String = "",
        date: LocalDate = LocalDate.of(2026, 8, 10),
    ) = Diary(
        id = id,
        date = date,
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
