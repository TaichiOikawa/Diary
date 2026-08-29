package com.amanospica.diary.domain.repository

import com.amanospica.diary.domain.model.CalendarDayMarker
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.MonthlyCount
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日記データの読み書き契約。実装は data 層（Room + 内部ストレージ）が担う。
 */
interface DiaryRepository {

    /** タイムライン用。日付降順 → 同日内は作成日時降順。 */
    fun observeTimeline(): Flow<List<Diary>>

    /** 指定日の日記一覧（作成日時降順）。 */
    fun observeByDate(date: LocalDate): Flow<List<Diary>>

    /**
     * カレンダーのマス目情報（日記のある日だけ返る）。
     *
     * [start]〜[end] の月をまとめて返すのは、横スワイプで隣の月が見え始めた時点で
     * マーカーが揃っているようにするため。
     */
    fun observeMonths(start: YearMonth, end: YearMonth): Flow<List<CalendarDayMarker>>

    /** 編集画面用。存在しない ID なら null を流す。 */
    fun observeDiary(id: String): Flow<Diary?>

    suspend fun getDiary(id: String): Diary?

    /** バックアップ書き出し用に全日記を取得する（タイムラインと同じ並び）。 */
    suspend fun getAllDiaries(): List<Diary>

    /** 新規作成・更新の両方を担う（[Diary.updatedAt] は実装側で打ち直す）。 */
    suspend fun saveDiary(diary: Diary)

    /**
     * バックアップから読み込んだ日記をまとめて保存する。
     *
     * [saveDiary] と違い、差し替えで参照されなくなったメディアの回収はしない。
     * 読み込みでは ZIP 内のメディアが先に復元されており、採用しなかった分もまとめて
     * 孤児メディアの掃除で片付ける方が無駄がないため。
     */
    suspend fun importDiaries(diaries: List<Diary>)

    /** お気に入り（星）を付け外しする。本文には触れないので [Diary.updatedAt] は動かない。 */
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    /** 日記本体と、そこから参照されていたメディアファイルを削除する。 */
    suspend fun deleteDiary(id: String)

    /** 全日記が参照しているメディアの相対パス集合（孤児ファイル掃除用）。 */
    suspend fun getReferencedMediaPaths(): Set<String>

    // --- ダッシュボード（フェーズ4）向けの集計 ---

    fun observeTotalCount(): Flow<Int>

    /** 日記が存在する日付の一覧（降順）。ストリーク算出に使う。 */
    fun observeRecordedDates(): Flow<List<LocalDate>>

    /** 絵文字ごとの件数（多い順）。 */
    fun observeEmojiCounts(): Flow<Map<String, Int>>

    /** 月ごとの投稿数（古い順）。 */
    fun observeMonthlyCounts(): Flow<List<MonthlyCount>>
}
