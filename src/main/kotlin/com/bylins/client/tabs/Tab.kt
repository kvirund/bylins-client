package com.bylins.client.tabs

import com.bylins.client.ui.scroll.ContentSnapshot
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
    val maxLines: Int = 2000,  // Уменьшено с 10000 до 2000 для экономии памяти
    val isPluginTab: Boolean = false,  // Вкладка создана плагином (не редактируется пользователем)
    val profileTab: Boolean = false,   // Видна только на своём сервере (определение в профиле)
    val profileLog: Boolean = false,   // Лог свой на каждый сервер (profileTab ⟹ profileLog)
    val persistContent: Boolean = false // Сохранять лог вкладки между запусками (по умолчанию нет)
) {
    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content

    // Снимок с абсолютной нумерацией строк (для логики автоскролла/выделения)
    private val _snapshot = MutableStateFlow(ContentSnapshot.EMPTY)
    val snapshot: StateFlow<ContentSnapshot> = _snapshot

    // Индикатор непрочитанных сообщений (для неактивных вкладок)
    private val _hasUnreadMessages = MutableStateFlow(false)
    val hasUnreadMessages: StateFlow<Boolean> = _hasUnreadMessages

    private val lines = mutableListOf<String>()

    // Сколько строк уже вытеснено из начала буфера (скользящее окно).
    // Даёт абсолютный seq первой строки буфера; монотонно растёт.
    private var evictedLines: Long = 0L

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

        // Ограничиваем количество строк (вытесняем из начала, считаем вытесненные)
        while (lines.size > maxLines) {
            lines.removeAt(0)
            evictedLines++
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
            publish()
        }
    }

    /**
     * Принудительно обновляет содержимое (для немедленного отображения)
     */
    fun flush() {
        if (updateCounter > 0) {
            updateCounter = 0
            publish()
        }
    }

    /**
     * Очищает содержимое вкладки
     */
    fun clear() {
        // Сохраняем монотонность seq: считаем очищенные строки вытесненными
        evictedLines += lines.size
        lines.clear()
        _content.value = ""
        _snapshot.value = ContentSnapshot("", evictedLines, 0)
        updateCounter = 0
    }

    /**
     * Публикует текущее содержимое в content и snapshot согласованно
     */
    private fun publish() {
        val text = lines.joinToString("\n")
        _content.value = text
        _snapshot.value = ContentSnapshot(text, evictedLines, lines.size)
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
    val content: String? = null,  // Сохранённое содержимое вкладки (только если persistContent)
    val perProfile: Boolean = false,   // legacy: старое поле, мигрируется в profileTab
    val profileTab: Boolean = false,
    val profileLog: Boolean = false,
    val persistContent: Boolean = false
) {
    fun toTab(): Tab {
        // ONLY был удалён, старые конфиги с ONLY будут использовать COPY
        val mode = try {
            CaptureMode.valueOf(captureMode)
        } catch (e: IllegalArgumentException) {
            CaptureMode.COPY
        }
        // Миграция: старое perProfile == профильная вкладка; каскад profileTab ⟹ profileLog
        val pt = profileTab || perProfile
        val pl = profileLog || pt
        val tab = Tab(
            id = id,
            name = name,
            filters = filters.map { it.toTabFilter() },
            captureMode = mode,
            maxLines = maxLines,
            profileTab = pt,
            profileLog = pl,
            persistContent = persistContent
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
                // Лог в самом TabDto храним, только если он НЕ профильный (профильный
                // лог глобальной вкладки лежит в profile.tabLogs). Для профильной вкладки
                // (profileTab) её лог хранится здесь же, в её TabDto внутри профиля.
                content = if (tab.persistContent && (tab.profileTab || !tab.profileLog))
                    tab.content.value.takeIf { it.isNotEmpty() } else null,
                profileTab = tab.profileTab,
                profileLog = tab.profileLog,
                persistContent = tab.persistContent
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
