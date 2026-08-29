package com.amanospica.diary.data.local

import com.amanospica.diary.data.local.converter.DiaryTypeConverters
import com.amanospica.diary.data.media.ImageCompressor
import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.domain.model.SpanType
import com.amanospica.diary.domain.model.TextSpanAnnotation
import com.amanospica.diary.domain.model.mediaFilePaths
import com.amanospica.diary.domain.model.plainTextPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryBlockSerializationTest {

    private val converters = DiaryTypeConverters()

    private val blocks = listOf(
        DiaryBlock.TextBlock(
            id = "block-1",
            text = "今日は良い天気だった",
            spans = listOf(TextSpanAnnotation(0, 2, SpanType.BOLD)),
        ),
        DiaryBlock.ImageBlock(id = "block-2", localFilePath = "images/a.jpg", caption = "散歩道"),
        DiaryBlock.TextBlock(id = "block-3", text = "そのあと動画も撮った"),
        DiaryBlock.VideoBlock(id = "block-4", localFilePath = "videos/b.mp4"),
    )

    @Test
    fun `ブロック列は JSON 往復で完全に復元される`() {
        val restored = converters.jsonToBlocks(converters.blocksToJson(blocks))
        assertEquals(blocks, restored)
    }

    @Test
    fun `JSON には型判別子とブロック ID が書き出される`() {
        val json = converters.blocksToJson(blocks)
        assertTrue(json, json.contains("\"type\":\"text\""))
        assertTrue(json, json.contains("\"type\":\"image\""))
        assertTrue(json, json.contains("\"type\":\"video\""))
        assertTrue(json, json.contains("\"id\":\"block-1\""))
    }

    @Test
    fun `未知のフィールドがあっても復元できる`() {
        val json = """[{"type":"text","id":"x","text":"hello","futureField":123}]"""
        val restored = converters.jsonToBlocks(json)
        assertEquals(listOf(DiaryBlock.TextBlock(id = "x", text = "hello")), restored)
    }

    @Test
    fun `壊れた JSON では空リストにフォールバックする`() {
        assertEquals(emptyList<DiaryBlock>(), converters.jsonToBlocks("{ not json"))
        assertEquals(emptyList<DiaryBlock>(), converters.jsonToBlocks(""))
    }

    @Test
    fun `メディアパスの抽出はテキストブロックを無視する`() {
        assertEquals(listOf("images/a.jpg", "videos/b.mp4"), blocks.mediaFilePaths())
    }

    @Test
    fun `プレビューはテキストブロックだけを連結する`() {
        assertEquals("今日は良い天気だった そのあと動画も撮った", blocks.plainTextPreview())
    }

    @Test
    fun `プレビューは指定文字数で打ち切られる`() {
        val long = listOf(DiaryBlock.TextBlock(text = "あ".repeat(200)))
        val preview = long.plainTextPreview(maxLength = 10)
        assertEquals("あ".repeat(10) + "…", preview)
    }

    @Test
    fun `inSampleSize は長辺が上限を下回らない最大の2の冪を返す`() {
        // 4000px -> 2000px（1920 を下回らない）ところまで間引く
        assertEquals(2, ImageCompressor.calculateInSampleSize(4000, 3000, 1920))
        assertEquals(4, ImageCompressor.calculateInSampleSize(8000, 6000, 1920))
        assertEquals(1, ImageCompressor.calculateInSampleSize(1920, 1080, 1920))
        assertEquals(1, ImageCompressor.calculateInSampleSize(800, 600, 1920))
    }
}
