package com.bylins.client.ui.scroll

/** Совпадение поиска: полуинтервал [start, end) в координатах видимого (plain) текста. */
data class SearchMatch(val start: Int, val end: Int)

/**
 * Чистая логика поиска по тексту панели вывода (без Compose).
 *
 * Находит все совпадения (подстрока или regex), хранит текущий индекс и умеет
 * переходить к следующему/предыдущему по кругу. Совпадения — char-офсеты в plain
 * тексте; для перехода скролла их переводят в seq строки выше по стеку.
 */
class OutputSearch {
    var query: String = ""
        private set
    var caseSensitive: Boolean = false
    var useRegex: Boolean = false
    var regexError: Boolean = false
        private set

    private val _matches = mutableListOf<SearchMatch>()
    val matches: List<SearchMatch> get() = _matches

    var currentIndex: Int = -1
        private set

    val count: Int get() = _matches.size
    val current: SearchMatch? get() = _matches.getOrNull(currentIndex)
    val isActive: Boolean get() = query.isNotEmpty()

    /**
     * Пересчитывает совпадения для [query] в [plainText]. При смене запроса текущий
     * индекс сбрасывается на первое совпадение; при том же запросе (изменился текст) —
     * сохраняется, насколько возможно.
     */
    fun update(query: String, plainText: String) {
        val queryChanged = query != this.query
        this.query = query
        regexError = useRegex && query.isNotEmpty() && !isValidRegex(query)
        _matches.clear()
        _matches.addAll(findMatches(plainText, query, caseSensitive, useRegex))
        currentIndex = when {
            _matches.isEmpty() -> -1
            queryChanged || currentIndex < 0 -> 0
            else -> currentIndex.coerceIn(0, _matches.size - 1)
        }
    }

    fun next() { if (count > 0) currentIndex = (currentIndex + 1).mod(count) }
    fun prev() { if (count > 0) currentIndex = (currentIndex - 1).mod(count) }

    fun clear() {
        query = ""
        _matches.clear()
        currentIndex = -1
        regexError = false
    }

    companion object {
        private fun isValidRegex(pattern: String): Boolean =
            try { Regex(pattern); true } catch (e: Exception) { false }

        /** Все непересекающиеся совпадения [query] в [text]. */
        fun findMatches(
            text: String,
            query: String,
            caseSensitive: Boolean,
            useRegex: Boolean
        ): List<SearchMatch> {
            if (query.isEmpty() || text.isEmpty()) return emptyList()
            return if (useRegex) {
                val opts = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                try {
                    Regex(query, opts).findAll(text)
                        .filter { it.value.isNotEmpty() }
                        .map { SearchMatch(it.range.first, it.range.last + 1) }
                        .toList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                val result = mutableListOf<SearchMatch>()
                val hay = if (caseSensitive) text else text.lowercase()
                val needle = if (caseSensitive) query else query.lowercase()
                var i = hay.indexOf(needle)
                while (i >= 0) {
                    result.add(SearchMatch(i, i + needle.length))
                    i = hay.indexOf(needle, i + needle.length)
                }
                result
            }
        }
    }
}
