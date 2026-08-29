package com.amanospica.diary.ui.richtext

import com.amanospica.diary.domain.model.SpanType
import com.amanospica.diary.domain.model.TextSpanAnnotation

/**
 * 本文中の1行を表す範囲。[end] は行末の位置で、区切りの改行文字そのものは含まない。
 * 空行では [start] と [end] が等しくなる。
 */
data class LineRange(val start: Int, val end: Int)

/**
 * 見出し・箇条書きといった段落スタイルを「行」に対して扱うための道具。
 *
 * 1つのテキストブロックが複数行を持つため、段落スタイルはブロック全体ではなく行ごとに付く。
 * スパンは行の目印として使い、次の規約で読み書きする。
 *
 * - 目印は1行につき1つ、その行の [LineRange.start] から [LineRange.end] までを覆う
 * - 空行の目印は幅ゼロになる（[TextSpanEditor.normalize] は段落スタイルに限りこれを残す）
 * - 行にスタイルが付いているかは「端を含む重なり」で見る。
 *   幅ゼロの目印を拾うためと、行末で改行して伸びた目印を新しい行へ引き継ぐため
 */

/** 改行で区切った行の一覧。末尾が改行で終わる場合は空行が1つ後ろに付く。 */
fun String.lineRanges(): List<LineRange> {
    val lines = mutableListOf<LineRange>()
    var start = 0
    while (true) {
        val lineBreak = indexOf('\n', start)
        if (lineBreak < 0) {
            lines += LineRange(start, length)
            return lines
        }
        lines += LineRange(start, lineBreak)
        start = lineBreak + 1
    }
}

/** 選択範囲 [selectionStart]..[selectionEnd] が掛かっている行すべて。 */
fun String.lineRangesCovering(selectionStart: Int, selectionEnd: Int): List<LineRange> {
    val from = minOf(selectionStart, selectionEnd).coerceIn(0, length)
    val to = maxOf(selectionStart, selectionEnd).coerceIn(0, length)
    return lineRanges().filter { it.start <= to && it.end >= from }
}

/** [line] に [type] の段落スタイルが付いているか。 */
fun List<TextSpanAnnotation>.hasParagraphStyle(type: SpanType, line: LineRange): Boolean =
    any { it.type == type && it.start <= line.end && it.end >= line.start }

/**
 * 段落スタイルの目印を「1行につき1つ」の形へ整え直す。
 *
 * 編集に追従させた（[TextSpanEditor.remap]）直後の目印は、行をまたいで伸びていたり
 * 行の一部しか覆っていなかったりする。ここで行の形に揃えておくことで、
 * 以降の判定・付け外しが行単位の素直な操作で済む。
 *
 * 行末で改行したときに目印が新しい行まで伸びる性質はそのまま使っていて、
 * これが「箇条書きの途中で改行すれば次の行も箇条書きになる」ふるまいになる。
 */
fun List<TextSpanAnnotation>.canonicalizeParagraphStyles(
    text: String,
): List<TextSpanAnnotation> {
    val lines = text.lineRanges()
    val markers = SpanType.entries
        .filter { it.isParagraphStyle }
        .flatMap { type ->
            lines.filter { line -> hasParagraphStyle(type, line) }
                .map { line -> TextSpanAnnotation(line.start, line.end, type) }
        }
    return filterNot { it.type.isParagraphStyle } + markers
}

/**
 * 選択範囲が掛かっている行の段落スタイルを切り替える。
 * 掛かっている行すべてに付いていれば外し、そうでなければ付いていない行も含めて全部に付ける。
 */
fun List<TextSpanAnnotation>.toggleParagraphStyle(
    type: SpanType,
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
): List<TextSpanAnnotation> {
    require(type.isParagraphStyle) { "$type は段落スタイルではない" }

    val lines = text.lineRangesCovering(selectionStart, selectionEnd)
    if (lines.isEmpty()) return this

    // 目印が行の形に揃っている前提で消すため、先に整えておく
    val canonical = canonicalizeParagraphStyles(text)
    val untouched = canonical.filterNot { span ->
        span.type == type && lines.any { span.start <= it.end && span.end >= it.start }
    }

    return if (lines.all { canonical.hasParagraphStyle(type, it) }) {
        untouched
    } else {
        untouched + lines.map { TextSpanAnnotation(it.start, it.end, type) }
    }
}
