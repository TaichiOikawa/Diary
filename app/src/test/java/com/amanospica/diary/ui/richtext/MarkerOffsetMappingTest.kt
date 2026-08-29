package com.amanospica.diary.ui.richtext

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 箇条書きの行頭記号を挿し込んだときの、カーソル位置の対応を確かめる。
 *
 * ここがずれると入力欄のカーソルが記号の中に迷い込んだり、
 * 打った文字が別の場所へ入ったりする。境目は「記号の内側を指されたとき」。
 */
class MarkerOffsetMappingTest {

    /** 本文 "ab\ncd" の2行目に2文字の記号を挿した状態（表示は "ab\n__cd"）。 */
    private val mapping = MarkerOffsetMapping(
        markerPositions = listOf(3),
        markerLength = 2,
        originalLength = 5,
    )

    @Test
    fun `記号より前の位置はそのまま`() {
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(2, mapping.originalToTransformed(2))
    }

    @Test
    fun `行頭のカーソルは記号のうしろに立つ`() {
        assertEquals(5, mapping.originalToTransformed(3))
    }

    @Test
    fun `記号より後ろは挿した分だけずれる`() {
        assertEquals(6, mapping.originalToTransformed(4))
        assertEquals(7, mapping.originalToTransformed(5))
    }

    @Test
    fun `記号の内側を指されたら行頭へ寄せる`() {
        assertEquals(3, mapping.transformedToOriginal(3))
        assertEquals(3, mapping.transformedToOriginal(4))
        assertEquals(3, mapping.transformedToOriginal(5))
    }

    @Test
    fun `表示位置から本文の位置へ戻せる`() {
        assertEquals(0, mapping.transformedToOriginal(0))
        assertEquals(2, mapping.transformedToOriginal(2))
        assertEquals(4, mapping.transformedToOriginal(6))
        assertEquals(5, mapping.transformedToOriginal(7))
    }

    @Test
    fun `本文の先頭が箇条書きでも対応がずれない`() {
        val fromStart = MarkerOffsetMapping(
            markerPositions = listOf(0),
            markerLength = 2,
            originalLength = 2,
        )

        assertEquals(2, fromStart.originalToTransformed(0))
        assertEquals(4, fromStart.originalToTransformed(2))
        assertEquals(0, fromStart.transformedToOriginal(0))
        assertEquals(0, fromStart.transformedToOriginal(2))
        assertEquals(1, fromStart.transformedToOriginal(3))
        assertEquals(2, fromStart.transformedToOriginal(4))
    }

    @Test
    fun `連続する箇条書きでも往復して元の位置に戻る`() {
        // "a\nb\nc" の3行すべてが箇条書き
        val allLines = MarkerOffsetMapping(
            markerPositions = listOf(0, 2, 4),
            markerLength = 2,
            originalLength = 5,
        )

        (0..5).forEach { original ->
            assertEquals(
                original,
                allLines.transformedToOriginal(allLines.originalToTransformed(original)),
            )
        }
    }

    @Test
    fun `記号が無ければ何も変わらない`() {
        val plain = MarkerOffsetMapping(
            markerPositions = emptyList(),
            markerLength = 2,
            originalLength = 4,
        )

        (0..4).forEach { offset ->
            assertEquals(offset, plain.originalToTransformed(offset))
            assertEquals(offset, plain.transformedToOriginal(offset))
        }
    }
}
