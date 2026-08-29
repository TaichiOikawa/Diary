package com.amanospica.diary.ui.richtext

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import com.amanospica.diary.domain.model.SpanType
import com.amanospica.diary.domain.model.TextSpanAnnotation

/**
 * 箇条書きの行頭に見せる記号。本文には入らず、表示のときだけ挿し込む。
 * 記号だけが行に取り残されないよう、区切りには改行しない空白を使う。
 */
private const val BULLET_MARKER = "• "

/** 文字単位の装飾（太字・斜体・下線）に対応する [SpanStyle]。 */
fun SpanType.toSpanStyle(): SpanStyle = when (this) {
    SpanType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    SpanType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    SpanType.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
    // 段落スタイルは行ごとに解釈するため、ここでは扱わない
    SpanType.HEADING, SpanType.LIST_ITEM -> SpanStyle()
}

/**
 * 入力中のテキストへ装飾を重ねる変換。
 *
 * 文字装飾（太字・斜体・下線）はスパンの範囲へそのまま乗せる。
 * 段落スタイルは行ごとに解釈し（[canonicalizeParagraphStyles]）、見出しは行全体の文字を
 * [headingStyle] で大きく、箇条書きは行頭へ [BULLET_MARKER] を挿し込んで見せる。
 *
 * 記号を挿し込むぶん表示上の文字位置がずれるので、カーソルと選択範囲の対応は
 * [MarkerOffsetMapping] で取り直す。本文そのものは変わらないため、
 * コピーや保存に行頭記号が紛れ込むことはない。
 */
class RichTextVisualTransformation(
    private val spans: List<TextSpanAnnotation>,
    private val headingStyle: SpanStyle,
    private val headingLineHeight: TextUnit,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText =
        buildRichText(text.text, spans, headingStyle, headingLineHeight)

    override fun equals(other: Any?): Boolean =
        other is RichTextVisualTransformation &&
            other.spans == spans &&
            other.headingStyle == headingStyle &&
            other.headingLineHeight == headingLineHeight

    override fun hashCode(): Int {
        var result = spans.hashCode()
        result = 31 * result + headingStyle.hashCode()
        result = 31 * result + headingLineHeight.hashCode()
        return result
    }
}

/**
 * 装飾を重ねた表示用の文字列と、本文との文字位置の対応を組み立てる。
 *
 * 編集画面（[RichTextVisualTransformation]）と閲覧画面の両方がここを通るので、
 * 書いているときと読んでいるときで本文の見え方が食い違わない。
 */
fun buildRichText(
    source: String,
    spans: List<TextSpanAnnotation>,
    headingStyle: SpanStyle,
    headingLineHeight: TextUnit,
): TransformedText {
    val lines = source.lineRanges()
    val mapping = richTextOffsetMapping(source, spans)

    val builder = AnnotatedString.Builder()
    var copied = 0
    mapping.markerPositions.forEach { at ->
        builder.append(source.substring(copied, at))
        builder.append(BULLET_MARKER)
        copied = at
    }
    builder.append(source.substring(copied))

    // 装飾の範囲は、記号を挿し込んだあとの座標へ置き直す
    spans.filterNot { it.type.isParagraphStyle }.forEach { span ->
        val start = span.start.coerceIn(0, source.length)
        val end = span.end.coerceIn(start, source.length)
        if (start < end) {
            builder.addStyle(
                span.type.toSpanStyle(),
                mapping.originalToTransformed(start),
                mapping.originalToTransformed(end),
            )
        }
    }
    lines.filter { spans.hasParagraphStyle(SpanType.HEADING, it) }
        .filter { it.start < it.end }
        .forEach { line ->
            // 行頭記号も見出しの一部として扱う。段落の切れ目を記号の手前に置かないと、
            // 記号だけが前の段落に取り残されて1行に浮いてしまう
            val start = mapping.transformedLineStart(line.start)
            val end = mapping.originalToTransformed(line.end)
            builder.addStyle(headingStyle, start, end)
            // 大きい字が行に収まるよう、この行だけ行送りを広げる。
            // 範囲は行末の改行まで含める（含めないと改行だけの段落ができて空行が増える）
            builder.addStyle(
                ParagraphStyle(lineHeight = headingLineHeight),
                start,
                if (line.end < source.length) end + 1 else end,
            )
        }

    return TransformedText(builder.toAnnotatedString(), mapping)
}

/**
 * 本文の位置と、行頭記号を挿し込んだ表示上の位置との対応。
 *
 * [buildRichText] が組み立てるのと同じ対応を、装飾を組み直さずに取り出せる。
 * カーソルの座標を知りたいだけの場面（表示中の行を画面内へ送るなど）で使う。
 */
fun richTextOffsetMapping(
    source: String,
    spans: List<TextSpanAnnotation>,
): MarkerOffsetMapping {
    val bulletStarts = source.lineRanges()
        .filter { spans.hasParagraphStyle(SpanType.LIST_ITEM, it) }
        .map { it.start }
    return MarkerOffsetMapping(bulletStarts, BULLET_MARKER.length, source.length)
}

/**
 * 本文の決まった位置へ同じ長さの記号を挿し込んだときの、文字位置の対応。
 *
 * [markerPositions] は挿し込む位置（本文側の座標、昇順）。挿し込んだ位置にあったカーソルは
 * 記号の**後ろ**へ送る（箇条書きの行頭にカーソルを置くと、記号の右に立つ）。
 * 記号の内側を指す表示位置は、すべてその行頭へ寄せる。
 */
class MarkerOffsetMapping(
    /** 記号を挿し込む位置（本文側の座標、昇順）。 */
    val markerPositions: List<Int>,
    private val markerLength: Int,
    private val originalLength: Int,
) : OffsetMapping {

    override fun originalToTransformed(offset: Int): Int {
        val original = offset.coerceIn(0, originalLength)
        return original + markerLength * markerPositions.count { it <= original }
    }

    /**
     * [original] にある行の、記号を含めた表示上の先頭。
     * [originalToTransformed] は行頭のカーソルを記号のうしろへ送るので、
     * 「行そのものの始まり」が要るときはこちらを使う。
     */
    fun transformedLineStart(original: Int): Int =
        original.coerceIn(0, originalLength).let { at ->
            at + markerLength * markerPositions.count { it < at }
        }

    override fun transformedToOriginal(offset: Int): Int {
        var original = 0
        var transformed = 0
        markerPositions.forEach { position ->
            val plainLength = position - original
            if (offset <= transformed + plainLength) return original + (offset - transformed)
            transformed += plainLength
            original = position
            // 記号の内側は行頭と区別できないので、まとめて行頭に寄せる
            if (offset <= transformed + markerLength) return original
            transformed += markerLength
        }
        return (original + (offset - transformed)).coerceIn(0, originalLength)
    }
}
