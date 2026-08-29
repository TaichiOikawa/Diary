package com.amanospica.diary.data.backup

import com.amanospica.diary.data.mapper.toDbDate
import com.amanospica.diary.data.mapper.toLocalDate
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.DiaryBlock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * バックアップ ZIP の `backup.json` の形式バージョン。
 *
 * 読み込み時、これより新しい番号のファイルは「知らない形式」として断る。
 * フィールドを足すだけの変更なら番号は据え置きでよい（`ignoreUnknownKeys` が吸収する）。
 * 既存フィールドの意味を変えるときだけ上げること。
 */
internal const val FORMAT_VERSION = 1

/**
 * `backup.json` のルート。
 *
 * [formatVersion] と [diaries] に既定値を置かないのは、無関係な JSON が
 * たまたま空のバックアップとして読めてしまうのを防ぐため。
 */
@Serializable
internal data class BackupDocument(
    val formatVersion: Int,
    /** 書き出した時刻（エポックミリ秒）。復元には使わないが、どれが新しいかを人が見分けられる。 */
    val exportedAt: Long,
    val diaries: List<BackupDiary>,
)

/**
 * 日記1件。[Diary] をそのまま使わないのは、`LocalDate` を持っていてシリアライズできないのと、
 * ドメインモデルの変更がそのままファイル形式の破壊にならないようにするため。
 */
@Serializable
internal data class BackupDiary(
    val id: String,
    /** `YYYY-MM-DD`。DB のカラムと同じ表記。 */
    val date: String,
    val emoji: String,
    val title: String? = null,
    val blocks: List<DiaryBlock> = emptyList(),
    val isDraft: Boolean = false,
    val isFavorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * バックアップ JSON の設定。
 *
 * 本文ブロックは DB の `blocksJson` と同じ形にしたいので、判別キーと既定値の扱いを
 * [com.amanospica.diary.data.local.DiaryJson] に揃える。
 * 整形して書き出すのは中身を人が覗けるようにするため（ZIP で圧縮されるのでサイズはほぼ変わらない）。
 */
internal val BackupJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
    explicitNulls = false
    prettyPrint = true
}

internal fun List<Diary>.toBackupDocument(exportedAt: Long): BackupDocument = BackupDocument(
    formatVersion = FORMAT_VERSION,
    exportedAt = exportedAt,
    diaries = map { it.toBackup() },
)

private fun Diary.toBackup(): BackupDiary = BackupDiary(
    id = id,
    date = date.toDbDate(),
    emoji = emoji,
    title = title,
    blocks = blocks,
    isDraft = isDraft,
    isFavorite = isFavorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun BackupDiary.toDomain(): Diary = Diary(
    id = id,
    date = date.toLocalDate(),
    emoji = emoji,
    title = title,
    blocks = blocks,
    isDraft = isDraft,
    isFavorite = isFavorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
