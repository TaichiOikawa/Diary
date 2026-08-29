package com.amanospica.diary.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Notion 風エディタを構成するコンテンツブロック。
 *
 * ブロック列は [kotlinx.serialization] で JSON 配列にシリアライズされ、
 * `diaries.blocksJson` カラムへ保存される（変換は Room の TypeConverter が担当）。
 * JSON 上の型は `type` フィールド（"text" / "image" / "video"）で判別する。
 */
@Serializable
sealed interface DiaryBlock {

    /** ブロックの安定 ID。エディタでの並べ替え・差分更新のキーとして使う。 */
    val id: String

    /**
     * リッチテキストブロック。装飾は [spans] に文字範囲として保持する。
     * [text] は改行を含む複数行で、ブロックが分かれるのは画像・動画を挟んだ位置だけ。
     */
    @Serializable
    @SerialName("text")
    data class TextBlock(
        override val id: String = newBlockId(),
        val text: String = "",
        val spans: List<TextSpanAnnotation> = emptyList(),
    ) : DiaryBlock

    /** 画像ブロック。[localFilePath] は内部ストレージ media ディレクトリからの相対パス。 */
    @Serializable
    @SerialName("image")
    data class ImageBlock(
        override val id: String = newBlockId(),
        val localFilePath: String,
        val caption: String? = null,
    ) : DiaryBlock

    /** 動画ブロック。[localFilePath] は内部ストレージ media ディレクトリからの相対パス。 */
    @Serializable
    @SerialName("video")
    data class VideoBlock(
        override val id: String = newBlockId(),
        val localFilePath: String,
        val caption: String? = null,
    ) : DiaryBlock
}

/** 新しいブロック ID を発行する。 */
fun newBlockId(): String = UUID.randomUUID().toString()

/**
 * リッチテキストの装飾範囲。[start] は含む / [end] は含まない（Compose の AnnotatedString と同じ規約）。
 *
 * 段落スタイル（見出し・箇条書き）だけは行に付く目印として使うため、空行では
 * [start] と [end] が等しくなることがある。詳しくは `ParagraphLines.kt`。
 */
@Serializable
data class TextSpanAnnotation(
    val start: Int,
    val end: Int,
    val type: SpanType,
)

/** 装飾の種類。 */
@Serializable
enum class SpanType {
    BOLD,
    ITALIC,
    UNDERLINE,

    /** 見出し。行単位で適用する（範囲はその行を覆う）。 */
    HEADING,

    /** 箇条書き。行単位で適用する（範囲はその行を覆う）。 */
    LIST_ITEM,
    ;

    /**
     * 行単位で適用されるスタイルかどうか。
     * true の場合、エディタは文字を部分選択させず、選択が掛かっている行全体へ適用する。
     */
    val isParagraphStyle: Boolean get() = this == HEADING || this == LIST_ITEM
}

/** ブロック列に含まれるメディアの相対パス一覧（削除時のファイル回収に使う）。 */
fun List<DiaryBlock>.mediaFilePaths(): List<String> = mapNotNull { block ->
    when (block) {
        is DiaryBlock.ImageBlock -> block.localFilePath
        is DiaryBlock.VideoBlock -> block.localFilePath
        is DiaryBlock.TextBlock -> null
    }
}

/** タイムラインカードに出す本文プレビュー用のプレーンテキスト。 */
fun List<DiaryBlock>.plainTextPreview(maxLength: Int = 120): String {
    val text = filterIsInstance<DiaryBlock.TextBlock>()
        .joinToString(separator = " ") { it.text.trim() }
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (text.length <= maxLength) text else text.take(maxLength).trimEnd() + "…"
}
