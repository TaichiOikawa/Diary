package com.amanospica.diary.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

@RunWith(AndroidJUnit4::class)
class MediaRepositoryImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: MediaRepositoryImpl
    private lateinit var workDir: File

    @Before
    fun setUp() {
        repository = MediaRepositoryImpl(context)
        workDir = File(context.cacheDir, "media-test").apply { mkdirs() }
        File(context.filesDir, "media").deleteRecursively()
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
        File(context.filesDir, "media").deleteRecursively()
    }

    @Test
    fun largeImageIsScaledDownToFullHdAndStoredInsideAppStorage() = runTest {
        val source = createJpeg(width = 4000, height = 3000, name = "large.jpg")

        val saved = repository.saveImage(Uri.fromFile(source).toString()).getOrThrow()

        assertEquals(1920, max(saved.width, saved.height))
        // 元の縦横比 4:3 が保たれている
        assertEquals(1440, minOf(saved.width, saved.height))

        val stored = File(saved.absolutePath)
        assertTrue(stored.exists())
        assertTrue(stored.absolutePath.startsWith(context.filesDir.absolutePath))
        assertTrue("圧縮後の方が小さいはず", stored.length() < source.length())
        assertEquals("images/${stored.name}", saved.relativePath)
    }

    @Test
    fun smallImageKeepsItsOriginalSize() = runTest {
        val source = createJpeg(width = 640, height = 480, name = "small.jpg")

        val saved = repository.saveImage(Uri.fromFile(source).toString()).getOrThrow()

        assertEquals(640, saved.width)
        assertEquals(480, saved.height)
    }

    @Test
    fun relativePathResolvesBackToTheStoredFile() = runTest {
        val saved = repository.saveImage(
            Uri.fromFile(createJpeg(800, 600, "resolve.jpg")).toString()
        ).getOrThrow()

        assertEquals(saved.absolutePath, repository.resolveAbsolutePath(saved.relativePath))
    }

    @Test
    fun invalidSourceReturnsFailureWithoutLeavingAFile() = runTest {
        val broken = File(workDir, "broken.jpg").apply { writeText("これは画像ではありません") }

        val result = repository.saveImage(Uri.fromFile(broken).toString())

        assertTrue(result.isFailure)
        val imageDir = File(context.filesDir, "media/images")
        assertTrue("半端なファイルが残ってはいけない", imageDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun deleteRemovesTheStoredFile() = runTest {
        val saved = repository.saveImage(
            Uri.fromFile(createJpeg(800, 600, "delete.jpg")).toString()
        ).getOrThrow()

        repository.delete(saved.relativePath)

        assertFalse(File(saved.absolutePath).exists())
    }

    @Test
    fun deleteOrphansKeepsOnlyReferencedFiles() = runTest {
        val kept = repository.saveImage(
            Uri.fromFile(createJpeg(400, 300, "kept.jpg")).toString()
        ).getOrThrow()
        val orphan = repository.saveImage(
            Uri.fromFile(createJpeg(400, 300, "orphan.jpg")).toString()
        ).getOrThrow()

        val deleted = repository.deleteOrphans(setOf(kept.relativePath))

        assertEquals(1, deleted)
        assertTrue(File(kept.absolutePath).exists())
        assertFalse(File(orphan.absolutePath).exists())
    }

    @Test
    fun pathTraversalIsRejected() = runTest {
        val target = File(context.filesDir, "secret.txt").apply { writeText("消えては困る") }

        repository.delete("../secret.txt")

        assertTrue(target.exists())
        target.delete()
    }

    private fun createJpeg(width: Int, height: Int, name: String): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            // 一様な塗りだと圧縮が効きすぎるため、ノイズ代わりの矩形を置く
            for (i in 0 until 40) {
                drawRect(
                    (i * 37 % width).toFloat(),
                    (i * 53 % height).toFloat(),
                    (i * 37 % width + width / 8f),
                    (i * 53 % height + height / 8f),
                    android.graphics.Paint().apply { color = Color.rgb(i * 6, 255 - i * 5, i * 3) },
                )
            }
        }
        val file = File(workDir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()

        // 出力が想定どおりのサイズで書けたことを確認しておく
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        check(bounds.outWidth == width && bounds.outHeight == height)
        return file
    }
}
