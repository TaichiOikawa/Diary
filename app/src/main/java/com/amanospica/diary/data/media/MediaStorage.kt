package com.amanospica.diary.data.media

import android.content.Context
import java.io.File

/**
 * メディアの実体を置く内部ストレージ（`filesDir/media`）を受け持つ。
 *
 * 日記が持つのは `images/xxx.jpg` のような **相対パス** だけなので、
 * 相対パス ↔ 実ファイルの変換規約をここ 1 箇所に集約し、
 * 取り込み（[MediaRepositoryImpl]）とバックアップ（`data.backup`）が同じ規約を共有する。
 *
 * [Context] ではなくルートの [File] を受け取るのは、一時ディレクトリを渡せば
 * 端末なしのユニットテストからも同じ挙動を確かめられるようにするため。
 */
class MediaStorage(val root: File) {

    /** 相対パスに対応する実ファイル。存在するとは限らない。 */
    fun resolve(relativePath: String): File = File(root, relativePath)

    /** ルートからの相対パス。区切りは常に `/`（Windows 由来の `\` を混ぜない）。 */
    fun relativePathOf(file: File): String =
        file.relativeTo(root).path.replace(File.separatorChar, '/')

    /** 保存されている全メディアファイル。ルート未作成なら空。 */
    fun listAllFiles(): List<File> =
        if (root.exists()) root.walkTopDown().filter { it.isFile }.toList() else emptyList()

    /**
     * [file] がメディアディレクトリの内側を指しているか。
     *
     * ディレクトリ外への書き込み・削除を防ぐガード。
     * 外部から受け取った相対パス（バックアップ ZIP のエントリ名など）には必ず通すこと。
     */
    fun isWithinRoot(file: File): Boolean =
        file.canonicalPath.startsWith(root.canonicalPath + File.separator)

    companion object {
        private const val MEDIA_DIR = "media"

        fun forApp(context: Context): MediaStorage =
            MediaStorage(File(context.applicationContext.filesDir, MEDIA_DIR))
    }
}
