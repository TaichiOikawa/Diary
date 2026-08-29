package com.amanospica.diary.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amanospica.diary.R
import com.amanospica.diary.ui.calendar.CalendarScreen
import com.amanospica.diary.ui.common.DiaryViewModelFactory
import com.amanospica.diary.ui.dashboard.DashboardScreen
import com.amanospica.diary.ui.photo.PhotoScreen
import com.amanospica.diary.ui.timeline.TimelineScreen
import com.amanospica.diary.ui.timeline.TimelineSelectionTopBar
import com.amanospica.diary.ui.timeline.TimelineViewModel
import java.time.LocalDate

private const val PAGE_FADE_MILLIS = 150

/**
 * メイン画面。下部タブで切り替わる各画面と、中央の作成ボタンを束ねる。
 *
 * 画面の切り替えは選択中のタブ（[selectedIndex]）だけが決める。
 * 以前は `HorizontalPager` のスクロール位置を正としていたため、離れたタブへ移るときに
 * 途中のページを通過してタイトルが一瞬別の画面名になっていた。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onCreateDiary: (LocalDate) -> Unit,
    /** 日記を開く。書きかけかどうかで移動先（編集／閲覧）が変わるため、状態も一緒に渡す。 */
    onOpenDiary: (diaryId: String, isDraft: Boolean) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val pages = remember { MainPage.entries }
    var selectedIndex by rememberSaveable { mutableIntStateOf(MainPage.HOME.ordinal) }
    val currentPage = pages[selectedIndex]

    // タブを離れている間もスクロール位置などを保持する
    val stateHolder = rememberSaveableStateHolder()

    // タイムラインのまとめ削除では上部バーとFABが変わるため、選択の状態はここでも見る。
    // ViewModel は画面と同じ持ち主から取るので、下の TimelineScreen と同じ1個になる。
    val timelineViewModel: TimelineViewModel = viewModel(factory = DiaryViewModelFactory)
    val timelineState by timelineViewModel.uiState.collectAsStateWithLifecycle()
    val inTimelineSelection = currentPage == MainPage.TIMELINE && timelineState.isSelectionMode
    // 星での絞り込みはタイムラインだけの話なので、上部バーの星もそのタブにいる間だけ出す
    val showFavoriteFilter = currentPage == MainPage.TIMELINE
    val favoritesOnly = showFavoriteFilter && timelineState.isFavoritesOnly

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (inTimelineSelection) {
                TimelineSelectionTopBar(
                    selectedCount = timelineState.selectedIds.size,
                    onClearSelection = timelineViewModel::clearSelection,
                    onDeleteSelected = timelineViewModel::requestDeleteSelected,
                )
            } else {
                CenterAlignedTopAppBar(
                    // 絞り込み中は見出しも「お気に入り」にして、一覧が欠けて見えないようにする
                    title = {
                        Text(
                            stringResource(
                                if (favoritesOnly) R.string.timeline_favorites_title
                                else currentPage.titleRes
                            )
                        )
                    },
                    // 設定は日記を読み書きする操作ではないので、左端へ置いて
                    // 右側は検索・お気に入りといった一覧を手繰る操作でまとめる
                    navigationIcon = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                            )
                        }
                    },
                    actions = {
                        // 検索はどのタブからでも同じ全件が相手なので、常に出しておく
                        IconButton(onClick = onOpenSearch) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.search_open),
                            )
                        }
                        if (showFavoriteFilter) {
                            IconButton(onClick = timelineViewModel::toggleFavoritesOnly) {
                                Icon(
                                    imageVector = if (favoritesOnly) {
                                        Icons.Filled.Star
                                    } else {
                                        Icons.Outlined.StarBorder
                                    },
                                    contentDescription = stringResource(
                                        if (favoritesOnly) R.string.timeline_favorites_filter_off
                                        else R.string.timeline_favorites_filter_on
                                    ),
                                    tint = if (favoritesOnly) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        LocalContentColor.current
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            MainBottomBar(
                pages = pages,
                currentPage = currentPage,
                // 別のタブへ移るのは選択をやめる合図として扱う
                onSelectPage = { page ->
                    selectedIndex = page.ordinal
                    timelineViewModel.clearSelection()
                },
                // 選択中は「書く」より「選ぶ」が主役なので、＋ボタンは引っ込める
                showCreateButton = !inTimelineSelection,
                onCreateDiary = { onCreateDiary(LocalDate.now()) },
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentPage,
            // 横スライドだと離れたタブへ移るときに中間の画面が流れて見えるため、
            // 目的の画面だけが出入りするクロスフェードにする
            transitionSpec = {
                fadeIn(tween(PAGE_FADE_MILLIS)) togetherWith fadeOut(tween(PAGE_FADE_MILLIS))
            },
            label = "main-page",
            modifier = Modifier
                .fillMaxSize()
                // バーが塞がない帯（＋ボタンが飛び出すぶんと、その下の切り欠き）の分だけ
                // 下の余白を戻して中身を潜らせる。Scaffold はバーの高さぶん中身を上げるが、
                // この帯は画面が透ける場所なので、空けたままだと白い隙間として残ってしまう。
                // 潜らせたぶんはバーが上から覆うので、隠れて困るのは切り欠きの中だけ。
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    top = innerPadding.calculateTopPadding(),
                    bottom = (innerPadding.calculateBottomPadding() - MainBottomBarSeeThroughHeight)
                        .coerceAtLeast(0.dp),
                ),
        ) { page ->
            stateHolder.SaveableStateProvider(page.name) {
                when (page) {
                    MainPage.DASHBOARD -> DashboardScreen()
                    MainPage.TIMELINE -> TimelineScreen(
                        onOpenDiary = onOpenDiary,
                        viewModel = timelineViewModel,
                    )
                    MainPage.CALENDAR -> CalendarScreen(
                        onOpenDiary = onOpenDiary,
                        onCreateDiary = onCreateDiary,
                    )
                    MainPage.PHOTO -> PhotoScreen(onOpenDiary = onOpenDiary)
                }
            }
        }
    }
}
