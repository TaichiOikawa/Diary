package com.amanospica.diary.ui.richtext

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.amanospica.diary.domain.model.SpanType
import com.amanospica.diary.domain.model.TextSpanAnnotation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 行頭記号が「表示にだけ」足されることを確かめる。
 *
 * 本文へ実際に記号が混ざると、保存やコピーに紛れ込んだうえ文字数もずれる。
 * 境目は「変換後の見た目」と「本文はそのまま」の2つ。
 */
class RichTextVisualTransformationTest {

    private fun transform(text: String, vararg spans: TextSpanAnnotation) =
        RichTextVisualTransformation(
            spans = spans.toList(),
            headingStyle = SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            headingLineHeight = 35.sp,
        ).filter(AnnotatedString(text))

    private fun bullet(start: Int, end: Int) =
        TextSpanAnnotation(start, end, SpanType.LIST_ITEM)

    @Test
    fun `箇条書きの行だけ行頭に記号が付く`() {
        val result = transform("いち\nに\nさん", bullet(0, 2), bullet(5, 7))

        assertEquals("• いち\nに\n• さん", result.text.text)
    }

    @Test
    fun `箇条書きが無ければ本文はそのまま`() {
        val result = transform("いち\nに")
        assertEquals("いち\nに", result.text.text)
    }

    @Test
    fun `空行の箇条書きにも記号が付く`() {
        val result = transform("いち\n", bullet(0, 2), bullet(3, 3))
        assertEquals("• いち\n• ", result.text.text)
    }

    @Test
    fun `記号を足しても本文側のカーソル位置は戻せる`() {
        val result = transform("いち\nに", bullet(0, 2), bullet(3, 4))

        (0..4).forEach { original ->
            val transformed = result.offsetMapping.originalToTransformed(original)
            assertEquals(original, result.offsetMapping.transformedToOriginal(transformed))
        }
    }

    @Test
    fun `見出しは行の文字にだけ掛かる`() {
        val result = transform("みだし\nほんぶん", TextSpanAnnotation(0, 3, SpanType.HEADING))

        assertEquals("みだし\nほんぶん", result.text.text)
        assertEquals(listOf(0 to 3), result.text.spanStyles.map { it.start to it.end })
    }
}
