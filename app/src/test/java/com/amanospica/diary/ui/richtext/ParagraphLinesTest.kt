package com.amanospica.diary.ui.richtext

import com.amanospica.diary.domain.model.SpanType
import com.amanospica.diary.domain.model.TextSpanAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 見出し・箇条書きが「行」に付くことを確かめる。
 *
 * 1つのテキストブロックが複数行を持つようになったため、境目になるのは
 * 「どの行に効くか」「編集しても付いたままか」の2点。
 */
class ParagraphLinesTest {

    private fun bullet(start: Int, end: Int) =
        TextSpanAnnotation(start, end, SpanType.LIST_ITEM)

    private fun heading(start: Int, end: Int) =
        TextSpanAnnotation(start, end, SpanType.HEADING)

    @Test
    fun `改行で行に分かれ、末尾の改行のあとには空行が付く`() {
        assertEquals(listOf(LineRange(0, 3)), "あいう".lineRanges())
        assertEquals(listOf(LineRange(0, 2), LineRange(3, 6)), "あい\nうえお".lineRanges())
        assertEquals(listOf(LineRange(0, 2), LineRange(3, 3)), "あい\n".lineRanges())
        assertEquals(listOf(LineRange(0, 0)), "".lineRanges())
    }

    @Test
    fun `カーソルは行の境目でどちらか一方の行にだけ属する`() {
        val text = "あい\nうえ"

        assertEquals(listOf(LineRange(0, 2)), text.lineRangesCovering(2, 2))
        assertEquals(listOf(LineRange(3, 5)), text.lineRangesCovering(3, 3))
    }

    @Test
    fun `選択範囲が掛かっている行はすべて対象になる`() {
        val text = "あい\nうえ\nおか"

        assertEquals(
            listOf(LineRange(0, 2), LineRange(3, 5)),
            text.lineRangesCovering(1, 4),
        )
    }

    @Test
    fun `目印は自分の行にだけ効き、隣の行には漏れない`() {
        val text = "いちご\nみかん"
        val spans = listOf(bullet(0, 3))
        val (first, second) = text.lineRanges()

        assertTrue(spans.hasParagraphStyle(SpanType.LIST_ITEM, first))
        assertFalse(spans.hasParagraphStyle(SpanType.LIST_ITEM, second))
    }

    @Test
    fun `空行の目印は幅ゼロでもその行に効く`() {
        val text = "あ\n\nい"
        val spans = listOf(bullet(2, 2))
        val lines = text.lineRanges()

        assertFalse(spans.hasParagraphStyle(SpanType.LIST_ITEM, lines[0]))
        assertTrue(spans.hasParagraphStyle(SpanType.LIST_ITEM, lines[1]))
        assertFalse(spans.hasParagraphStyle(SpanType.LIST_ITEM, lines[2]))
    }

    @Test
    fun `選択が掛かった行すべてに付き、もう一度押すと外れる`() {
        val text = "いち\nに\nさん"

        val applied = emptyList<TextSpanAnnotation>()
            .toggleParagraphStyle(SpanType.LIST_ITEM, text, 1, 4)
        assertEquals(listOf(bullet(0, 2), bullet(3, 4)), applied)

        val removed = applied.toggleParagraphStyle(SpanType.LIST_ITEM, text, 1, 4)
        assertEquals(emptyList<TextSpanAnnotation>(), removed)
    }

    @Test
    fun `一部の行にしか付いていなければ、残りの行にも付ける`() {
        val text = "いち\nに"
        val partial = listOf(bullet(0, 2))

        assertEquals(
            listOf(bullet(0, 2), bullet(3, 4)),
            partial.toggleParagraphStyle(SpanType.LIST_ITEM, text, 0, 4),
        )
    }

    @Test
    fun `文字装飾は段落スタイルの付け外しで消えない`() {
        val text = "いちご"
        val bold = TextSpanAnnotation(0, 2, SpanType.BOLD)

        val applied = listOf(bold).toggleParagraphStyle(SpanType.HEADING, text, 0, 0)
        assertEquals(listOf(bold, heading(0, 3)), applied)
        assertEquals(listOf(bold), applied.toggleParagraphStyle(SpanType.HEADING, text, 0, 0))
    }

    @Test
    fun `行の途中で改行すると、割れた両方に引き継がれる`() {
        // 「いちご」が箇条書き。2文字目のうしろで Enter を押した状態
        val spans = TextSpanEditor
            .remap(listOf(bullet(0, 3)), TextChange(start = 2, removed = 0, inserted = 1), 4)
            .canonicalizeParagraphStyles("いち\nご")

        assertEquals(listOf(bullet(0, 2), bullet(3, 4)), spans)
    }

    @Test
    fun `行末で改行すると、新しい空行にも引き継がれる`() {
        val spans = TextSpanEditor
            .remap(listOf(bullet(0, 3)), TextChange(start = 3, removed = 0, inserted = 1), 4)
            .canonicalizeParagraphStyles("いちご\n")

        assertEquals(listOf(bullet(0, 3), bullet(4, 4)), spans)
    }

    @Test
    fun `引き継いだ空行に書き足しても箇条書きのまま`() {
        val spans = listOf(bullet(0, 3), bullet(4, 4))
            .let { TextSpanEditor.remap(it, TextChange(start = 4, removed = 0, inserted = 1), 5) }
            .canonicalizeParagraphStyles("いちご\nみ")

        assertEquals(listOf(bullet(0, 3), bullet(4, 5)), spans)
    }

    @Test
    fun `行をまたいで伸びた目印は行ごとの形に整え直される`() {
        val messy = listOf(bullet(1, 6))

        assertEquals(
            listOf(bullet(0, 2), bullet(3, 6)),
            messy.canonicalizeParagraphStyles("あい\nうえお"),
        )
    }
}
