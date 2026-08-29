package com.amanospica.diary.domain.usecase

import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.ExportSummary
import com.amanospica.diary.domain.model.ImportSummary
import com.amanospica.diary.domain.repository.BackupRepository
import com.amanospica.diary.domain.repository.DiaryRepository

/**
 * 全日記と、本文から参照されている写真・動画を1つの ZIP へ書き出す。
 */
class ExportDiariesUseCase(
    private val backupRepository: BackupRepository,
    private val diaryRepository: DiaryRepository,
) {
    suspend operator fun invoke(destinationUri: String): Result<ExportSummary> =
        backupRepository.exportTo(destinationUri, diaryRepository.getAllDiaries())
}

/**
 * ZIP から日記を読み込み、いま端末にある日記へ混ぜ合わせる。
 *
 * 端末側の日記は消さない。同じ日記（同一 ID）があるときは [Diary.updatedAt] が新しい方を残すので、
 * 古いバックアップを読んでも書きたての内容が巻き戻ることはない。
 *
 * 最後に孤児メディアを掃除するのは、ZIP 内の写真・動画が日記より先に復元されるため。
 * 「端末側の方が新しいので採用しなかった」日記の分もいったんは書き込まれるので、ここで回収する。
 */
class ImportDiariesUseCase(
    private val backupRepository: BackupRepository,
    private val diaryRepository: DiaryRepository,
    private val cleanUpOrphanMedia: CleanUpOrphanMediaUseCase,
) {
    suspend operator fun invoke(sourceUri: String): Result<ImportSummary> =
        backupRepository.readArchive(sourceUri).mapCatching { contents ->
            val current = diaryRepository.getAllDiaries().associateBy { it.id }
            val accepted = mutableListOf<Diary>()
            var updated = 0
            var skipped = 0

            contents.diaries.forEach { incoming ->
                val existing = current[incoming.id]
                when {
                    existing == null -> accepted += incoming
                    incoming.updatedAt > existing.updatedAt -> {
                        accepted += incoming
                        updated++
                    }

                    else -> skipped++
                }
            }

            diaryRepository.importDiaries(accepted)
            cleanUpOrphanMedia()

            ImportSummary(
                added = accepted.size - updated,
                updated = updated,
                skipped = skipped,
                mediaRestored = contents.mediaRestored,
            )
        }
}
