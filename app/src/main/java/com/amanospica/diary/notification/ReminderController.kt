package com.amanospica.diary.notification

import com.amanospica.diary.domain.model.AppSettings
import com.amanospica.diary.domain.model.ReminderCondition
import com.amanospica.diary.domain.model.shouldNotify
import com.amanospica.diary.domain.repository.SettingsRepository
import com.amanospica.diary.domain.usecase.ObserveDiariesByDateUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate
import java.time.LocalTime

/** 予約に効く設定だけを取り出したもの。ここが変わったときだけ予約を入れ直す。 */
private data class ReminderPlan(
    val isEnabled: Boolean,
    val time: LocalTime,
)

/**
 * リマインダー通知の予約と発火をまとめて受け持つ。
 *
 * 設定画面・アラームの受信・端末の再起動と入口が複数あるため、
 * 「設定に合わせて予約を保つ」責任をここ1箇所に集める。
 */
class ReminderController(
    private val settingsRepository: SettingsRepository,
    private val observeDiariesByDate: ObserveDiariesByDateUseCase,
    private val scheduler: ReminderScheduler,
    private val notifier: DiaryNotifier,
    private val scope: CoroutineScope,
) {

    /**
     * 設定の監視を始める。アプリの起動時に1度だけ呼ぶ。
     *
     * 通知の有無と時刻が変わるたびに予約を入れ直すので、
     * 設定画面側はスイッチを切り替えるだけでよい。
     */
    fun start() {
        settingsRepository.settings
            .map { ReminderPlan(isEnabled = it.isReminderEnabled, time = it.reminderTime) }
            .distinctUntilChanged()
            .onEach { plan -> plan.apply() }
            .launchIn(scope)
    }

    /**
     * 予約した時刻になったときの処理。条件を満たせば通知し、翌日の分を予約し直す。
     *
     * 予約の入れ直しは通知を出さなかった場合も必ず行う。
     * ここで止めると「今日は書いたから鳴らなかった」翌日以降が二度と鳴らなくなる。
     */
    suspend fun onReminderFired(today: LocalDate = LocalDate.now()) {
        val settings = settingsRepository.currentSettings()
        if (!settings.isEnabledWithPermission()) {
            // 設定が消えている／通知が止められている状態で予約だけ残しても鳴らせない
            scheduler.cancel()
            return
        }
        if (settings.reminderCondition.shouldNotifyToday(today)) notifier.notifyReminder()
        scheduler.schedule(settings.reminderTime)
    }

    /**
     * 端末の再起動やアプリの更新のあとに予約を入れ直す。
     * AlarmManager の予約は再起動で消えるため、ここで復元しないと通知が止まる。
     */
    suspend fun reschedule() {
        settingsRepository.currentSettings().toPlan().apply()
    }

    private suspend fun ReminderCondition.shouldNotifyToday(today: LocalDate): Boolean =
        shouldNotify(observeDiariesByDate(today).first())

    private fun ReminderPlan.apply() {
        if (isEnabled) scheduler.schedule(time) else scheduler.cancel()
    }

    private fun AppSettings.toPlan() = ReminderPlan(isEnabled = isReminderEnabled, time = reminderTime)

    /** 通知が OS 側で止められていれば、鳴らせないので予約も畳む。 */
    private fun AppSettings.isEnabledWithPermission(): Boolean =
        isReminderEnabled && notifier.canPostNotifications()
}
