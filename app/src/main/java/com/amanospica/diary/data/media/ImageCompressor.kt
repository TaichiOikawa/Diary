package com.amanospica.diary.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** 圧縮結果のサイズ情報。 */
data class CompressedImage(
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
)

/**
 * 選択された画像をフルHD相当まで縮小し、JPEG で保存する。
 *
 * 手順:
 *  1. 画像サイズだけ先読みして `inSampleSize`（2の冪）を決め、デコード時のメモリを抑える
 *  2. EXIF の回転情報を反映する（撮って出しの写真が横倒しになるのを防ぐ）
 *  3. 長辺が [maxDimension] を超える分をきっちり縮小する
 *  4. JPEG へ圧縮して保存
 */
internal class ImageCompressor(
    private val context: Context,
    private val maxDimension: Int = MAX_DIMENSION,
    private val quality: Int = JPEG_QUALITY,
) {

    @Throws(IOException::class)
    fun compressTo(source: Uri, destination: File): CompressedImage {
        val bounds = readBounds(source)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("画像としてデコードできません: $source")
        }

        val orientation = readOrientation(source)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        var bitmap = openStream(source).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IOException("画像のデコードに失敗しました: $source")

        try {
            bitmap = applyOrientation(bitmap, orientation)
            bitmap = scaleDownIfNeeded(bitmap)

            FileOutputStream(destination).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    throw IOException("画像の圧縮に失敗しました: $source")
                }
                out.flush()
            }
            return CompressedImage(bitmap.width, bitmap.height, destination.length())
        } finally {
            bitmap.recycle()
        }
    }

    private fun readBounds(source: Uri): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(source).use { BitmapFactory.decodeStream(it, null, options) }
        return options
    }

    private fun readOrientation(source: Uri): Int = try {
        openStream(source).use { ExifInterface(it).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        ) }
    } catch (e: IOException) {
        // EXIF を持たない形式（PNG 等）は回転補正不要
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun openStream(source: Uri) = context.contentResolver.openInputStream(source)
        ?: throw IOException("メディアを開けません: $source")

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap): Bitmap {
        val longestEdge = max(bitmap.width, bitmap.height)
        if (longestEdge <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / longestEdge
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    companion object {
        /** フルHD相当。長辺をこのピクセル数に収める。 */
        const val MAX_DIMENSION = 1920
        const val JPEG_QUALITY = 85

        /**
         * デコード時の間引き率。長辺が [maxDimension] を下回らない範囲で最大の2の冪を返す。
         * （ここで縮めすぎると画質が落ちるため、最終調整は [scaleDownIfNeeded] に任せる）
         */
        fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
            var sampleSize = 1
            var longestEdge = max(width, height)
            while (longestEdge / 2 >= maxDimension) {
                longestEdge /= 2
                sampleSize *= 2
            }
            return sampleSize
        }
    }
}
