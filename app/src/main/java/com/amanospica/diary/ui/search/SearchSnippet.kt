package com.amanospica.diary.ui.search

/**
 * 検索結果に出す抜粋と、その中で検索語に一致した位置。
 *
 * [highlights] は [text] 上の範囲（両端を含む [IntRange]）で、重なり合うものは1つにまとめてある。
 */
data class SearchSnippet(
    val text: String,
    val highlights: List<IntRange>,
) {
    companion object {
        val Empty = SearchSnippet(text = "", highlights = emptyList())
    }
}

/** 抜粋の長さ。カード上でおよそ2行に収まる分量。 */
private const val SNIPPET_LENGTH = 90

/** 一致箇所の手前に残す文字数。前後の流れが分かる程度に少しだけ遡る。 */
private const val SNIPPET_LEAD = 16

/**
 * 本文から、検索語が現れるあたりを抜き出す。
 *
 * 本文の先頭を切り出すだけだと、後ろのほうで一致した日記は「なぜ引っかかったのか」が
 * 分からない。一致箇所を含む窓を切り出し、前後を省いたことが分かるよう「…」で示す。
 *
 * 改行や連続した空白は1つの空白に潰す。カードでは数行しか出せないので、
 * 元の改行をそのまま持ち込むと数文字で行が尽きてしまうため。
 */
fun buildSearchSnippet(source: String, terms: List<String>): SearchSnippet {
    val text = source.replace(Regex("\\s+"), " ").trim()
    if (text.isEmpty()) return SearchSnippet.Empty

    // 本文に一致が無い（タイトルだけで引っかかった）場合は、素直に本文の頭から見せる
    val firstMatch = highlightRanges(text, terms).firstOrNull()?.first ?: 0
    val start = (firstMatch - SNIPPET_LEAD).coerceAtLeast(0)
    val end = (start + SNIPPET_LENGTH).coerceAtMost(text.length)

    val snippet = buildString {
        if (start > 0) append(ELLIPSIS)
        append(text, start, end)
        if (end < text.length) append(ELLIPSIS)
    }
    // 窓の端で語が切れた場合はその分は光らないが、切り出したあとの文字列を
    // 見たままに光らせるほうがずれが起きない
    return SearchSnippet(text = snippet, highlights = highlightRanges(snippet, terms))
}

/**
 * [text] の中で [terms] のいずれかに一致した範囲（両端を含む）を、前から順に返す。
 * 大文字小文字は区別せず、重なり合う範囲は1つにまとめる。
 */
fun highlightRanges(text: String, terms: List<String>): List<IntRange> {
    if (text.isEmpty()) return emptyList()

    val found = terms.filter { it.isNotEmpty() }.flatMap { term -> text.occurrencesOf(term) }
    if (found.isEmpty()) return emptyList()

    // 語同士が重なっていると光る帯が途切れて見えるので、つながるものは1本にする
    return found.sortedBy { it.first }.fold(mutableListOf<IntRange>()) { merged, range ->
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            merged.add(range)
        }
        merged
    }
}

/** [term] が現れる範囲をすべて拾う（開始は含む・終了は含まない）。 */
private fun String.occurrencesOf(term: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var from = indexOf(term, startIndex = 0, ignoreCase = true)
    while (from >= 0) {
        ranges.add(from until from + term.length)
        from = indexOf(term, startIndex = from + term.length, ignoreCase = true)
    }
    return ranges
}

private const val ELLIPSIS = "…"
