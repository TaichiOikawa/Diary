package com.amanospica.diary.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amanospica.diary.domain.model.EmojiCount
import com.amanospica.diary.domain.model.MonthlyCount
import java.time.YearMonth

/**
 * 月別の投稿数を縦棒で示す。
 *
 * 系列は1本だけなので凡例は置かず、色相も1つ（プライマリ）に固定する。
 * 当月だけ濃く塗って現在地を示し、値ラベルは最大値と当月にだけ添える
 * （全部に数字を振ると読み取りの邪魔になる）。
 */
@Composable
fun MonthlyBarChart(
    monthlyCounts: List<MonthlyCount>,
    currentMonth: YearMonth,
    modifier: Modifier = Modifier,
) {
    if (monthlyCounts.isEmpty()) return

    val maxCount = monthlyCounts.maxOf { it.count }.coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    val mutedBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        monthlyCounts.forEach { monthly ->
            val isCurrent = monthly.yearMonth == currentMonth
            val showValue = isCurrent || monthly.count == maxCount

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics {
                        contentDescription = "${monthly.yearMonth.monthValue}月 ${monthly.count}件"
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = if (showValue && monthly.count > 0) monthly.count.toString() else " ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 各列で同じ高さの描画領域を確保し、その中で棒の高さ比率を決める
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 0件でも列の位置が分かるよう、下端に細い痕跡を残す
                            .fillMaxHeight(
                                (monthly.count.toFloat() / maxCount).coerceAtLeast(0.02f)
                            )
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (isCurrent) barColor else mutedBarColor),
                    )
                }
                Text(
                    text = "${monthly.yearMonth.monthValue}月",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * 気分（絵文字）の内訳を横棒で示す。
 *
 * 識別は絵文字そのものが担うため、棒は単一色相のまま。
 * 気分ごとに色を割り当てると色覚の違いで読めなくなるうえ、意味も持たない。
 */
@Composable
fun EmojiBreakdown(
    emojiCounts: List<EmojiCount>,
    modifier: Modifier = Modifier,
    maxRows: Int = 6,
) {
    val rows = emojiCounts.take(maxRows)
    if (rows.isEmpty()) return

    val topRatio = rows.first().ratio.coerceAtLeast(0.01f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.emoji,
                    fontSize = 20.sp,
                    modifier = Modifier.width(32.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = entry.ratio / topRatio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Text(
                    text = "${entry.count}件",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .width(48.dp),
                )
            }
        }
    }
}

/** 数値ひとつを大きく見せるタイル。グラフにするまでもない指標はこちらで扱う。 */
@Composable
fun StatTile(
    value: String,
    label: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (accent) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (accent) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (accent) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (accent) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
