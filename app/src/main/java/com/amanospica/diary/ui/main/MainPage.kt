package com.amanospica.diary.ui.main

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.amanospica.diary.R

/**
 * メインのタブ画面。列挙順がそのまま下部タブの並び順になる。
 *
 * ホーム（タイムライン）を左端の起点に置き、そこから読み返す粒度が粗くなる順に並べる。
 * タイムライン（1件ずつ）→ カレンダー（日付から）→ フォト（写真から）と辿り、
 * 性格の違う集計のダッシュボードは右端に置いた。
 */
enum class MainPage(
    @param:StringRes val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    TIMELINE(R.string.page_timeline, Icons.Filled.Home, Icons.Outlined.Home),
    CALENDAR(R.string.page_calendar, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    PHOTO(R.string.page_photo, Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    DASHBOARD(R.string.page_dashboard, Icons.Filled.Insights, Icons.Outlined.Insights),
    ;

    companion object {
        /** 起動時に表示するページ。 */
        val HOME: MainPage = TIMELINE
    }
}
