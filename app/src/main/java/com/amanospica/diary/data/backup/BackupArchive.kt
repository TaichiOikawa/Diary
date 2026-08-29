package com.amanospica.diary.data.backup

import com.amanospica.diary.data.media.MediaStorage
import com.amanospica.diary.domain.model.ArchiveContents
import com.amanospica.diary.domain.model.BackupException
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.ExportSummary
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 日記のバックアップ ZIP を読み書きする。
 *
 * ```
 * backup.json      全日記（本文ブロックは DB と同じ JSON 形式）
 * media/images/    本文から参照されている画像
 * media/videos/    本文から参照されている動画
 * ```
 *
 * 受け取るのはストリームだけで、`content://` の解決は [BackupRepositoryImpl] に任せる。
 * おかげで書き出し→読み込みの往復を、端末なしのユニットテストでそのまま確かめられる。
 */
internal class BackupArchive(private val storage: MediaStorage) {

    /** [diaries] と、それらが参照しているメディアを [output] へ ZIP として書き出す。 */
    fun write(
        output: OutputStream,
        diaries: List<Diary>,
        exportedAt: Long = System.currentTimeMillis(),
    ): ExportSummary {
        var mediaCount = 0
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putEntry(MANIFEST_ENTRY) { out ->
                out.write(BackupJson.encodeToString(diaries.toBackupDocument(exportedAt)).toByteArray())
            }
            // 本文から参照されているものだけを詰める（どこからも使われていない残骸は持ち出さない）
            diaries.flatMap { it.mediaPaths }.distinct().forEach { path ->
                val file = storage.resolve(path)
                if (!storage.isWithinRoot(file) || !file.isFile) return@forEach
                zip.putEntry(MEDIA_PREFIX + path) { out -> file.inputStream().use { it.copyTo(out) } }
                mediaCount++
            }
        }
        return ExportSummary(diaryCount = diaries.size, mediaCount = mediaCount)
    }

    /**
     * [input] の ZIP からメディアを内部ストレージへ復元し、日記の一覧を読み出す。
     *
     * メディアは日記より先に書き込まれる。読み込みを採用しなかった日記の分まで
     * 一度は復元されるので、呼び出し側は読み込み後に孤児メディアを掃除すること。
     */
    fun read(input: InputStream): ArchiveContents {
        var document: BackupDocument? = null
        var restored = 0
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    entry.isDirectory -> Unit
                    name == MANIFEST_ENTRY ->
                        document = BackupJson.decodeFromString(zip.readBytes().decodeToString())

                    name.startsWith(MEDIA_PREFIX) ->
                        if (restoreMedia(name.removePrefix(MEDIA_PREFIX), zip)) restored++
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val manifest = document ?: throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        if (manifest.formatVersion > FORMAT_VERSION) {
            throw BackupException(BackupException.Reason.UNSUPPORTED_VERSION)
        }
        return ArchiveContents(
            diaries = manifest.diaries.map { it.toDomain() },
            mediaRestored = restored,
        )
    }

    /**
     * ZIP 内のメディア1件を media ディレクトリへ復元する。実際に書けたら true。
     *
     * エントリ名は外部から渡されたファイル由来なので、`../` でディレクトリの外へ
     * 抜ける細工を必ず弾く。同名のファイルが既にある場合は端末側を残す
     * （ファイル名は日時＋UUID なので、中身の違う別ファイルが同名になることは実質ない）。
     */
    private fun restoreMedia(relativePath: String, source: InputStream): Boolean {
        if (relativePath.isBlank()) return false
        val destination = storage.resolve(relativePath)
        if (!storage.isWithinRoot(destination) || destination.exists()) return false
        destination.parentFile?.mkdirs()
        destination.outputStream().use { source.copyTo(it) }
        return true
    }

    private inline fun ZipOutputStream.putEntry(name: String, write: (ZipOutputStream) -> Unit) {
        putNextEntry(ZipEntry(name))
        write(this)
        closeEntry()
    }

    private companion object {
        const val MANIFEST_ENTRY = "backup.json"
        const val MEDIA_PREFIX = "media/"
    }
}
