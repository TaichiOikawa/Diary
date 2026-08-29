package com.amanospica.diary.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 日記アプリの基調色（インディゴ系）。
 *
 * ライトとダークは明度を反転させただけの自動生成ではなく、
 * それぞれの背景に対してコントラストが取れる段を個別に選んでいる。
 * ダッシュボードの棒グラフはこの primary 1色だけで描くため、
 * 背景（surface）との明度差が読み取りやすさに直結する。
 */

// ライト: 明るい面に置く濃い段
val IndigoPrimaryLight = Color(0xFF4A5BB8)
val IndigoOnPrimaryLight = Color(0xFFFFFFFF)
val IndigoPrimaryContainerLight = Color(0xFFDFE1FF)
val IndigoOnPrimaryContainerLight = Color(0xFF00105C)
val TealSecondaryLight = Color(0xFF5B5D72)
val AmberTertiaryLight = Color(0xFF785900)
val SurfaceLight = Color(0xFFFBF8FF)
val OnSurfaceLight = Color(0xFF1A1B21)

// ダーク: 暗い面に置く明るい段（ライトの単純な反転ではない）
val IndigoPrimaryDark = Color(0xFFBAC3FF)
val IndigoOnPrimaryDark = Color(0xFF17287E)
val IndigoPrimaryContainerDark = Color(0xFF31409F)
val IndigoOnPrimaryContainerDark = Color(0xFFDFE1FF)
val TealSecondaryDark = Color(0xFFC3C5DD)
val AmberTertiaryDark = Color(0xFFF0C048)
val SurfaceDark = Color(0xFF121319)
val OnSurfaceDark = Color(0xFFE3E1E9)
