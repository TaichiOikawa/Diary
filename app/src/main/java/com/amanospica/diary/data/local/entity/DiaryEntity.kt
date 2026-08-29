package com.amanospica.diary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amanospica.diary.domain.model.DiaryBlock
import java.util.UUID

/**
 * 日記エントリー本体のテーブル定義。
 *
 * [blocks] は TypeConverter によって JSON 文字列へ変換され、`blocksJson` カラムに格納される。
 * 日付は `YYYY-MM-DD` 形式の文字列。SQLite では辞書順＝日付順になるため、
 * カレンダーの月範囲検索（BETWEEN）と並び替えを追加のカラムなしで賄える。
 */
@Entity(
    tableName = "diaries",
    indices = [
        Index(value = ["date"]),
        Index(value = ["createdAt"]),
    ],
)
data class DiaryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** 日付 (YYYY-MM-DD 形式 / カレンダー検索用) */
    val date: String,
    /** 選択されたプリセット絵文字 */
    val emoji: String,
    /** タイトル（任意） */
    val title: String?,
    /** Notion 風コンテンツブロック構造（JSON シリアライズして保存） */
    @ColumnInfo(name = "blocksJson") val blocks: List<DiaryBlock>,
    /** 書きかけ（下書き）フラグ。既存の行は「書き終わった日記」として false で埋める（v2 マイグレーション） */
    @ColumnInfo(defaultValue = "0") val isDraft: Boolean = false,
    /** お気に入り（星）フラグ。既存の行は星なしとして false で埋める（v3 マイグレーション） */
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    /** 作成日時（エポックミリ秒 / 同日内の最新判定用） */
    val createdAt: Long,
    /** 最終更新日時（エポックミリ秒） */
    val updatedAt: Long,
)
