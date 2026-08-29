package com.amanospica.diary.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 検索結果の抜粋と、光らせる位置の切り出しを確かめる。
 *
 * 抜粋は切り出したあとの文字列に対して位置を出し直すので、
 * 「…」を足しても光る場所がずれないことが要点。
 */
class SearchSnippetTest {

    @Test
    fun `一致した語の位置を返す`() {
        val ranges = highlightRanges("海までドライブ", listOf("ドライブ"))

        assertEquals(listOf(3..6), ranges)
    }

    @Test
    fun `同じ語が何度出ても全部拾う`() {
        val ranges = highlightRanges("海、海、海", listOf("海"))

        assertEquals(listOf(0..0, 2..2, 4..4), ranges)
    }

    @Test
    fun `大文字小文字は区別しない`() {
        val ranges = highlightRanges("Kotlin と kotlin", listOf("kotlin"))

        assertEquals(listOf(0..5, 9..14), ranges)
    }

    @Test
    fun `重なり合う語はひとつの範囲にまとめる`() {
        // 「ドライブ」と「ライ」は重なる。別々に返すと光る帯が途切れて見える
        val ranges = highlightRanges("海までドライブ", listOf("ドライブ", "ライ"))

        assertEquals(listOf(3..6), ranges)
    }

    @Test
    fun `一致が無ければ空`() {
        assertEquals(emptyList<IntRange>(), highlightRanges("海までドライブ", listOf("山")))
        assertEquals(emptyList<IntRange>(), highlightRanges("", listOf("海")))
    }

    @Test
    fun `短い本文はそのまま抜粋になる`() {
        val snippet = buildSearchSnippet("朝から海までドライブした", listOf("ドライブ"))

        assertEquals("朝から海までドライブした", snippet.text)
        assertEquals(listOf(6..9), snippet.highlights)
    }

    @Test
    fun `改行や連続した空白は空白ひとつに潰す`() {
        val snippet = buildSearchSnippet("朝から\n\n海まで  ドライブ", listOf("ドライブ"))

        assertEquals("朝から 海まで ドライブ", snippet.text)
    }

    @Test
    fun `後ろのほうで一致したら、その手前から切り出す`() {
        val body = "あ".repeat(200) + "ドライブ" + "い".repeat(200)

        val snippet = buildSearchSnippet(body, listOf("ドライブ"))

        // 前も後ろも省いたことが分かるようにする
        assertTrue(snippet.text.startsWith("…"))
        assertTrue(snippet.text.endsWith("…"))
        // 切り出したあとの位置で光るので、「…」の分がずれていない
        val highlight = snippet.highlights.single()
        assertEquals("ドライブ", snippet.text.substring(highlight.first, highlight.last + 1))
    }

    @Test
    fun `本文に一致が無ければ先頭から見せる`() {
        // タイトルだけで引っかかった日記。本文は頭から出す
        val body = "あ".repeat(200)

        val snippet = buildSearchSnippet(body, listOf("ドライブ"))

        assertTrue(snippet.text.startsWith("あ"))
        assertTrue(snippet.text.endsWith("…"))
        assertEquals(emptyList<IntRange>(), snippet.highlights)
    }

    @Test
    fun `本文が空なら抜粋も空`() {
        val snippet = buildSearchSnippet("", listOf("海"))

        assertEquals(SearchSnippet.Empty, snippet)
    }
}
