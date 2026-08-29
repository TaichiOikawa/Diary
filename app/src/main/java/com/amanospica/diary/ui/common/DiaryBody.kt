package com.amanospica.diary.ui.common

import com.amanospica.diary.domain.model.DiaryBlock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 本文の表示単位。続けて並んだ画像・動画は1つのまとまりとして横に敷き詰める。 */
sealed interface DiaryBodyRow {
    data class Text(val block: DiaryBlock.TextBlock, val isFirstBlock: Boolean) : DiaryBodyRow

    data class Media(val blocks: List<DiaryBlock>) : DiaryBodyRow
}

/**
 * ブロックの列を表示単位へ畳む。連続するメディアだけがひとまとまりになる。
 * 編集画面と閲覧画面で同じ畳み方を使うので、書いたときと読むときで並びが変わらない。
 */
fun List<DiaryBlock>.toBodyRows(): List<DiaryBodyRow> {
    val rows = mutableListOf<DiaryBodyRow>()
    var media = mutableListOf<DiaryBlock>()

    fun flushMedia() {
        if (media.isNotEmpty()) {
            rows += DiaryBodyRow.Media(media)
            media = mutableListOf()
        }
    }

    forEachIndexed { index, block ->
        if (block is DiaryBlock.TextBlock) {
            flushMedia()
            rows += DiaryBodyRow.Text(block, isFirstBlock = index == 0)
        } else {
            media += block
        }
    }
    flushMedia()
    return rows
}

private val ENTRY_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日(E)", Locale.JAPANESE)

/** 編集画面・閲覧画面の見出しに出す日付の表記。 */
fun LocalDate.toEntryDateLabel(): String = format(ENTRY_DATE_FORMATTER)
