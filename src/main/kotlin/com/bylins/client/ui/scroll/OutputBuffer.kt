package com.bylins.client.ui.scroll

/**
 * Геометрия буфера вывода в терминах абсолютной нумерации строк (seq).
 * Чистый тип без Compose — используется логикой автоскролла.
 *
 * @param firstSeq абсолютный порядковый номер первой (верхней) строки в буфере
 * @param lineCount число логических строк (по '\n') в текущем буфере
 */
data class BufferGeometry(val firstSeq: Long, val lineCount: Int) {
    val lastSeq: Long get() = firstSeq + lineCount - 1
    val isEmpty: Boolean get() = lineCount == 0
}

/**
 * Снимок содержимого буфера вывода с абсолютной нумерацией строк.
 *
 * seq не обязан совпадать с «номером строки сервера» — это монотонный счётчик,
 * согласованный внутри текущего буфера: firstSeq = сколько строк уже вытеснено
 * сверху (скользящее окно). Позволяет заякорить позицию скролла/выделение на
 * конкретной строке и корректно отрабатывать вытеснение строк из буфера.
 *
 * @param text сырой текст буфера (может содержать ANSI-escape последовательности)
 * @param firstSeq абсолютный seq первой строки
 * @param lineCount число логических строк в [text]
 */
data class ContentSnapshot(
    val text: String,
    val firstSeq: Long,
    val lineCount: Int
) {
    val geometry: BufferGeometry get() = BufferGeometry(firstSeq, lineCount)

    companion object {
        val EMPTY = ContentSnapshot("", 0L, 0)

        /** Число логических строк (по '\n'); пустой текст = 0 строк. */
        fun countLines(text: String): Int =
            if (text.isEmpty()) 0 else text.count { it == '\n' } + 1
    }
}
