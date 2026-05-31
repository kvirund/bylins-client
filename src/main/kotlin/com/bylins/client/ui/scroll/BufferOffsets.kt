package com.bylins.client.ui.scroll

/**
 * Чистый маппинг между символьными offset'ами текста и логическими строками
 * (разделитель '\n'). Используется для перевода seq строки в позицию скролла
 * и для модели выделения. Без Compose — тестируется юнитами.
 */
object BufferOffsets {

    /** Char-offset начала логической строки [lineIndex] (0-based). */
    fun lineStartOffset(text: String, lineIndex: Int): Int {
        if (lineIndex <= 0) return 0
        var seen = 0
        var i = 0
        while (i < text.length) {
            if (text[i] == '\n') {
                seen++
                if (seen == lineIndex) return i + 1
            }
            i++
        }
        return text.length
    }

    /** Индекс логической строки, содержащей [offset]. */
    fun lineIndexOfOffset(text: String, offset: Int): Int {
        val clamped = offset.coerceIn(0, text.length)
        var count = 0
        var i = 0
        while (i < clamped) {
            if (text[i] == '\n') count++
            i++
        }
        return count
    }

    /** (индекс строки, столбец) для [offset]. */
    fun lineColOfOffset(text: String, offset: Int): Pair<Int, Int> {
        val clamped = offset.coerceIn(0, text.length)
        var line = 0
        var lineStart = 0
        var i = 0
        while (i < clamped) {
            if (text[i] == '\n') {
                line++
                lineStart = i + 1
            }
            i++
        }
        return line to (clamped - lineStart)
    }

    /** Char-offset для (строка, столбец); столбец клампится по длине строки. */
    fun offsetOfLineCol(text: String, lineIndex: Int, col: Int): Int {
        val start = lineStartOffset(text, lineIndex)
        var end = start
        while (end < text.length && text[end] != '\n') end++
        val c = col.coerceIn(0, end - start)
        return start + c
    }
}
