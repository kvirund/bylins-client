package com.bylins.client.ui.scroll

/**
 * Точка выделения в координатах (абсолютный seq строки, столбец).
 * Хранение в seq (а не в char-offset) позволяет выделению переживать
 * вытеснение строк из буфера так же, как якорю скролла.
 */
data class SelPoint(val seq: Long, val col: Int)

/**
 * Чистая модель выделения текста по всему буферу (без Compose).
 *
 * Выделение задаётся парой anchor/focus в координатах (seq, col) и не зависит
 * от того, раздвоено окно или нет, и от конкретных панелей — поэтому переживает
 * раздвоение/схлопывание во время drag. Перевод в символьный диапазон делается
 * относительно текущего firstSeq и видимого (plain) текста.
 */
class OutputSelection {
    var anchor: SelPoint? = null
        private set
    var focus: SelPoint? = null
        private set

    val isEmpty: Boolean get() = anchor == null || anchor == focus

    fun start(p: SelPoint) {
        anchor = p
        focus = p
    }

    fun extendTo(p: SelPoint) {
        if (anchor == null) anchor = p
        focus = p
    }

    fun clear() {
        anchor = null
        focus = null
    }

    /** Выделить весь буфер от первой до последней строки. */
    fun selectAll(firstSeq: Long, lineCount: Int) {
        if (lineCount <= 0) {
            clear()
            return
        }
        anchor = SelPoint(firstSeq, 0)
        focus = SelPoint(firstSeq + lineCount - 1, Int.MAX_VALUE)
    }

    /** Нормализованная пара (начало, конец) по порядку в буфере, или null если пусто. */
    fun normalized(): Pair<SelPoint, SelPoint>? {
        val a = anchor ?: return null
        val f = focus ?: return null
        if (a == f) return null
        return if (compare(a, f) <= 0) a to f else f to a
    }

    /**
     * Диапазон выделения в символах [plainText] (видимый текст без ANSI).
     * При вытеснении строки (seq < firstSeq) точка подтягивается к началу буфера.
     */
    fun charRange(firstSeq: Long, plainText: String): IntRange? {
        val (min, max) = normalized() ?: return null
        val startOff = pointToOffset(min, firstSeq, plainText)
        val endOff = pointToOffset(max, firstSeq, plainText)
        if (startOff >= endOff) return null
        return startOff until endOff
    }

    /** Текст выделения (включая скрытую «середину» при разрыве панелей). */
    fun copyText(firstSeq: Long, plainText: String): String {
        val r = charRange(firstSeq, plainText) ?: return ""
        return plainText.substring(r.first, r.last + 1)
    }

    private fun pointToOffset(p: SelPoint, firstSeq: Long, plainText: String): Int {
        val lineIndex = (p.seq - firstSeq).toInt().coerceAtLeast(0)
        val col = if (p.seq < firstSeq) 0 else p.col
        return BufferOffsets.offsetOfLineCol(plainText, lineIndex, col)
    }

    private fun compare(a: SelPoint, b: SelPoint): Int =
        if (a.seq != b.seq) a.seq.compareTo(b.seq) else a.col.compareTo(b.col)
}
