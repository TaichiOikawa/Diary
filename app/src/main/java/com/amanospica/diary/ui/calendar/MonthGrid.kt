package com.amanospica.diary.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amanospica.diary.domain.model.CalendarDayMarker
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** 日曜始まりの曜日並び。 */
private val WEEK_DAYS: List<DayOfWeek> = listOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
)

private val WEEK_DAY_LABELS: List<String> = listOf("日", "月", "火", "水", "木", "金", "土")

/** どの月も同じ高さになるよう、行数は常にこれで揃える（月をまたいでも表が伸び縮みしない）。 */
private const val WEEK_ROWS = 6

/**
 * 指定月を7列 × [WEEK_ROWS] 行のマス目に並べる。前後月にはみ出す位置は null。
 */
fun buildMonthGrid(yearMonth: YearMonth): List<List<LocalDate?>> {
    val firstDay = yearMonth.atDay(1)
    val leadingBlanks = WEEK_DAYS.indexOf(firstDay.dayOfWeek)
    val cells = buildList<LocalDate?> {
        repeat(leadingBlanks) { add(null) }
        for (day in 1..yearMonth.lengthOfMonth()) add(yearMonth.atDay(day))
        while (size < WEEK_ROWS * 7) add(null)
    }
    return cells.chunked(7)
}

@Composable
fun MonthGrid(
    yearMonth: YearMonth,
    markers: Map<LocalDate, CalendarDayMarker>,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEK_DAY_LABELS.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = weekDayColor(index),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp),
                )
            }
        }
        buildMonthGrid(yearMonth).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEachIndexed { index, date ->
                    if (date == null) {
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    } else {
                        DayCell(
                            date = date,
                            marker = markers[date],
                            isToday = date == today,
                            dayColor = weekDayColor(index),
                            onClick = { onDayClick(date) },
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    marker: CalendarDayMarker?,
    isToday: Boolean,
    dayColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (marker != null) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = if (isToday) {
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                } else {
                    Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                },
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimary else dayColor,
                )
            }
            if (marker != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = marker.emoji, fontSize = 16.sp)
                    if (marker.count > 1) {
                        Text(
                            text = "+${marker.count - 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                // 書きかけがある日は小さな点で示す。マスが狭いので文字は置かない
                if (marker.hasDraft) {
                    Box(
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                }
            }
        }
    }
}

@Composable
private fun weekDayColor(index: Int): Color = when (index) {
    0 -> MaterialTheme.colorScheme.error            // 日曜
    6 -> MaterialTheme.colorScheme.tertiary         // 土曜
    else -> MaterialTheme.colorScheme.onSurface
}
