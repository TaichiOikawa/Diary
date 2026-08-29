package com.amanospica.diary.ui.common

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.amanospica.diary.DiaryApplication
import com.amanospica.diary.di.AppContainer
import com.amanospica.diary.ui.calendar.CalendarViewModel
import com.amanospica.diary.ui.dashboard.DashboardViewModel
import com.amanospica.diary.ui.editor.EditorViewModel
import com.amanospica.diary.ui.lock.LockViewModel
import com.amanospica.diary.ui.navigation.EditorRoute
import com.amanospica.diary.ui.navigation.ViewerRoute
import com.amanospica.diary.ui.photo.PhotoViewModel
import com.amanospica.diary.ui.search.SearchViewModel
import com.amanospica.diary.ui.settings.SettingsViewModel
import com.amanospica.diary.ui.timeline.TimelineViewModel
import com.amanospica.diary.ui.viewer.ViewerViewModel
import java.time.LocalDate

/**
 * 手動 DI コンテナから ViewModel を組み立てるファクトリ。
 * `viewModel(factory = DiaryViewModelFactory)` の形で各画面から使う。
 */
val DiaryViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val container = container()
        TimelineViewModel(
            observeTimeline = container.observeTimeline,
            deleteDiary = container.deleteDiary,
            resolveMediaPath = container.resolveMediaPath,
        )
    }
    initializer {
        val container = container()
        CalendarViewModel(
            observeCalendarMonth = container.observeCalendarMonth,
            observeDiariesByDate = container.observeDiariesByDate,
            resolveMediaPath = container.resolveMediaPath,
        )
    }
    initializer {
        val container = container()
        PhotoViewModel(
            observeTimeline = container.observeTimeline,
            resolveMediaPath = container.resolveMediaPath,
        )
    }
    initializer {
        DashboardViewModel(observeDiaryStats = container().observeDiaryStats)
    }
    initializer {
        val container = container()
        SearchViewModel(
            searchDiaries = container.searchDiaries,
            resolveMediaPath = container.resolveMediaPath,
        )
    }
    initializer {
        val container = container()
        SettingsViewModel(
            settingsRepository = container.settingsRepository,
            exportDiaries = container.exportDiaries,
            importDiaries = container.importDiaries,
            updateManager = container.updateManager,
        )
    }
    initializer {
        val container = container()
        LockViewModel(
            settingsRepository = container.settingsRepository,
            appLockController = container.appLockController,
        )
    }
    initializer {
        val container = container()
        // 遷移時の引数（日記ID・日付）はナビゲーションのルートから直接受け取る
        val route = createSavedStateHandle().toRoute<EditorRoute>()
        EditorViewModel(
            diaryId = route.diaryId,
            initialDate = route.date?.let(LocalDate::parse) ?: LocalDate.now(),
            getDiary = container.getDiary,
            saveDiary = container.saveDiary,
            deleteDiary = container.deleteDiary,
            attachImage = container.attachImage,
            attachVideo = container.attachVideo,
            cleanUpOrphanMedia = container.cleanUpOrphanMedia,
            resolveMediaPath = container.resolveMediaPath,
        )
    }
    initializer {
        val container = container()
        val route = createSavedStateHandle().toRoute<ViewerRoute>()
        ViewerViewModel(
            diaryId = route.diaryId,
            observeDiary = container.observeDiary,
            setFavorite = container.setFavorite,
            resolveMediaPath = container.resolveMediaPath,
        )
    }
}

private fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DiaryApplication).container
