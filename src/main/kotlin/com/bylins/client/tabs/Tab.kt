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
    val maxLines: Int = 2000  // Уменьшено с 10000 до 2000 для экономии памяти
) {
    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content

    // Индикатор непрочитанных сообщений (для неактивных вкладок)
    private val _hasUnreadMessages = MutableStateFlow(false)
    val hasUnreadMessages: StateFlow<Boolean> = _hasUnreadMessages

    private val lines = mutableListOf<String>()

    // Счётчик для оптимизации обновлений
    private var updateCounter = 0

    /**
     * Добавляет текст во вкладку
     * @param markUnread если true, помечает вкладку как имеющую непрочитанные сообщения
     */
    fun appendText(text: String, markUnread: Boolean = false) {
        // Разбиваем на строки
        val newLines = text.split("\n")

        // Добавляем новые строки
        for (line in newLines) {
            if (line.isEmpty() && lines.isNotEmpty() && lines.last().isEmpty()) {
                // Пропускаем дублирующиеся пустые строки
                continue
            }
            lines.add(line)
        }

        // Ограничиваем количество строк
        while (lines.size > maxLines) {
            lines.removeAt(0)
        }

        // Помечаем непрочитанные сообщения
        if (markUnread) {
            _hasUnreadMessages.value = true
        }

        // Обновляем содержимое только каждые N добавлений или если буфер большой
        // Увеличен интервал обновления для экономии памяти (меньше создаётся строк)
        updateCounter++
        if (updateCounter >= 50 || lines.size > maxLines * 0.95) {
            updateCounter = 0
            _content.value = lines.joinToString("\n")
        }
    }

    /**
     * Принудительно обновляет содержимое (для немедленного отображения)
     */
    fun flush() {
        if (updateCounter > 0) {
            updateCounter = 0
            _content.value = lines.joinToString("\n")
        }
    }

    /**
     * Очищает содержимое вкладки
     */
    fun clear() {
        lines.clear()
        _content.value = ""
        updateCounter = 0
    }

    /**
     * Сбрасывает индикатор непрочитанных сообщений
     */
    fun markAsRead() {
        _hasUnreadMessages.value = false
    }

    /**
     * Проверяет, должна ли строка попасть в эту вкладку
     * @param cleanLine строка без ANSI-кодов
     * @param rawLine оригинальная строка с ANSI-кодами
     * @return трансформированная строка или null если не матчит
     */
    fun captureAndTransform(cleanLine: String, rawLine: String): String? {
        if (filters.isEmpty()) return null
        for (filter in filters) {
            val result = filter.transform(cleanLine, rawLine)
            if (result != null) return result
        }
        return null
    }
}

/**
 * Фильтр для захвата текста во вкладку
 * @param pattern regex паттерн для матчинга
 * @param replacement строка замены (null = копировать как есть, иначе применить замену с $1, $2...)
 * @param matchWithColors true = матчить по строке с ANSI-кодами цветов
 */
data class TabFilter(
    val pattern: Regex,
    val replacement: String? = null,  // null = копировать как есть
    val matchWithColors: Boolean = false,
    val includeMatched: Boolean = true  // deprecated, kept for compatibility
) {
    /**
     * Трансформирует строку если она матчит паттерн
     * @param cleanLine строка без ANSI-кодов
     * @param rawLine оригинальная строка с ANSI-кодами
     * @return трансформированная строка или null если не матчит
     */
    fun transform(cleanLine: String, rawLine: String): String? {
        val lineToMatch = if (matchWithColors) rawLine else cleanLine
        val match = pattern.find(lineToMatch) ?: return null

        // Если замена не задана - возвращаем оригинальную строку
        if (replacement == null) {
            return rawLine
        }

        // Применяем замену с поддержкой $0, $1, $2...
        var result = replacement
        match.groupValues.forEachIndexed { index, value ->
            result = result!!.replace("\$$index", value)
        }
        return result
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
     * Перемещает текст в эту вкладку, удаляя из основной
     */
    MOVE
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
    val maxLines: Int = 10000,
    val content: String? = null  // Сохранённое содержимое вкладки
) {
    fun toTab(): Tab {
        // ONLY был удалён, старые конфиги с ONLY будут использовать COPY
        val mode = try {
            CaptureMode.valueOf(captureMode)
        } catch (e: IllegalArgumentException) {
            CaptureMode.COPY
        }
        val tab = Tab(
            id = id,
            name = name,
            filters = filters.map { it.toTabFilter() },
            captureMode = mode,
            maxLines = maxLines
        )
        // Восстанавливаем содержимое
        if (!content.isNullOrEmpty()) {
            tab.appendText(content)
            tab.flush()
        }
        return tab
    }

    companion object {
        fun fromTab(tab: Tab): TabDto {
            return TabDto(
                id = tab.id,
                name = tab.name,
                filters = tab.filters.map { TabFilterDto.fromTabFilter(it) },
                captureMode = tab.captureMode.name,
                maxLines = tab.maxLines,
                content = tab.content.value.takeIf { it.isNotEmpty() }
            )
        }
    }
}

@Serializable
data class TabFilterDto(
    val pattern: String,
    val replacement: String? = null,
    val matchWithColors: Boolean = false,
    val includeMatched: Boolean = true  // deprecated
) {
    fun toTabFilter(): TabFilter {
        return TabFilter(
            pattern = pattern.toRegex(),
            replacement = replacement,
            matchWithColors = matchWithColors
        )
    }

    companion object {
        fun fromTabFilter(filter: TabFilter): TabFilterDto {
            return TabFilterDto(
                pattern = filter.pattern.pattern,
                replacement = filter.replacement,
                matchWithColors = filter.matchWithColors
            )
        }
    }
}
