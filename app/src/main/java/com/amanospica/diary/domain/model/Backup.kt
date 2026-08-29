package com.amanospica.diary.domain.model

/** 書き出しの結果。 */
data class ExportSummary(
    val diaryCount: Int,
    /** 一緒に書き出した写真・動画の件数。 */
    val mediaCount: Int,
)

/**
 * バックアップから読み出した中身。
 * メディアはこの時点で内部ストレージへ復元済みで、日記はまだ DB に入っていない。
 */
data class ArchiveContents(
    val diaries: List<Diary>,
    val mediaRestored: Int,
)

/** 読み込みの結果。 */
data class ImportSummary(
    /** 端末に無かったので新しく増えた件数。 */
    val added: Int,
    /** 同じ日記の、より新しい内容で置き換えた件数。 */
    val updated: Int,
    /** 端末側が同じか新しいので触らなかった件数。 */
    val skipped: Int,
    /** 復元した写真・動画の件数。 */
    val mediaRestored: Int,
)

/**
 * バックアップの読み書きに失敗した理由。
 *
 * 例外の文言をそのまま画面に出すと利用者には意味が分からないので、
 * 原因を数種類に畳んでおき、UI 側でメッセージへ読み替える。
 */
class BackupException(
    val reason: Reason,
    cause: Throwable? = null,
) : Exception(reason.name, cause) {

    enum class Reason {
        /** ZIP として読めない、または backup.json が入っていない。 */
        NOT_A_BACKUP,

        /** 新しいバージョンのアプリで書き出されていて読めない。 */
        UNSUPPORTED_VERSION,

        /** ファイルの読み書き自体に失敗した（空き容量不足・権限切れなど）。 */
        IO,
    }
}
