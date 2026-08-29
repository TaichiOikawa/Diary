package com.amanospica.diary.ui.editor.block

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.amanospica.diary.domain.model.DiaryBlock
import com.amanospica.diary.ui.editor.rememberBodyTextMetrics
import com.amanospica.diary.ui.richtext.RichTextVisualTransformation
import com.amanospica.diary.ui.richtext.richTextOffsetMapping

/**
 * リッチテキストブロックの編集欄。
 *
 * 1つのブロックが本文の複数行をまとめて持つ（ブロックの境目は画像・動画を挟んだ位置だけ）。
 * こうしておくと全選択・ドラッグ選択・取り消しが入力欄1つの中で完結し、
 * 「改行したら選択が途切れる」ことがなくなる。
 *
 * 装飾は [RichTextVisualTransformation] で見た目にだけ重ねるため、保持している文字列は
 * 変わらない。見出し・箇条書きは行ごとのスタイルで、これも同じ変換が受け持つ。
 */
@Composable
fun TextBlockEditor(
    block: DiaryBlock.TextBlock,
    placeholder: String,
    onTextChange: (text: String, selectionStart: Int, selectionEnd: Int) -> Unit,
    onSelectionChange: (selectionStart: Int, selectionEnd: Int) -> Unit,
    onFocusLost: () -> Unit,
    modifier: Modifier = Modifier,
    /** メディアの差し込み・ブロック結合の直後など、自動でカーソルを入れたい位置。不要なら null。 */
    requestFocusAt: Int? = null,
    onFocusHandled: () -> Unit = {},
    /** 先頭でバックスペースが押されたとき（前のブロックと結合する合図）。 */
    onBackspaceAtStart: () -> Unit = {},
    /** 先頭で「←」が押されたとき。 */
    onMoveToPreviousBlock: () -> Unit = {},
    /** 末尾で「→」が押されたとき。 */
    onMoveToNextBlock: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }

    // カーソル位置は入力欄が持ち、確定した文字列は ViewModel が持つ
    var fieldValue by remember(block.id) { mutableStateOf(TextFieldValue(block.text)) }
    if (fieldValue.text != block.text) {
        // メディアの差し込みなど、入力以外の理由で本文が変わったときに追従する
        val safeSelection = block.text.length.coerceAtMost(fieldValue.selection.end)
        fieldValue = fieldValue.copy(
            text = block.text,
            selection = TextRange(safeSelection),
        )
    }

    LaunchedEffect(requestFocusAt, block.text) {
        val at = requestFocusAt ?: return@LaunchedEffect
        fieldValue = fieldValue.copy(
            text = block.text,
            selection = TextRange(at.coerceIn(0, block.text.length)),
        )
        // 直前に生成されたばかりのノードだと要求が通らないことがあるため握りつぶす
        runCatching { focusRequester.requestFocus() }
        onFocusHandled()
    }

    // 行間は設定（余白 大・中・小）から決まる。ブロック間の余白も同じ値を使う
    val metrics = rememberBodyTextMetrics()

    // 書いている行がツールバーやキーボードの裏に回り込まないよう、本文をスクロールさせる。
    // 入力欄は本文全体の高さで並んでいて自前のスクロールを持たないため、
    // カーソルの位置を外側のスクロールへ知らせるのは入力欄の役目になる
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var textLayout by remember(block.id) { mutableStateOf<TextLayoutResult?>(null) }
    var isFocused by remember(block.id) { mutableStateOf(false) }

    LaunchedEffect(isFocused, fieldValue.selection, textLayout) {
        if (!isFocused) return@LaunchedEffect
        val layout = textLayout ?: return@LaunchedEffect
        // 行頭記号のぶん表示上の位置がずれるので、本文の位置から取り直す
        val cursor = richTextOffsetMapping(fieldValue.text, block.spans)
            .originalToTransformed(fieldValue.selection.end)
            .coerceIn(0, layout.layoutInput.text.length)
        // カーソルが縁にぴったり付くと窮屈なので、1行ぶん余分に見せる
        val caret = layout.getCursorRect(cursor)
        bringIntoViewRequester.bringIntoView(caret.inflate(caret.height))
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (fieldValue.text.isEmpty()) {
            Text(
                text = placeholder,
                style = metrics.paragraphStyle,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                val textChanged = newValue.text != fieldValue.text
                fieldValue = newValue
                if (textChanged) {
                    onTextChange(newValue.text, newValue.selection.start, newValue.selection.end)
                } else {
                    onSelectionChange(newValue.selection.start, newValue.selection.end)
                }
            },
            textStyle = metrics.paragraphStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = RichTextVisualTransformation(
                spans = block.spans,
                headingStyle = metrics.headingSpanStyle,
                headingLineHeight = metrics.headingLineHeight,
            ),
            onTextLayout = { textLayout = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .bringIntoViewRequester(bringIntoViewRequester)
                // ブロックの端でのキー操作は入力欄の中では行き場がないので、
                // ここで拾って隣のブロックへの移動・結合に振り替える。
                // 端以外はそのまま入力欄に任せる（通常のカーソル移動・削除・改行）
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    val selection = fieldValue.selection
                    val atStart = selection.collapsed && selection.start == 0
                    val atEnd = selection.collapsed &&
                        selection.start == fieldValue.text.length

                    when {
                        event.key == Key.Backspace && atStart -> {
                            onBackspaceAtStart()
                            true
                        }

                        event.key == Key.DirectionLeft && atStart -> {
                            onMoveToPreviousBlock()
                            true
                        }

                        event.key == Key.DirectionRight && atEnd -> {
                            onMoveToNextBlock()
                            true
                        }

                        else -> false
                    }
                }
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (focusState.isFocused) {
                        onSelectionChange(fieldValue.selection.start, fieldValue.selection.end)
                    } else {
                        onFocusLost()
                    }
                },
        )
    }
}
