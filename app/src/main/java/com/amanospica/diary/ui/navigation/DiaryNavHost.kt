package com.amanospica.diary.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.amanospica.diary.ui.editor.EditorScreen
import com.amanospica.diary.ui.main.MainScreen
import com.amanospica.diary.ui.search.SearchScreen
import com.amanospica.diary.ui.settings.SettingsScreen
import com.amanospica.diary.ui.viewer.ViewerScreen

@Composable
fun DiaryNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = MainRoute) {
        composable<MainRoute> {
            MainScreen(
                onCreateDiary = { date ->
                    navController.navigate(EditorRoute(date = date.toString()))
                },
                onOpenDiary = navController::openDiary,
                onOpenSearch = { navController.navigate(SearchRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<EditorRoute> {
            // 引数は EditorViewModel が SavedStateHandle 経由で受け取る
            EditorScreen(onNavigateUp = navController::navigateUp)
        }
        composable<ViewerRoute> {
            ViewerScreen(
                onNavigateUp = navController::navigateUp,
                // 編集を終えて戻ると閲覧画面へ戻る。中身は購読しているので更新後の内容が出る
                onEdit = { diaryId -> navController.navigate(EditorRoute(diaryId = diaryId)) },
            )
        }
        composable<SearchRoute> {
            SearchScreen(
                onNavigateUp = navController::navigateUp,
                onOpenDiary = navController::openDiary,
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onNavigateUp = navController::navigateUp)
        }
    }
}

/**
 * 一覧から日記を開く。
 *
 * 書きかけの日記は続きを書く場面なのでそのまま編集画面へ。
 * 書き終わった日記はまず読む画面で開き、直したくなったら鉛筆から編集へ移る。
 */
private fun NavHostController.openDiary(diaryId: String, isDraft: Boolean) {
    if (isDraft) {
        navigate(EditorRoute(diaryId = diaryId))
    } else {
        navigate(ViewerRoute(diaryId = diaryId))
    }
}
