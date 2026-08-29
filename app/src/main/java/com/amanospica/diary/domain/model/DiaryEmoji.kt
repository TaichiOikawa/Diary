package com.amanospica.diary.domain.model

/**
 * 日記に付けるプリセット絵文字（スタンプ）。
 * ユーザーは任意入力ではなくこの一覧から1つ選ぶ。
 */
data class DiaryEmoji(
    val emoji: String,
    /** 選択 UI に出すラベル（アクセシビリティ用の説明も兼ねる）。 */
    val label: String,
    val category: Category,
) {
    enum class Category { MOOD, ACTIVITY, EVENT }

    companion object {
        /** 未選択時のデフォルト絵文字。 */
        val DEFAULT: DiaryEmoji get() = PRESETS.first()

        /** アプリ内に用意された絵文字一覧（24種）。 */
        val PRESETS: List<DiaryEmoji> = listOf(
            // 気分
            DiaryEmoji("😊", "うれしい", Category.MOOD),
            DiaryEmoji("😄", "たのしい", Category.MOOD),
            DiaryEmoji("🥰", "しあわせ", Category.MOOD),
            DiaryEmoji("😌", "おだやか", Category.MOOD),
            DiaryEmoji("🤔", "かんがえ中", Category.MOOD),
            DiaryEmoji("😐", "ふつう", Category.MOOD),
            DiaryEmoji("😢", "かなしい", Category.MOOD),
            DiaryEmoji("😡", "いかり", Category.MOOD),
            DiaryEmoji("😱", "おどろき", Category.MOOD),
            DiaryEmoji("😴", "ねむい", Category.MOOD),
            DiaryEmoji("🤒", "たいちょう不良", Category.MOOD),
            DiaryEmoji("😵‍💫", "つかれた", Category.MOOD),
            // 行動
            DiaryEmoji("💪", "トレーニング", Category.ACTIVITY),
            DiaryEmoji("📚", "べんきょう", Category.ACTIVITY),
            DiaryEmoji("💼", "しごと", Category.ACTIVITY),
            DiaryEmoji("🍽️", "ごはん", Category.ACTIVITY),
            DiaryEmoji("☕", "カフェ", Category.ACTIVITY),
            DiaryEmoji("🎮", "あそび", Category.ACTIVITY),
            DiaryEmoji("🎵", "おんがく", Category.ACTIVITY),
            DiaryEmoji("🏃", "おでかけ", Category.ACTIVITY),
            // イベント
            DiaryEmoji("✈️", "りょこう", Category.EVENT),
            DiaryEmoji("🎉", "おいわい", Category.EVENT),
            DiaryEmoji("🎂", "たんじょうび", Category.EVENT),
            DiaryEmoji("🌸", "きせつ", Category.EVENT),
        )

        private val byEmoji: Map<String, DiaryEmoji> = PRESETS.associateBy { it.emoji }

        /** 保存済みの絵文字文字列からプリセット定義を引く。未知の値なら null。 */
        fun find(emoji: String): DiaryEmoji? = byEmoji[emoji]

        /** 保存済みの絵文字が未知でも表示を壊さないためのフォールバック。 */
        fun labelOf(emoji: String): String = byEmoji[emoji]?.label ?: emoji
    }
}
