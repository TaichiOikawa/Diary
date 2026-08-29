package com.amanospica.diary.ui.richtext

import com.amanospica.diary.domain.model.SpanType
import com.amanospica.diary.domain.model.TextSpanAnnotation

/** テキスト編集1回分の差分（[start] 位置から [removed] 文字消して [inserted] 文字挿入）。 */
data class TextChange(
    val start: Int,
    val removed: Int,
    val inserted: Int,
) {
    val isEmpty: Boolean get() = removed == 0 && inserted == 0
}

/**
 * リッチテキストの装飾範囲を操作する純粋ロジック。
 *
 * Compose の状態管理から切り離してあるため、そのまま単体テストできる。
 * 範囲は常に `[start, end)`（end は含まない）。
 */
object TextSpanEditor {

    /**
     * 範囲外・空・重複を整理する。
     * 同じ種類で重なっている／隣接しているスパンは1本にまとめ、常に開始位置順に並べる。
     *
     * 幅ゼロのスパンは捨てるが、段落スタイルだけは残す。空行に付いた見出し・箇条書きの
     * 目印は幅を持ちようがなく、ここで捨てると空行のスタイルが消えてしまうため
     * （詳しくは [ParagraphLines.kt][canonicalizeParagraphStyles]）。
     */
    fun normalize(spans: List<TextSpanAnnotation>, textLength: Int): List<TextSpanAnnotation> {
        val clipped = spans.mapNotNull { span ->
            val start = span.start.coerceIn(0, textLength)
            val end = span.end.coerceIn(start, textLength)
            if (start < end || span.type.isParagraphStyle) {
                span.copy(start = start, end = end)
            } else {
                null
            }
        }

        return clipped
            .groupBy { it.type }
            .flatMap { (type, group) -> mergeSameType(type, group) }
            .sortedWith(compareBy({ it.start }, { it.end }, { it.type }))
    }

    private fun mergeSameType(
        type: SpanType,
        group: List<TextSpanAnnotation>,
    ): List<TextSpanAnnotation> {
        val sorted = group.sortedBy { it.start }
        val merged = mutableListOf<TextSpanAnnotation>()
        sorted.forEach { span ->
            val last = merged.lastOrNull()
            // 隣接（last.end == span.start）も結合対象にして細切れを防ぐ
            if (last != null && span.start <= last.end) {
                merged[merged.lastIndex] = last.copy(end = maxOf(last.end, span.end))
            } else {
                merged += TextSpanAnnotation(span.start, span.end, type)
            }
        }
        return merged
    }

    /** [start, end) が [type] で隙間なく覆われているか。 */
    fun isApplied(
        spans: List<TextSpanAnnotation>,
        type: SpanType,
        start: Int,
        end: Int,
    ): Boolean {
        if (start >= end) return false
        var covered = start
        normalize(spans, Int.MAX_VALUE)
            .filter { it.type == type }
            .forEach { span ->
                if (span.start <= covered) covered = maxOf(covered, span.end)
            }
        return covered >= end
    }

    /**
     * [start, end) に対する [type] のオン／オフを切り替える。
     * 既に全体が適用済みなら解除、そうでなければ適用する（一般的なエディタの挙動）。
     *
     * 文字単位の装飾（太字・斜体・下線）専用。見出し・箇条書きは行ごとに付くスタイルなので
     * [toggleParagraphStyle] を使う。
     */
    fun toggle(
        spans: List<TextSpanAnnotation>,
        type: SpanType,
        start: Int,
        end: Int,
        textLength: Int,
    ): List<TextSpanAnnotation> {
        require(!type.isParagraphStyle) { "$type は行単位のスタイル" }
        if (start >= end) return normalize(spans, textLength)

        return if (isApplied(spans, type, start, end)) {
            normalize(remove(spans, type, start, end), textLength)
        } else {
            normalize(spans + TextSpanAnnotation(start, end, type), textLength)
        }
    }

    /** [start, end) から [type] の装飾を取り除く（範囲の一部だけなら分割する）。 */
    fun remove(
        spans: List<TextSpanAnnotation>,
        type: SpanType,
        start: Int,
        end: Int,
    ): List<TextSpanAnnotation> = spans.flatMap { span ->
        if (span.type != type || span.end <= start || span.start >= end) {
            listOf(span)
        } else {
            listOfNotNull(
                span.copy(end = start).takeIf { it.start < start },
                span.copy(start = end).takeIf { end < it.end },
            )
        }
    }

    /**
     * 文字列の編集にスパンを追従させる。
     *
     * 段落スタイルの終端だけは、ちょうど末尾で書き足したときに一緒に伸ばす。
     * 見出し・箇条書きの行の末尾で打った文字はその行の続きなので、外に押し出しては困る
     * （行末で改行したときに新しい行へ引き継がれるのも、この伸びを使っている）。
     * 文字装飾では逆に、太字の直後に打った文字まで太字になってしまうので伸ばさない。
     */
    fun remap(
        spans: List<TextSpanAnnotation>,
        change: TextChange,
        newTextLength: Int,
    ): List<TextSpanAnnotation> {
        if (change.isEmpty) return normalize(spans, newTextLength)
        val shifted = spans.map { span ->
            span.copy(
                start = shift(span.start, change),
                end = shift(span.end, change, extendOnInsert = span.type.isParagraphStyle),
            )
        }
        return normalize(shifted, newTextLength)
    }

    private fun shift(offset: Int, change: TextChange, extendOnInsert: Boolean = false): Int {
        val removalEnd = change.start + change.removed
        return when {
            offset < change.start -> offset
            offset == change.start && !extendOnInsert -> offset
            offset >= removalEnd -> offset - change.removed + change.inserted
            // 削除された範囲の内側はまとめて削除開始位置へ寄せる
            else -> change.start
        }
    }

    /**
     * 旧テキストと新テキストから編集差分を求める。
     * 前後の共通部分を除いた中央部分だけが変更されたとみなす（IME の変換にも耐える近似）。
     */
    fun computeChange(oldText: String, newText: String): TextChange {
        if (oldText == newText) return TextChange(0, 0, 0)

        val maxPrefix = minOf(oldText.length, newText.length)
        var prefix = 0
        while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) prefix++

        val maxSuffix = maxPrefix - prefix
        var suffix = 0
        while (
            suffix < maxSuffix &&
            oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) suffix++

        return TextChange(
            start = prefix,
            removed = oldText.length - prefix - suffix,
            inserted = newText.length - prefix - suffix,
        )
    }

    /**
     * [at] 位置でスパンを2つに分割する。ブロックの途中に画像・動画を挟むときに使う。
     * 戻り値の second は分割後ブロックの先頭を 0 とした座標系に直してある。
     */
    fun split(
        spans: List<TextSpanAnnotation>,
        at: Int,
        textLength: Int,
    ): Pair<List<TextSpanAnnotation>, List<TextSpanAnnotation>> {
        val normalized = normalize(spans, textLength)

        val before = normalized.mapNotNull { span ->
            when {
                // 空行に付いた段落スタイルの目印。幅が無いので位置だけで振り分ける
                span.start == span.end -> span.takeIf { it.start < at }
                span.start < at -> span.copy(end = minOf(span.end, at))
                else -> null
            }
        }
        val after = normalized.mapNotNull { span ->
            when {
                span.start == span.end ->
                    span.takeIf { it.start >= at }?.copy(start = span.start - at, end = span.end - at)

                span.end > at -> {
                    val start = maxOf(span.start, at)
                    span.copy(start = start - at, end = span.end - at)
                }

                else -> null
            }
        }
        return before to after
    }
}
