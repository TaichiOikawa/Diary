package com.amanospica.diary.domain.model

import java.time.LocalDate
import java.util.UUID

/**
 * 日記エントリー1件を表すドメインモデル。
 *
 * 1日に複数件作成できるため、[date] は一意ではない。同一日の並び順は [createdAt] の降順。
 */
data class Diary(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val emoji: String,
    val title: String? = null,
    val blocks: List<DiaryBlock> = listOf(DiaryBlock.TextBlock()),
    /**
     * 書きかけ（下書き）かどうか。
     *
     * 新規作成した日記は自動保存された時点では下書きで、編集画面で「完了」にしたときに false になる。
     * 既存の日記を開いて書き足しただけでは状態は変わらない。
     */
    val isDraft: Boolean = false,
    /**
     * お気に入り（星）を付けた日記かどうか。
     *
     * 読み返したい日記に印を付けるためのもので、本文とは独立して付け外しできる。
     * タイムラインではこの印が付いた日記だけに絞り込める。
     */
    val isFavorite: Boolean = false,
    /** 作成日時（エポックミリ秒）。 */
    val createdAt: Long = System.currentTimeMillis(),
    /** 最終更新日時（エポックミリ秒）。 */
    val updatedAt: Long = createdAt,
) {
    /** タイムラインカードに表示する本文プレビュー。 */
    val preview: String get() = blocks.plainTextPreview()

    /** 保存済みメディアの相対パス一覧。 */
    val mediaPaths: List<String> get() = blocks.mediaFilePaths()

    /** 本文・タイトル・メディアがすべて空なら未入力とみなす。 */
    val isBlank: Boolean
        get() = title.isNullOrBlank() && blocks.all { it is DiaryBlock.TextBlock && it.text.isBlank() }
}

/**
 * カレンダーのマス目に表示するその日のサマリー。
 * [emoji] はその日の最新（[Diary.createdAt] が最大）の日記の絵文字。
 */
data class CalendarDayMarker(
    val date: LocalDate,
    val emoji: String,
    val count: Int,
    /** その日に書きかけの日記があるか。マス目に印を出して続きを書けることを示す。 */
    val hasDraft: Boolean = false,
)
