package com.bylins.client.tabs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Представляет вкладку с выводом текста
 */
data class Tab(
    val id: String,
    val name: String,
    val filters: List<TabFilter> = emptyList(),
    val captureMode: CaptureMode = CaptureMode.COPY,
    val maxLines: Int = 10000
) {
    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content

    private val lines = mutableListOf<String>()

    /**
     * Добавляет текст во вкладку
     */
    fun appendText(text: String) {
        // Разбиваем на строки
        val newLines = text.split("\n")
        lines.addAll(newLines)

        // Ограничиваем количество строк
        if (lines.size > maxLines) {
            val toRemove = lines.size - maxLines
            repeat(toRemove) { lines.removeAt(0) }
        }

        // Обновляем содержимое
        _content.value = lines.joinToString("\n")
    }

    /**
     * Очищает содержимое вкладки
     */
    fun clear() {
        lines.clear()
        _content.value = ""
    }

    /**
     * Проверяет, должна ли строка попасть в эту вкладку
     */
    fun shouldCapture(line: String): Boolean {
        if (filters.isEmpty()) return false
        return filters.any { it.matches(line) }
    }
}

/**
 * Фильтр для захвата текста во вкладку
 */
data class TabFilter(
    val pattern: Regex,
    val includeMatched: Boolean = true
) {
    /**
     * Проверяет, соответствует ли строка паттерну
     */
    fun matches(line: String): Boolean {
        return pattern.matches(line)
    }
}

/**
 * Режим захвата текста
 */
enum class CaptureMode {
    /**
     * Копирует текст в эту вкладку, оставляя в основной
     */
    COPY,

    /**
     * Перемещает текст в эту вкладку, удаляя из основной (redirect)
     */
    MOVE,

    /**
     * Только эта вкладка (не показывать в основной, но и не удалять)
     */
    ONLY
}

/**
 * DTO для сериализации
 */
@Serializable
data class TabDto(
    val id: String,
    val name: String,
    val filters: List<TabFilterDto> = emptyList(),
    val captureMode: String = "COPY",
    val maxLines: Int = 10000
) {
    fun toTab(): Tab {
        return Tab(
            id = id,
            name = name,
            filters = filters.map { it.toTabFilter() },
            captureMode = CaptureMode.valueOf(captureMode),
            maxLines = maxLines
        )
    }

    companion object {
        fun fromTab(tab: Tab): TabDto {
            return TabDto(
                id = tab.id,
                name = tab.name,
                filters = tab.filters.map { TabFilterDto.fromTabFilter(it) },
                captureMode = tab.captureMode.name,
                maxLines = tab.maxLines
            )
        }
    }
}

@Serializable
data class TabFilterDto(
    val pattern: String,
    val includeMatched: Boolean = true
) {
    fun toTabFilter(): TabFilter {
        return TabFilter(
            pattern = pattern.toRegex(),
            includeMatched = includeMatched
        )
    }

    companion object {
        fun fromTabFilter(filter: TabFilter): TabFilterDto {
            return TabFilterDto(
                pattern = filter.pattern.pattern,
                includeMatched = filter.includeMatched
            )
        }
    }
}
