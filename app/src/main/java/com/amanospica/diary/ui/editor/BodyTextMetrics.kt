package com.amanospica.diary.ui.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.amanospica.diary.domain.model.TextSpacing

/** アプリ設定の余白をエディタへ届けるための受け皿。 */
val LocalTextSpacing = compositionLocalOf { TextSpacing.MEDIUM }

/** 余白の段階ごとの行送り（フォントサイズに対する倍率）。 */
private val TextSpacing.lineHeightMultiplier: Float
    get() = when (this) {
        TextSpacing.SMALL -> 1.3f
        TextSpacing.MEDIUM -> 1.6f
        TextSpacing.LARGE -> 2.0f
    }

/**
 * 本文の文字スタイルと、メディアブロックの上下に入れる余白。
 *
 * 行間とブロック間を一致させる仕掛けは文字スタイル側にある。
 * [LineHeightStyle.Trim.None] を指定すると、先頭行の上と最終行の下にも行送りの余りが
 * 半分ずつ乗る。そのためテキストブロック同士は **間隔ゼロで並べる** だけで、
 * 「折り返して次の行へ進む」ときと「メディアを挟んで次のブロックへ進む」ときの間隔が
 * 厳密に同じ（どちらもベースライン間が lineHeight）になる。
 *
 * [mediaGap] は画像・動画ブロックの上下に入れる余白。文字と違って行送りを持たないので、
 * 同じ見た目の余白を明示的に足す。
 */
data class BodyTextMetrics(
    val paragraphStyle: TextStyle,
    /**
     * 見出し行に重ねる文字装飾。
     * 1つの入力欄が複数行を持ち、見出しは行ごとに切り替わるため [TextStyle] ではなく
     * [SpanStyle] で持ち、該当行の範囲にだけ重ねる。
     */
    val headingSpanStyle: SpanStyle,
    /**
     * 見出し行の行送り。
     *
     * [paragraphStyle] の lineHeight は本文の文字サイズに合わせてあるため、そのままでは
     * 大きい見出しの字が行の高さに収まらず、上下の行と重なってしまう
     * （余白「小」だと顕著）。見出しの行にだけこの高さを与えて逃がす。
     */
    val headingLineHeight: TextUnit,
    val mediaGap: Dp,
)

@Composable
fun rememberBodyTextMetrics(): BodyTextMetrics {
    val spacing = LocalTextSpacing.current
    val typography = MaterialTheme.typography
    val contentColor = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current

    return remember(spacing, typography, contentColor, density) {
        val paragraph = typography.bodyLarge.withSpacing(spacing, contentColor)
        val headingSize = typography.titleLarge.fontSize.let { if (it.isUnspecified) 22.sp else it }

        // 行送りの余り（＝行と行のすき間）と同じだけ、メディアの上下にも余白を置く
        val gap = with(density) {
            (paragraph.lineHeight.toPx() - paragraph.fontSize.toPx()).toDp()
        }
        BodyTextMetrics(
            paragraphStyle = paragraph,
            headingSpanStyle = SpanStyle(fontSize = headingSize, fontWeight = FontWeight.Bold),
            headingLineHeight = headingSize * spacing.lineHeightMultiplier,
            mediaGap = (gap / 2).coerceAtLeast(0.dp),
        )
    }
}

private fun TextStyle.withSpacing(spacing: TextSpacing, color: Color): TextStyle {
    val size = if (fontSize.isUnspecified) 16.sp else fontSize
    return copy(
        color = color,
        fontSize = size,
        lineHeight = size * spacing.lineHeightMultiplier,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            // 先頭行の上・最終行の下にも行送りの余りを残す。
            // これがブロック間の余白そのものになり、行間と完全に揃う
            trim = LineHeightStyle.Trim.None,
        ),
    )
}
