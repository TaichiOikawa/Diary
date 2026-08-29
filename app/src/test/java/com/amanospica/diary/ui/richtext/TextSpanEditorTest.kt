package com.amanospica.diary.ui.richtext

import com.amanospica.diary.domain.model.SpanType
import com.amanospica.diary.domain.model.TextSpanAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSpanEditorTest {

    private fun bold(start: Int, end: Int) = TextSpanAnnotation(start, end, SpanType.BOLD)
    private fun italic(start: Int, end: Int) = TextSpanAnnotation(start, end, SpanType.ITALIC)

    // --- normalize ---

    @Test
    fun `同じ種類の重なりと隣接は1本にまとまる`() {
        val result = TextSpanEditor.normalize(listOf(bold(0, 5), bold(3, 8), bold(8, 10)), 20)
        assertEquals(listOf(bold(0, 10)), result)
    }

    @Test
    fun `種類が違えば重なっていても別々に残る`() {
        val result = TextSpanEditor.normalize(listOf(bold(0, 5), italic(2, 7)), 20)
        assertEquals(listOf(bold(0, 5), italic(2, 7)), result)
    }

    @Test
    fun `テキスト長を超える範囲は切り詰められ空になれば捨てられる`() {
        val result = TextSpanEditor.normalize(listOf(bold(0, 100), bold(30, 40)), 10)
        assertEquals(listOf(bold(0, 10)), result)
    }

    // --- toggle ---

    @Test
    fun `未適用の範囲をトグルすると装飾が付く`() {
        val result = TextSpanEditor.toggle(emptyList(), SpanType.BOLD, 2, 5, 10)
        assertEquals(listOf(bold(2, 5)), result)
    }

    @Test
    fun `適用済みの範囲をトグルすると装飾が外れる`() {
        val result = TextSpanEditor.toggle(listOf(bold(0, 10)), SpanType.BOLD, 2, 5, 10)
        assertEquals(listOf(bold(0, 2), bold(5, 10)), result)
    }

    @Test
    fun `部分的にしか適用されていない範囲をトグルすると全体に付く`() {
        val result = TextSpanEditor.toggle(listOf(bold(0, 3)), SpanType.BOLD, 0, 8, 10)
        assertEquals(listOf(bold(0, 8)), result)
    }

    @Test
    fun `行単位のスタイルはここでは扱わない`() {
        // 見出し・箇条書きは toggleParagraphStyle の担当（ParagraphLinesTest を参照）
        assertThrows(IllegalArgumentException::class.java) {
            TextSpanEditor.toggle(emptyList(), SpanType.HEADING, 3, 4, 12)
        }
    }

    // --- isApplied ---

    @Test
    fun `隙間なく覆われていれば適用済みと判定する`() {
        assertTrue(TextSpanEditor.isApplied(listOf(bold(0, 5), bold(5, 9)), SpanType.BOLD, 1, 8))
        assertFalse(TextSpanEditor.isApplied(listOf(bold(0, 3), bold(5, 9)), SpanType.BOLD, 1, 8))
    }

    // --- computeChange / remap ---

    @Test
    fun `文字装飾はちょうど末尾で書き足しても広がらない`() {
        val change = TextChange(start = 3, removed = 0, inserted = 1)
        val result = TextSpanEditor.remap(listOf(bold(0, 3)), change, 4)
        assertEquals(listOf(bold(0, 3)), result)
    }

    @Test
    fun `段落スタイルはちょうど末尾で書き足すと一緒に伸びる`() {
        // 見出し行の末尾で打った文字は、その行の続きとして扱いたい
        val change = TextChange(start = 3, removed = 0, inserted = 1)
        val heading = TextSpanAnnotation(0, 3, SpanType.HEADING)

        val result = TextSpanEditor.remap(listOf(heading), change, 4)
        assertEquals(listOf(heading.copy(end = 4)), result)
    }

    @Test
    fun `末尾への追記は前方のスパンを動かさない`() {
        val change = TextSpanEditor.computeChange("あいうえお", "あいうえおかき")
        assertEquals(TextChange(start = 5, removed = 0, inserted = 2), change)

        val result = TextSpanEditor.remap(listOf(bold(0, 3)), change, 7)
        assertEquals(listOf(bold(0, 3)), result)
    }

    @Test
    fun `前方への挿入でスパンが後ろへずれる`() {
        val change = TextSpanEditor.computeChange("あいうえお", "XXあいうえお")
        assertEquals(TextChange(start = 0, removed = 0, inserted = 2), change)

        val result = TextSpanEditor.remap(listOf(bold(1, 4)), change, 7)
        assertEquals(listOf(bold(3, 6)), result)
    }

    @Test
    fun `削除でスパンが縮む`() {
        val change = TextSpanEditor.computeChange("あいうえお", "あお")
        assertEquals(TextChange(start = 1, removed = 3, inserted = 0), change)

        val result = TextSpanEditor.remap(listOf(bold(0, 5)), change, 2)
        assertEquals(listOf(bold(0, 2)), result)
    }

    @Test
    fun `スパンが丸ごと削除されると消える`() {
        val change = TextSpanEditor.computeChange("あいうえお", "あお")
        val result = TextSpanEditor.remap(listOf(bold(2, 3)), change, 2)
        assertEquals(emptyList<TextSpanAnnotation>(), result)
    }

    @Test
    fun `置換は前後の共通部分を除いた範囲だけを変更とみなす`() {
        val change = TextSpanEditor.computeChange("今日は晴れ", "今日は雨天だ")
        assertEquals(TextChange(start = 3, removed = 2, inserted = 3), change)
    }

    // --- split ---

    @Test
    fun `分割すると後半のスパン座標が0起点に直る`() {
        val (before, after) = TextSpanEditor.split(listOf(bold(1, 8)), at = 4, textLength = 10)

        assertEquals(listOf(bold(1, 4)), before)
        assertEquals(listOf(bold(0, 4)), after)
    }

    @Test
    fun `分割点より手前のスパンは前半にだけ残る`() {
        val (before, after) = TextSpanEditor.split(listOf(bold(0, 2)), at = 5, textLength = 10)

        assertEquals(listOf(bold(0, 2)), before)
        assertEquals(emptyList<TextSpanAnnotation>(), after)
    }
}
