package com.amanospica.diary.data.backup

import android.content.Context
import android.net.Uri
import com.amanospica.diary.data.media.MediaStorage
import com.amanospica.diary.domain.model.ArchiveContents
import com.amanospica.diary.domain.model.BackupException
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.ExportSummary
import com.amanospica.diary.domain.repository.BackupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.format.DateTimeParseException
import java.util.zip.ZipException

/**
 * SAF で利用者が選んだ `content://` を開き、ZIP の中身の組み立ては [BackupArchive] に任せる実装。
 *
 * ここが受け持つのは URI の解決と、失敗理由の畳み込みだけ。
 */
class BackupRepositoryImpl(
    private val context: Context,
    storage: MediaStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BackupRepository {

    private val archive = BackupArchive(storage)

    override suspend fun exportTo(
        destinationUri: String,
        diaries: List<Diary>,
    ): Result<ExportSummary> = withContext(ioDispatcher) {
        runCatchingBackup { openOutput(destinationUri).use { archive.write(it, diaries) } }
    }

    override suspend fun readArchive(sourceUri: String): Result<ArchiveContents> =
        withContext(ioDispatcher) {
            runCatchingBackup { openInput(sourceUri).use { archive.read(it) } }
        }

    /** `wt` で開くのは、既にあるファイルへ上書きしたときに古い中身が末尾へ残らないようにするため。 */
    private fun openOutput(uri: String): OutputStream =
        context.contentResolver.openOutputStream(Uri.parse(uri), "wt")
            ?: throw IOException("書き出し先を開けません: $uri")

    private fun openInput(uri: String): InputStream =
        context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("読み込み元を開けません: $uri")

    /**
     * 失敗を [BackupException] に揃えて [Result] へ包む。
     *
     * ZIP として壊れている・別形式の JSON だった・日付が読めない、はどれも利用者から見れば
     * 「これはこのアプリのバックアップではない」なので同じ扱いにする。
     */
    private inline fun <T> runCatchingBackup(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: BackupException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(BackupException(e.toReason(), e))
    }

    private fun Exception.toReason(): BackupException.Reason = when (this) {
        is ZipException, is SerializationException, is DateTimeParseException ->
            BackupException.Reason.NOT_A_BACKUP

        else -> BackupException.Reason.IO
    }
}
