package com.amanospica.diary.domain.model

/**
 * 内部ストレージへ保存済みのメディア1件。
 *
 * [relativePath] は `filesDir/media` からの相対パス（例: `images/xxxx.jpg`）。
 * アプリ更新やバックアップ復元で `filesDir` の絶対パスが変わっても壊れないよう、
 * 永続化するのは相対パスのみとし、絶対パスは都度解決する。
 */
data class SavedMedia(
    val relativePath: String,
    val absolutePath: String,
    val sizeBytes: Long,
    /** 画像のみ。動画・不明時は 0。 */
    val width: Int = 0,
    /** 画像のみ。動画・不明時は 0。 */
    val height: Int = 0,
)
