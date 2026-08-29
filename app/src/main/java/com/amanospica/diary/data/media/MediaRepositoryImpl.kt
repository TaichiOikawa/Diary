package com.amanospica.diary.data.media

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.amanospica.diary.domain.model.SavedMedia
import com.amanospica.diary.domain.repository.MediaRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * メディアを端末の共有領域から切り離し、アプリ専用の内部ストレージ
 * （`filesDir/media`）へ取り込む実装。
 *
 * - 内部ストレージなので他アプリからは参照できず、アンインストールで確実に消える（プライバシー要件）
 * - 元の `content://` URI は権限が失効すると読めなくなるため、必ず実体をコピーする
 * - 日記に保存するのは `images/xxx.jpg` のような **相対パス**（絶対パスは端末・復元で変わりうる）
 */
class MediaRepositoryImpl(
    private val context: Context,
    private val storage: MediaStorage = MediaStorage.forApp(context),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MediaRepository {

    private val compressor = ImageCompressor(context)

    override suspend fun saveImage(sourceUri: String): Result<SavedMedia> =
        withContext(ioDispatcher) {
            runCatching {
                val destination = newFile(IMAGE_DIR, "jpg")
                writeAtomically(destination) { temp ->
                    val compressed = compressor.compressTo(Uri.parse(sourceUri), temp)
                    compressed.width to compressed.height
                }.let { (width, height) ->
                    destination.toSavedMedia(width, height)
                }
            }
        }

    override suspend fun saveVideo(sourceUri: String): Result<SavedMedia> =
        withContext(ioDispatcher) {
            runCatching {
                val uri = Uri.parse(sourceUri)
                val destination = newFile(VIDEO_DIR, videoExtension(uri))
                writeAtomically(destination) { temp -> copyStream(uri, temp) }
                destination.toSavedMedia()
            }
        }

    override fun resolveAbsolutePath(relativePath: String): String =
        storage.resolve(relativePath).absolutePath

    override suspend fun delete(relativePath: String) {
        withContext(ioDispatcher) {
            val file = storage.resolve(relativePath)
            if (storage.isWithinRoot(file) && file.exists()) file.delete()
        }
    }

    override suspend fun deleteOrphans(referencedPaths: Set<String>): Int =
        withContext(ioDispatcher) {
            storage.listAllFiles()
                .filterNot { storage.relativePathOf(it) in referencedPaths }
                .count { it.delete() }
        }

    /**
     * 一時ファイルへ書き切ってから本ファイルへ差し替える。
     * 途中で失敗したときに、読めない半端なファイルが日記から参照されるのを防ぐ。
     */
    private fun <T> writeAtomically(destination: File, write: (File) -> T): T {
        val temp = File(destination.parentFile, destination.name + ".tmp")
        try {
            val result = write(temp)
            if (destination.exists()) destination.delete()
            if (!temp.renameTo(destination)) {
                throw IOException("メディアの保存に失敗しました: ${destination.name}")
            }
            return result
        } catch (e: Throwable) {
            temp.delete()
            throw e
        }
    }

    private fun copyStream(source: Uri, destination: File) {
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("メディアを開けません: $source")
        input.use { stream ->
            FileOutputStream(destination).use { out ->
                stream.copyTo(out, DEFAULT_BUFFER_SIZE)
                out.flush()
            }
        }
    }

    private fun newFile(subDir: String, extension: String): File {
        val dir = storage.resolve(subDir)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("メディア保存先を作成できません: ${dir.absolutePath}")
        }
        val stamp = LocalDateTime.now().format(FILE_NAME_FORMATTER)
        val unique = UUID.randomUUID().toString().take(8)
        return File(dir, "${stamp}_$unique.$extension")
    }

    private fun videoExtension(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        return mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
            ?: DEFAULT_VIDEO_EXTENSION
    }

    private fun File.toSavedMedia(width: Int = 0, height: Int = 0) = SavedMedia(
        relativePath = storage.relativePathOf(this),
        absolutePath = absolutePath,
        sizeBytes = length(),
        width = width,
        height = height,
    )

    private companion object {
        const val IMAGE_DIR = "images"
        const val VIDEO_DIR = "videos"
        const val DEFAULT_VIDEO_EXTENSION = "mp4"
        val FILE_NAME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }
}
