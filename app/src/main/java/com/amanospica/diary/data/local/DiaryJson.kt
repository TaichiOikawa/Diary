package com.amanospica.diary.data.local

import com.amanospica.diary.domain.model.DiaryBlock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * ブロック構造の永続化に使う JSON 設定。
 *
 * - `ignoreUnknownKeys`: 将来ブロックにフィールドを足した後でダウングレードしても壊れないように
 * - `encodeDefaults`: 既定値の `id` を必ず書き出すため（省略するとブロック ID が失われる）
 * - `classDiscriminator`: 保存済み JSON の互換性を固定するため明示
 */
internal val DiaryJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
    explicitNulls = false
}

/**
 * `blocksJson` カラムのシリアライザ。
 *
 * 型を明示しているのは、Room の KSP 処理時点では kotlinx.serialization プラグインが生成する
 * `DiaryBlock.serializer()` の戻り値型が解決できず、型推論に任せると MissingType になるため。
 */
internal val DiaryBlockListSerializer: KSerializer<List<DiaryBlock>> =
    ListSerializer(DiaryBlock.serializer())
