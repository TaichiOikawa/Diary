package com.amanospica.diary.data.local.converter

import androidx.room.TypeConverter
import com.amanospica.diary.data.local.DiaryBlockListSerializer
import com.amanospica.diary.data.local.DiaryJson
import com.amanospica.diary.domain.model.DiaryBlock
import kotlinx.serialization.SerializationException

/**
 * ブロック構造 ↔ JSON 文字列（`diaries.blocksJson` カラム）の相互変換。
 */
class DiaryTypeConverters {

    @TypeConverter
    fun blocksToJson(blocks: List<DiaryBlock>): String =
        DiaryJson.encodeToString(DiaryBlockListSerializer, blocks)

    /**
     * 壊れた JSON でアプリ全体が落ちるのを避けるため、復元できない場合は空リストにフォールバックする。
     * （日付・絵文字・タイトルは残るので、ユーザーは記録の存在自体を失わない）
     */
    @TypeConverter
    fun jsonToBlocks(json: String): List<DiaryBlock> = try {
        DiaryJson.decodeFromString(DiaryBlockListSerializer, json)
    } catch (e: SerializationException) {
        emptyList()
    } catch (e: IllegalArgumentException) {
        emptyList()
    }
}
