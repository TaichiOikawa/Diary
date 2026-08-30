package com.amanospica.diary.domain.model

/**
 * リマインダー通知を出す条件。
 *
 * 「毎日必ず声をかけてほしい」人と「書き忘れたときだけ声をかけてほしい」人がいるので、
 * 通知そのものの有無（[AppSettings.isReminderEnabled]）とは別の設定として持つ。
 */
enum class ReminderCondition {
    /** その日の日記がまだ無い、または下書きのままのときだけ通知する。 */
    WHEN_UNWRITTEN,

    /** 日記の状況にかかわらず、毎日その時刻に通知する。 */
    ALWAYS,
}

/**
 * 通知の時刻になったとき、[diariesOfDay]（その日の日記一覧）を見て通知を出すか決める。
 *
 * 下書きは「まだ書き終わっていない」扱いにする。書きかけで閉じたまま忘れることこそ、
 * この通知が拾いたい状況のため。
 */
fun ReminderCondition.shouldNotify(diariesOfDay: List<Diary>): Boolean = when (this) {
    ReminderCondition.ALWAYS -> true
    ReminderCondition.WHEN_UNWRITTEN -> diariesOfDay.none { !it.isDraft }
}
