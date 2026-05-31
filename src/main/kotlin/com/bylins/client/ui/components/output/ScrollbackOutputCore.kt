package com.bylins.client.ui.components.output

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import com.bylins.client.ui.scroll.BufferOffsets
import com.bylins.client.ui.scroll.SelPoint

/**
 * Портируемое ядро панели вывода: маппинг между абсолютным seq строки и
 * пиксельной позицией скролла (через TextLayoutResult) и отрисовка.
 * Использует только multiplatform-API Compose (без desktop-специфики).
 */

/** Максимальный сдвиг скролла для заданной высоты контента и вьюпорта. */
internal fun maxScrollOf(contentHeightPx: Float, viewportPx: Float): Float =
    (contentHeightPx - viewportPx).coerceAtLeast(0f)

/** Пиксельная позиция верха логической строки [seq] (для якоря/перехода). */
internal fun seqToTopPx(
    layout: TextLayoutResult,
    plainText: String,
    firstSeq: Long,
    seq: Long
): Float {
    val lineIndex = (seq - firstSeq).toInt().coerceAtLeast(0)
    val offset = BufferOffsets.lineStartOffset(plainText, lineIndex)
        .coerceIn(0, layout.layoutInput.text.length)
    val visualLine = layout.getLineForOffset(offset)
    return layout.getLineTop(visualLine)
}

/** seq верхней видимой строки при заданном сдвиге скролла. */
internal fun topSeqAt(
    layout: TextLayoutResult,
    plainText: String,
    firstSeq: Long,
    scrollPx: Float
): Long {
    val visualLine = layout.getLineForVerticalPosition(scrollPx)
    val offset = layout.getLineStart(visualLine)
    return firstSeq + BufferOffsets.lineIndexOfOffset(plainText, offset)
}

/** Точка выделения (seq, col) для позиции указателя в координатах контента. */
internal fun pointToSelPoint(
    layout: TextLayoutResult,
    plainText: String,
    firstSeq: Long,
    contentX: Float,
    contentY: Float
): SelPoint {
    val offset = layout.getOffsetForPosition(Offset(contentX, contentY))
        .coerceIn(0, plainText.length)
    val (line, col) = BufferOffsets.lineColOfOffset(plainText, offset)
    return SelPoint(firstSeq + line, col)
}

/** Последние [maxLines] строк текста (оптимизация парсинга больших буферов). */
internal fun lastLines(text: String, maxLines: Int): String {
    if (text.isEmpty()) return text
    var lineCount = 0
    var position = text.length - 1
    while (position >= 0 && lineCount < maxLines) {
        if (text[position] == '\n') lineCount++
        position--
    }
    if (position < 0) return text
    return text.substring(position + 1)
}

/**
 * Рисует одну панель-вьюпорт над общим layout: подсветку выделения и текст,
 * сдвинутые на [scrollPx] и обрезанные границами панели.
 */
@androidx.compose.runtime.Composable
internal fun OutputCanvas(
    layout: TextLayoutResult,
    scrollPx: Float,
    selectionPath: Path?,
    selectionColor: Color,
    modifier: Modifier
) {
    Canvas(modifier) {
        clipRect {
            translate(top = -scrollPx) {
                if (selectionPath != null) {
                    drawPath(selectionPath, color = selectionColor)
                }
                drawText(layout)
            }
        }
    }
}
