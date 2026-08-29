package com.amanospica.diary.domain.usecase

import com.amanospica.diary.domain.model.CalendarDayMarker
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.repository.DiaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

/** タイムライン画面（ホーム）の日記一覧を購読する。 */
class ObserveTimelineUseCase(private val repository: DiaryRepository) {
    operator fun invoke(): Flow<List<Diary>> = repository.observeTimeline()
}

/** カレンダーで日付をタップした際の、その日の日記一覧を購読する。 */
class ObserveDiariesByDateUseCase(private val repository: DiaryRepository) {
    operator fun invoke(date: LocalDate): Flow<List<Diary>> = repository.observeByDate(date)
}

/**
 * カレンダー画面のマーカー（最新絵文字＋件数）を購読する。
 *
 * 表示中の月だけでなく前後1ヶ月も一緒に読むので、横スワイプで隣の月が覗いた時点で
 * すでにマーカーが乗っている（スワイプ後に絵文字が遅れて現れない）。
 */
class ObserveCalendarMonthUseCase(private val repository: DiaryRepository) {
    operator fun invoke(yearMonth: YearMonth): Flow<Map<LocalDate, CalendarDayMarker>> =
        repository.observeMonths(yearMonth.minusMonths(1), yearMonth.plusMonths(1))
            .map { markers -> markers.associateBy { it.date } }
}

/** 編集画面が開いている日記を購読する。 */
class ObserveDiaryUseCase(private val repository: DiaryRepository) {
    operator fun invoke(id: String): Flow<Diary?> = repository.observeDiary(id)
}

/** 日記を1件取得する（編集画面の初期ロード用）。 */
class GetDiaryUseCase(private val repository: DiaryRepository) {
    suspend operator fun invoke(id: String): Diary? = repository.getDiary(id)
}

/**
 * 日記を保存する。空の日記は保存せず false を返す。
 */
class SaveDiaryUseCase(private val repository: DiaryRepository) {
    suspend operator fun invoke(diary: Diary): Boolean {
        if (diary.isBlank) return false
        repository.saveDiary(diary.copy(updatedAt = System.currentTimeMillis()))
        return true
    }
}

/**
 * お気に入り（星）を付け外しする。
 *
 * 本文の保存とは別扱いにして、日記の中身を書き換えずに印だけを更新する。
 * 編集画面が開いたままでも、書きかけの本文を巻き戻す心配がない。
 */
class SetFavoriteUseCase(private val repository: DiaryRepository) {
    suspend operator fun invoke(id: String, isFavorite: Boolean) =
        repository.setFavorite(id, isFavorite)
}

/** 日記と、それが参照していたメディアファイルを削除する。 */
class DeleteDiaryUseCase(private val repository: DiaryRepository) {
    suspend operator fun invoke(id: String) = repository.deleteDiary(id)
}
