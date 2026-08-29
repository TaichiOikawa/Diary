package com.amanospica.diary.di

import android.content.Context
import com.amanospica.diary.data.backup.BackupRepositoryImpl
import com.amanospica.diary.data.local.DiaryDatabase
import com.amanospica.diary.data.media.MediaRepositoryImpl
import com.amanospica.diary.data.media.MediaStorage
import com.amanospica.diary.data.preferences.SettingsRepositoryImpl
import com.amanospica.diary.data.repository.DiaryRepositoryImpl
import com.amanospica.diary.domain.repository.BackupRepository
import com.amanospica.diary.domain.repository.DiaryRepository
import com.amanospica.diary.domain.repository.MediaRepository
import com.amanospica.diary.domain.repository.SettingsRepository
import com.amanospica.diary.domain.security.AppLockController
import com.amanospica.diary.domain.usecase.AttachImageUseCase
import com.amanospica.diary.domain.usecase.AttachVideoUseCase
import com.amanospica.diary.domain.usecase.CleanUpOrphanMediaUseCase
import com.amanospica.diary.domain.usecase.DeleteDiaryUseCase
import com.amanospica.diary.domain.usecase.ExportDiariesUseCase
import com.amanospica.diary.domain.usecase.GetDiaryUseCase
import com.amanospica.diary.domain.usecase.ImportDiariesUseCase
import com.amanospica.diary.domain.usecase.ObserveCalendarMonthUseCase
import com.amanospica.diary.domain.usecase.ObserveDiariesByDateUseCase
import com.amanospica.diary.domain.usecase.ObserveDiaryStatsUseCase
import com.amanospica.diary.domain.usecase.ObserveDiaryUseCase
import com.amanospica.diary.domain.usecase.ObserveTimelineUseCase
import com.amanospica.diary.domain.usecase.ResolveMediaPathUseCase
import com.amanospica.diary.domain.usecase.SaveDiaryUseCase
import com.amanospica.diary.domain.usecase.SearchDiariesUseCase
import com.amanospica.diary.domain.usecase.SetFavoriteUseCase
import com.amanospica.diary.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 依存関係の生成をまとめる手動 DI コンテナ。
 *
 * 単一モジュール・単一プロセスのローカルアプリなので、DI ライブラリを足さず
 * Application が保持する 1 つのコンテナで完結させる（ビルド構成と起動時間を軽く保つ）。
 * すべて lazy なので、実際に使われるまで DB もファイル I/O も発生しない。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** アプリ全体で生き続ける処理（ロック状態の監視など）に使うスコープ。 */
    private val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val database: DiaryDatabase by lazy { DiaryDatabase.getInstance(appContext) }

    /** メディアの実体を置く内部ストレージ。取り込みとバックアップで同じ場所を共有する。 */
    private val mediaStorage: MediaStorage by lazy { MediaStorage.forApp(appContext) }

    val mediaRepository: MediaRepository by lazy { MediaRepositoryImpl(appContext, mediaStorage) }

    val diaryRepository: DiaryRepository by lazy {
        DiaryRepositoryImpl(dao = database.diaryDao(), mediaRepository = mediaRepository)
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepositoryImpl(context = appContext, storage = mediaStorage)
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepositoryImpl(appContext) }

    val appLockController: AppLockController by lazy {
        AppLockController(settingsRepository, applicationScope)
    }

    /**
     * 更新の確認結果は画面をまたいで共有したいので、ここで1つだけ持つ。
     * （設定画面で確認した結果を、そのまま全画面共通のダイアログが受け取る）
     */
    val updateManager: UpdateManager by lazy {
        UpdateManager(
            context = appContext,
            settingsRepository = settingsRepository,
            scope = applicationScope,
        )
    }

    // --- UseCase ---
    val observeTimeline by lazy { ObserveTimelineUseCase(diaryRepository) }
    val observeDiariesByDate by lazy { ObserveDiariesByDateUseCase(diaryRepository) }
    val observeCalendarMonth by lazy { ObserveCalendarMonthUseCase(diaryRepository) }
    val observeDiary by lazy { ObserveDiaryUseCase(diaryRepository) }
    val observeDiaryStats by lazy { ObserveDiaryStatsUseCase(diaryRepository) }
    val searchDiaries by lazy { SearchDiariesUseCase(diaryRepository) }
    val getDiary by lazy { GetDiaryUseCase(diaryRepository) }
    val saveDiary by lazy { SaveDiaryUseCase(diaryRepository) }
    val setFavorite by lazy { SetFavoriteUseCase(diaryRepository) }
    val deleteDiary by lazy { DeleteDiaryUseCase(diaryRepository) }
    val attachImage by lazy { AttachImageUseCase(mediaRepository) }
    val attachVideo by lazy { AttachVideoUseCase(mediaRepository) }
    val resolveMediaPath by lazy { ResolveMediaPathUseCase(mediaRepository) }
    val cleanUpOrphanMedia by lazy { CleanUpOrphanMediaUseCase(mediaRepository, diaryRepository) }
    val exportDiaries by lazy { ExportDiariesUseCase(backupRepository, diaryRepository) }
    val importDiaries by lazy {
        ImportDiariesUseCase(backupRepository, diaryRepository, cleanUpOrphanMedia)
    }
}
