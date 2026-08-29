package com.amanospica.diary.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.amanospica.diary.data.local.entity.DiaryEntity
import kotlinx.coroutines.flow.Flow

/** カレンダー1マス分の集計結果。 */
data class CalendarDayMarkerRow(
    val date: String,
    val emoji: String,
    val entryCount: Int,
    /** その日の書きかけ（下書き）の件数。 */
    val draftCount: Int,
)

/** 絵文字ごとの投稿数（ダッシュボード用）。 */
data class EmojiCountRow(
    val emoji: String,
    val entryCount: Int,
)

/** 月ごとの投稿数（ダッシュボード用）。[yearMonth] は `YYYY-MM`。 */
data class MonthlyCountRow(
    val yearMonth: String,
    val entryCount: Int,
)

@Dao
interface DiaryDao {

    /** タイムライン（ホーム）: 日付降順 → 同日内は作成日時降順。 */
    @Query("SELECT * FROM diaries ORDER BY date DESC, createdAt DESC")
    fun observeTimeline(): Flow<List<DiaryEntity>>

    /** 指定日の日記一覧。1日に複数件ある前提で新しい順に返す。 */
    @Query("SELECT * FROM diaries WHERE date = :date ORDER BY createdAt DESC")
    fun observeByDate(date: String): Flow<List<DiaryEntity>>

    /**
     * カレンダー用の日別サマリー。
     * 絵文字は「その日の最新の日記」のもの。createdAt が同値のときは id で決定的に選ぶ。
     */
    @Query(
        """
        SELECT d.date AS date,
               (SELECT latest.emoji FROM diaries AS latest
                 WHERE latest.date = d.date
                 ORDER BY latest.createdAt DESC, latest.id DESC
                 LIMIT 1) AS emoji,
               COUNT(*) AS entryCount,
               SUM(d.isDraft) AS draftCount
          FROM diaries AS d
         WHERE d.date BETWEEN :startDate AND :endDate
      GROUP BY d.date
      ORDER BY d.date ASC
        """
    )
    fun observeDayMarkers(startDate: String, endDate: String): Flow<List<CalendarDayMarkerRow>>

    @Query("SELECT * FROM diaries WHERE id = :id")
    fun observeById(id: String): Flow<DiaryEntity?>

    @Query("SELECT * FROM diaries WHERE id = :id")
    suspend fun findById(id: String): DiaryEntity?

    /** バックアップ書き出し用の全件取得。並びはタイムラインと同じにして、ファイル内でも新しい順に並べる。 */
    @Query("SELECT * FROM diaries ORDER BY date DESC, createdAt DESC")
    suspend fun findAll(): List<DiaryEntity>

    /** 新規作成と更新を兼ねる。 */
    @Upsert
    suspend fun upsert(diary: DiaryEntity)

    /**
     * バックアップ読み込み用のまとめ書き。
     * 1件ずつ upsert するとそのたびに一覧の Flow が流れ、読み込み中に画面が何度も描き直される。
     */
    @Upsert
    suspend fun upsertAll(diaries: List<DiaryEntity>)

    /**
     * お気に入り（星）の付け外し。
     *
     * 本文を触らない操作なので [DiaryEntity.updatedAt] は動かさない。
     * 星を付け直しただけで「最終更新」が新しくなってしまうのを避ける。
     */
    @Query("UPDATE diaries SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM diaries WHERE id = :id")
    suspend fun deleteById(id: String)

    // --- ダッシュボード（フェーズ4）向け ---

    @Query("SELECT COUNT(*) FROM diaries")
    fun observeTotalCount(): Flow<Int>

    /** 日記が存在する日付（重複なし・降順）。ストリーク算出に使う。 */
    @Query("SELECT DISTINCT date FROM diaries ORDER BY date DESC")
    fun observeRecordedDates(): Flow<List<String>>

    @Query("SELECT emoji AS emoji, COUNT(*) AS entryCount FROM diaries GROUP BY emoji ORDER BY entryCount DESC")
    fun observeEmojiCounts(): Flow<List<EmojiCountRow>>

    /** 月別の投稿数。日付は `YYYY-MM-DD` なので先頭7文字がそのまま年月になる。 */
    @Query(
        """
        SELECT substr(date, 1, 7) AS yearMonth, COUNT(*) AS entryCount
          FROM diaries
      GROUP BY yearMonth
      ORDER BY yearMonth ASC
        """
    )
    fun observeMonthlyCounts(): Flow<List<MonthlyCountRow>>

    /** 孤児メディアの掃除用に、全日記のブロック JSON を取得する。 */
    @Query("SELECT blocksJson FROM diaries")
    suspend fun findAllBlocksJson(): List<String>
}
