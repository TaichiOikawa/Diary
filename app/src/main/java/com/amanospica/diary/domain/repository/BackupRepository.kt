package com.amanospica.diary.domain.repository

import com.amanospica.diary.domain.model.ArchiveContents
import com.amanospica.diary.domain.model.Diary
import com.amanospica.diary.domain.model.ExportSummary

/**
 * 日記をアプリの外へ持ち出し／持ち帰るための契約。
 *
 * URI は SAF（ストレージアクセスフレームワーク）で利用者が選んだ場所を指す文字列。
 * 失敗は例外を投げず [Result] で返し、中身は
 * [com.amanospica.diary.domain.model.BackupException] に揃える。
 */
interface BackupRepository {

    /**
     * [diaries] と、それらが本文から参照している写真・動画を
     * [destinationUri] のファイルへ ZIP としてまとめて書き出す。
     */
    suspend fun exportTo(destinationUri: String, diaries: List<Diary>): Result<ExportSummary>

    /**
     * [sourceUri] の ZIP から写真・動画を内部ストレージへ復元し、日記の一覧を読み出す。
     *
     * DB へは書き込まない。既にある日記とどう突き合わせるかは呼び出し側が決める。
     */
    suspend fun readArchive(sourceUri: String): Result<ArchiveContents>
}
