package com.amanospica.diary.domain.model

/** アプリの外観設定。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 本文の余白の広さ。
 *
 * 行間とブロック間の余白は同じ値から導くため、この1つの設定で両方が変わる。
 * 実際の倍率は表示上の都合なので UI 層（BodyTextMetrics）で決める。
 */
enum class TextSpacing { SMALL, MEDIUM, LARGE }

/**
 * 端末に保存する設定一式。
 *
 * PIN は平文では持たず、ソルト付きハッシュ（[PinCredential]）だけを保存する。
 */
data class AppSettings(
    val isLockEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val hasPin: Boolean = false,
    /**
     * 登録済み PIN の桁数。ロック画面の入力欄（丸の数）を合わせるために持つ。
     * 0 は「未設定」または「桁数が分からない（この項目より前に設定された PIN）」。
     */
    val pinLength: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val textSpacing: TextSpacing = TextSpacing.MEDIUM,
    /** 起動時に GitHub Releases へ更新を見に行くか。 */
    val isAutoUpdateCheckEnabled: Boolean = true,
    /** 直近で更新を確認した時刻（エポックミリ秒）。起動のたびに通信しないための目安。 */
    val lastUpdateCheckAt: Long = 0L,
    /** 「このバージョンはスキップ」と言われたタグ。自動確認のときだけ黙る。 */
    val skippedUpdateVersion: String? = null,
)

/** PIN の検証に必要な情報。値そのものは含まない。 */
data class PinCredential(
    val hash: String,
    val salt: String,
)
