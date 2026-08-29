package com.amanospica.diary.ui.photo

import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.DiaryBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * フォトタブの一覧を組み立てる [toPhotoSections] の検証。
 *
 * 入力の日記は `ObserveTimelineUseCase` と同じ日付降順で渡す。
 */
class PhotoSectionsTest {

    /** 相対パスの頭に印を付けるだけの、解決関数の代役。 */
    private val resolvePath: (String) -> String = { "/media/$it" }

    private fun diary(
        id: String,
        date: LocalDate,
        blocks: List<DiaryBlock>,
        isDraft: Boolean = false,
    ) = Diary(id = id, date = date, emoji = "😀", blocks = blocks, isDraft = isDraft)

    private fun image(id: String, path: String) =
        DiaryBlock.ImageBlock(id = id, localFilePath = path)

    private fun video(id: String, path: String) =
        DiaryBlock.VideoBlock(id = id, localFilePath = path)

    private fun text(id: String, body: String) = DiaryBlock.TextBlock(id = id, text = body)

    @Test
    fun `日記がなければ区切りもできない`() {
        assertTrue(emptyList<Diary>().toPhotoSections(resolvePath).isEmpty())
    }

    @Test
    fun `写真も動画もない日記は区切りを作らない`() {
        val diaries = listOf(
            diary("d1", LocalDate.of(2026, 8, 10), listOf(text("t1", "本文だけ"))),
        )
        assertTrue(diaries.toPhotoSections(resolvePath).isEmpty())
    }

    @Test
    fun `文章ブロックは一覧に混ざらない`() {
        val diaries = listOf(
            diary(
                "d1",
                LocalDate.of(2026, 8, 10),
                listOf(text("t1", "前置き"), image("b1", "a.jpg"), text("t2", "後書き")),
            ),
        )

        val items = diaries.toPhotoSections(resolvePath).single().items
        assertEquals(listOf("d1:b1"), items.map { it.id })
    }

    @Test
    fun `同じ月の写真は日記をまたいでひとつの区切りにまとまる`() {
        val diaries = listOf(
            diary("d1", LocalDate.of(2026, 8, 10), listOf(image("b1", "a.jpg"))),
            diary("d2", LocalDate.of(2026, 8, 3), listOf(image("b2", "b.jpg"))),
        )

        val sections = diaries.toPhotoSections(resolvePath)
        assertEquals(1, sections.size)
        assertEquals(2026 to 8, sections[0].year to sections[0].month)
        assertEquals(listOf("d1:b1", "d2:b2"), sections[0].items.map { it.id })
    }

    @Test
    fun `月が変われば区切りも分かれ、新しい月が先に来る`() {
        val diaries = listOf(
            diary("d1", LocalDate.of(2026, 8, 1), listOf(image("b1", "a.jpg"))),
            diary("d2", LocalDate.of(2026, 7, 31), listOf(image("b2", "b.jpg"))),
            diary("d3", LocalDate.of(2025, 8, 1), listOf(image("b3", "c.jpg"))),
        )

        val sections = diaries.toPhotoSections(resolvePath)
        assertEquals(
            listOf(2026 to 8, 2026 to 7, 2025 to 8),
            sections.map { it.year to it.month },
        )
    }

    @Test
    fun `1件の日記の中では本文に現れる順に並ぶ`() {
        val diaries = listOf(
            diary(
                "d1",
                LocalDate.of(2026, 8, 10),
                listOf(image("b1", "a.jpg"), video("b2", "m.mp4"), image("b3", "c.jpg")),
            ),
        )

        val items = diaries.toPhotoSections(resolvePath).single().items
        assertEquals(listOf("d1:b1", "d1:b2", "d1:b3"), items.map { it.id })
        assertEquals(listOf(false, true, false), items.map { it.isVideo })
    }

    @Test
    fun `相対パスは解決関数を通した絶対パスになる`() {
        val diaries = listOf(
            diary("d1", LocalDate.of(2026, 8, 10), listOf(image("b1", "a.jpg"))),
        )

        assertEquals(
            "/media/a.jpg",
            diaries.toPhotoSections(resolvePath).single().items.single().absolutePath,
        )
    }

    @Test
    fun `貼り付け元の日記と日付を引き継ぐ`() {
        val date = LocalDate.of(2026, 8, 10)
        val diaries = listOf(diary("d1", date, listOf(image("b1", "a.jpg")), isDraft = true))

        val item = diaries.toPhotoSections(resolvePath).single().items.single()
        assertEquals("d1", item.diaryId)
        assertEquals(date, item.date)
        assertTrue(item.isDraft)
    }

    @Test
    fun `別の日記が同じブロックIDを持っていてもキーは重ならない`() {
        val diaries = listOf(
            diary("d1", LocalDate.of(2026, 8, 10), listOf(image("same", "a.jpg"))),
            diary("d2", LocalDate.of(2026, 8, 9), listOf(image("same", "b.jpg"))),
        )

        val ids = diaries.toPhotoSections(resolvePath).flatMap { it.items }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
