package com.amanospica.diary.ui.navigation

import kotlinx.serialization.Serializable

/** メイン3画面（HorizontalPager）。 */
@Serializable
data object MainRoute

/** 設定画面（ロック・外観）。 */
@Serializable
data object SettingsRoute

/** 日記をタイトル・本文から探す検索画面。検索語は画面が開いている間だけ持つ。 */
@Serializable
data object SearchRoute

/**
 * 日記の新規作成・編集画面。
 *
 * @param diaryId 既存日記を開く場合の ID。新規作成なら null。
 * @param date 新規作成時の初期日付（`YYYY-MM-DD`）。カレンダーから来た場合は選択日。
 */
@Serializable
data class EditorRoute(
    val diaryId: String? = null,
    val date: String? = null,
)

/**
 * 書き終わった日記を読む画面。編集はできず、鉛筆から [EditorRoute] へ移る。
 *
 * @param diaryId 開く日記の ID。
 */
@Serializable
data class ViewerRoute(val diaryId: String)
