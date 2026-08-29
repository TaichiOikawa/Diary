package com.amanospica.diary.data.backup

import com.amanospica.diary.data.media.MediaStorage
import com.amanospica.diary.domain.model.BackupException
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.domain.model.SpanType
import com.amanospica.diary.domain.model.TextSpanAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * バックアップ ZIP の書き出し／読み込みを確かめる。
 *
 * 要点は、書いたものがそのまま戻ること（ブロック ID や装飾まで含めて）、
 * 写真の実体も一緒に往復すること、そして外から渡されたファイルを読むので
 * 壊れた・細工されたエントリで端末側を壊さないこと。
 */
class BackupArchiveTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var mediaRoot: File
    private lateinit var archive: BackupArchive

    @Before
    fun setUp() {
        mediaRoot = temporaryFolder.newFolder("media")
        archive = BackupArchive(MediaStorage(mediaRoot))
    }

    @Test
    fun `書き出した日記はそのままの形で読み戻せる`() {
        val original = diary(
            id = "a",
            blocks = listOf(
                DiaryBlock.TextBlock(
                    id = "block-1",
                    text = "海に行った",
                    spans = listOf(TextSpanAnnotation(0, 2, SpanType.BOLD)),
                ),
            ),
        ).copy(title = "夏の記録", isFavorite = true, isDraft = true)

        val restored = roundTrip(listOf(original)).diaries

        // ブロック ID・装飾範囲・星・下書きまで落ちずに戻ること
        assertEquals(listOf(original), restored)
    }

    @Test
    fun `本文が参照する写真も一緒に往復する`() {
        val photo = writeMedia("images/photo.jpg", "写真のバイト列")
        val original = diary(
            id = "a",
            blocks = listOf(
                DiaryBlock.TextBlock(id = "block-1", text = "海に行った"),
                DiaryBlock.ImageBlock(id = "block-2", localFilePath = "images/photo.jpg"),
            ),
        )
        val archived = write(listOf(original))

        // 端末から写真が消えた状態（機種変更・再インストール）を作ってから読み込む
        assertTrue(photo.delete())
        val contents = archive.read(ByteArrayInputStream(archived))

        assertEquals(listOf(original), contents.diaries)
        assertEquals(1, contents.mediaRestored)
        assertEquals("写真のバイト列", photo.readText())
    }

    @Test
    fun `どこからも参照されていないメディアは書き出さない`() {
        writeMedia("images/used.jpg", "使っている")
        writeMedia("images/orphan.jpg", "使っていない")
        val entry = diary(
            id = "a",
            blocks = listOf(DiaryBlock.ImageBlock(id = "block-1", localFilePath = "images/used.jpg")),
        )

        val summary = ByteArrayOutputStream().let { archive.write(it, listOf(entry)) }

        assertEquals(1, summary.diaryCount)
        assertEquals(1, summary.mediaCount)
    }

    @Test
    fun `端末に既にある同名の写真は上書きしない`() {
        writeMedia("images/photo.jpg", "書き出したときの中身")
        val entry = diary(
            id = "a",
            blocks = listOf(DiaryBlock.ImageBlock(id = "block-1", localFilePath = "images/photo.jpg")),
        )
        val archived = write(listOf(entry))

        writeMedia("images/photo.jpg", "端末側で差し替わった中身")
        val contents = archive.read(ByteArrayInputStream(archived))

        assertEquals(0, contents.mediaRestored)
        assertEquals("端末側で差し替わった中身", File(mediaRoot, "images/photo.jpg").readText())
    }

    @Test
    fun `メディアディレクトリの外を指すエントリは復元しない`() {
        val escaped = File(temporaryFolder.root, "escaped.txt")
        val crafted = zipOf(
            "backup.json" to EMPTY_MANIFEST.toByteArray(),
            "media/../escaped.txt" to "外に置きたいファイル".toByteArray(),
        )

        val contents = archive.read(ByteArrayInputStream(crafted))

        assertEquals(0, contents.mediaRestored)
        assertFalse(escaped.exists())
    }

    @Test
    fun `backup_json が無いファイルは読み込めない`() {
        val notABackup = zipOf("readme.txt" to "ただの ZIP".toByteArray())

        val reason = readFailureReason(notABackup)

        assertEquals(BackupException.Reason.NOT_A_BACKUP, reason)
    }

    @Test
    fun `新しい形式で書き出されたファイルは読み込まない`() {
        val fromFuture = zipOf(
            "backup.json" to """{"formatVersion":99,"exportedAt":0,"diaries":[]}""".toByteArray(),
        )

        val reason = readFailureReason(fromFuture)

        assertEquals(BackupException.Reason.UNSUPPORTED_VERSION, reason)
    }

    private fun readFailureReason(archived: ByteArray): BackupException.Reason = try {
        archive.read(ByteArrayInputStream(archived))
        error("読み込めてはいけない")
    } catch (e: BackupException) {
        e.reason
    }

    private fun roundTrip(diaries: List<Diary>) =
        archive.read(ByteArrayInputStream(write(diaries)))

    private fun write(diaries: List<Diary>): ByteArray =
        ByteArrayOutputStream().also { archive.write(it, diaries) }.toByteArray()

    private fun writeMedia(relativePath: String, text: String): File =
        File(mediaRoot, relativePath).apply {
            parentFile?.mkdirs()
            writeText(text)
        }

    /** 細工された ZIP を組み立てる（アプリが書き出したものだけが来るとは限らないため）。 */
    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun diary(
        id: String,
        blocks: List<DiaryBlock> = listOf(DiaryBlock.TextBlock(id = "block-1", text = "本文")),
    ) = Diary(
        id = id,
        date = LocalDate.of(2026, 8, 13),
        emoji = "🙂",
        blocks = blocks,
        createdAt = 50L,
        updatedAt = 100L,
    )

    private companion object {
        const val EMPTY_MANIFEST = """{"formatVersion":1,"exportedAt":0,"diaries":[]}"""
    }
}
