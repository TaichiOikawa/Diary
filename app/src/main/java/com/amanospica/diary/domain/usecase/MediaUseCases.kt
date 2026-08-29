package com.amanospica.diary.domain.usecase

import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.domain.repository.DiaryRepository
import com.amanospica.diary.domain.repository.MediaRepository

/**
 * 選択された画像を内部ストレージへ取り込み、本文へ挿入できる画像ブロックを返す。
 */
class AttachImageUseCase(private val repository: MediaRepository) {
    suspend operator fun invoke(sourceUri: String): Result<DiaryBlock.ImageBlock> =
        repository.saveImage(sourceUri).map { DiaryBlock.ImageBlock(localFilePath = it.relativePath) }
}

/**
 * 選択された動画を内部ストレージへ取り込み、本文へ挿入できる動画ブロックを返す。
 */
class AttachVideoUseCase(private val repository: MediaRepository) {
    suspend operator fun invoke(sourceUri: String): Result<DiaryBlock.VideoBlock> =
        repository.saveVideo(sourceUri).map { DiaryBlock.VideoBlock(localFilePath = it.relativePath) }
}

/** 相対パスを Coil / ExoPlayer が読める絶対パスへ解決する。 */
class ResolveMediaPathUseCase(private val repository: MediaRepository) {
    operator fun invoke(relativePath: String): String = repository.resolveAbsolutePath(relativePath)
}

/** 編集中に挿入したが保存されなかったメディアなど、参照されていないファイルを掃除する。 */
class CleanUpOrphanMediaUseCase(
    private val mediaRepository: MediaRepository,
    private val diaryRepository: DiaryRepository,
) {
    suspend operator fun invoke(): Int =
        mediaRepository.deleteOrphans(diaryRepository.getReferencedMediaPaths())
}
