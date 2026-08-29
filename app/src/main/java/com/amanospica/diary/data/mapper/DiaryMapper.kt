package com.amanospica.diary.data.mapper

import com.amanospica.diary.data.local.dao.CalendarDayMarkerRow
import com.amanospica.diary.data.local.entity.DiaryEntity
import com.amanospica.diary.domain.model.CalendarDayMarker
import com.amanospica.diary.domain.model.Diary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** DB に格納する日付フォーマット（YYYY-MM-DD）。 */
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun LocalDate.toDbDate(): String = format(DATE_FORMATTER)

fun String.toLocalDate(): LocalDate = LocalDate.parse(this, DATE_FORMATTER)

fun DiaryEntity.toDomain(): Diary = Diary(
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

fun Diary.toEntity(): DiaryEntity = DiaryEntity(
    id = id,
    date = date.toDbDate(),
    emoji = emoji,
    title = title?.takeIf { it.isNotBlank() },
    blocks = blocks,
    isDraft = isDraft,
    isFavorite = isFavorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun CalendarDayMarkerRow.toDomain(): CalendarDayMarker = CalendarDayMarker(
    date = date.toLocalDate(),
    emoji = emoji,
    count = entryCount,
    hasDraft = draftCount > 0,
)
