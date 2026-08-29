package com.amanospica.diary.domain.repository

import com.amanospica.diary.domain.model.SavedMedia

/**
 * 端末のギャラリー等から選択されたメディアを、アプリの内部ストレージへ取り込む契約。
 *
 * `sourceUri` は `content://` などの URI 文字列。Android の `Uri` 型に依存しないよう
 * ドメイン層では文字列で受け取り、data 層でパースする。
 */
interface MediaRepository {

    /** 画像をフルHD相当へ縮小・JPEG 圧縮して内部ストレージへ保存する。 */
    suspend fun saveImage(sourceUri: String): Result<SavedMedia>

    /** 動画を内部ストレージへコピーする（再エンコードはしない）。 */
    suspend fun saveVideo(sourceUri: String): Result<SavedMedia>

    /** 相対パスから実ファイルの絶対パスを解決する（Coil / ExoPlayer へ渡す用）。 */
    fun resolveAbsolutePath(relativePath: String): String

    /** メディアファイルを1件削除する。 */
    suspend fun delete(relativePath: String)

    /**
     * どの日記からも参照されていないメディアファイルを削除し、削除件数を返す。
     * [referencedPaths] には現存する全日記のメディア相対パスを渡す。
     */
    suspend fun deleteOrphans(referencedPaths: Set<String>): Int
}
