package com.amanospica.diary.domain.usecase

import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.domain.repository.DiaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * 日記をタイトル・本文から探す。
 *
 * 絞り込みはタイムラインと同じ購読に掛ける。件数が数千程度までの個人の日記では
 * 全件を持っていても軽く、日記を書き換えれば結果もその場で追従する
 * （検索語ごとに DB へ問い合わせ直さない）。
 *
 * 並びは [ObserveTimelineUseCase] のまま（日付降順）で、一致度では並べ替えない。
 * 「いつ書いたか」で辿るほうが日記では見つけやすいため。
 */
class SearchDiariesUseCase(private val repository: DiaryRepository) {

    /**
     * @param terms [toSearchTerms] で切り出した検索語。空なら結果も空（全件は返さない）。
     */
    operator fun invoke(terms: List<String>): Flow<List<Diary>> {
        if (terms.isEmpty()) return flowOf(emptyList())
        return repository.observeTimeline().map { diaries ->
            diaries.filter { it.matches(terms) }
        }
    }
}

/** 語の区切り。半角空白だけでなく全角空白（U+3000）でも切る。 */
private val TERM_SEPARATOR = Regex("[\\s　]+")

/**
 * 入力欄の文字列を検索語へ切り出す。
 *
 * 空白区切りの語はすべて含む日記だけを残す（AND 検索）。「海 ドライブ」のように
 * 覚えている単語を並べて絞り込めるほうが、長い一文をそのまま打つより当てやすい。
 */
fun String.toSearchTerms(): List<String> =
    trim().split(TERM_SEPARATOR).filter { it.isNotEmpty() }

/** 本文（テキストブロック）だけをつないだ文字列。写真・動画は検索の対象外。 */
fun Diary.bodyText(): String =
    blocks.filterIsInstance<DiaryBlock.TextBlock>().joinToString(separator = "\n") { it.text }

/** 検索対象の文字列。タイトルと本文をつなげたもの。 */
fun Diary.searchText(): String =
    listOfNotNull(title, bodyText().takeIf { it.isNotBlank() }).joinToString(separator = "\n")

/** すべての検索語を含むか。大文字小文字は区別しない。 */
private fun Diary.matches(terms: List<String>): Boolean {
    val text = searchText()
    return terms.all { term -> text.contains(term, ignoreCase = true) }
}
