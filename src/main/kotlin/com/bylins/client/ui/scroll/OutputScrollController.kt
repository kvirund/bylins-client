package com.bylins.client.ui.scroll

/**
 * Целевая позиция скролла, вычисленная контроллером для скроллбэк-панели.
 */
sealed interface ScrollTarget {
    /** Прижать к низу (follow / схлопнутое окно). */
    data object Bottom : ScrollTarget

    /** Показать верх логической строки [seq]. */
    data class ToLine(val seq: Long) : ScrollTarget

    /** Ничего не делать. */
    data object None : ScrollTarget
}

/**
 * Чистая логика автоскролла панели вывода (без Compose).
 *
 * Два режима:
 * - followMode = true  — окно «схлопнуто», прижато к низу, автоскролл за новым текстом;
 * - followMode = false — окно «раздвоено», верхняя панель заякорена на [anchorSeq].
 *
 * Оперирует абсолютными порядковыми номерами строк (seq), а не пикселями,
 * поэтому полностью детерминирован и тестируем.
 */
class OutputScrollController {
    var followMode: Boolean = true
        private set

    var anchorSeq: Long = -1L
        private set

    /** Раздвоено ли окно (пользователь прокрутил вверх). */
    val isSplit: Boolean get() = !followMode

    /**
     * Реакция на пользовательский скролл активной панели.
     * @param atBottom находится ли видимая область у самого низа буфера
     * @param visibleTopSeq seq верхней видимой строки (для якоря)
     */
    fun onUserScroll(atBottom: Boolean, visibleTopSeq: Long) {
        if (atBottom) {
            followMode = true
            anchorSeq = -1L
        } else {
            followMode = false
            anchorSeq = visibleTopSeq
        }
    }

    /**
     * Вычисляет целевую позицию скроллбэк-панели при изменении буфера.
     * При вытеснении заякоренной строки из буфера — подтягивает якорь к началу
     * буфера (позиция «ползёт» вверх, не дёргается к низу).
     */
    fun onContentChanged(g: BufferGeometry): ScrollTarget {
        if (g.isEmpty) return ScrollTarget.None
        if (followMode) return ScrollTarget.Bottom
        return when {
            anchorSeq < g.firstSeq -> {
                anchorSeq = g.firstSeq
                ScrollTarget.ToLine(g.firstSeq)
            }
            anchorSeq > g.lastSeq -> ScrollTarget.Bottom
            else -> ScrollTarget.ToLine(anchorSeq)
        }
    }

    /** Схлопнуть окно и включить follow. */
    fun jumpToBottom() {
        followMode = true
        anchorSeq = -1L
    }

    /** Заякорить на строке [seq] (для перехода к результату поиска). */
    fun jumpToLine(seq: Long) {
        followMode = false
        anchorSeq = seq
    }
}
