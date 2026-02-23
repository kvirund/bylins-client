package com.bylins.client.assistant.perception

import mu.KotlinLogging

/**
 * Парсер содержимого комнаты (мобы и объекты) по ANSI-цветам.
 *
 * ANSI цвета в Былинах:
 * - Мобы: [1;31m (bright red)
 * - Объекты: [1;33m (bright yellow)
 * - Название комнаты: [1;36m (bright cyan)
 * - Сброс: [0;37m или [0;0m
 *
 * Формат описания комнаты:
 * [1;36mНазвание комнаты [VNUM][0;37m
 *    Описание комнаты...
 * [1;33mОбъект лежит здесь.
 * [1;31mМоб стоит здесь.
 * [0;0m
 */
private val logger = KotlinLogging.logger("RoomContentParser")

/**
 * Содержимое комнаты.
 */
data class RoomContent(
    val mobs: List<String>,      // Строки с мобами (красный цвет)
    val objects: List<String>    // Строки с объектами (жёлтый цвет)
) {
    fun isEmpty(): Boolean = mobs.isEmpty() && objects.isEmpty()
}

class RoomContentParser {

    // Callback при успешном парсинге
    var onRoomContentParsed: ((RoomContent) -> Unit)? = null

    // Последний распарсенный результат
    private var lastContent: RoomContent? = null

    // ANSI escape codes
    private val ESC = "\u001B"

    // Regex для удаления ANSI кодов
    private val ansiStripPattern = Regex("""\u001B\[[0-9;]*m""")

    // Regex для поиска ANSI кодов цвета в строке
    // Ищем [1;31m (bright red) или [1;33m (bright yellow)
    // Важно: используем ПОСЛЕДНИЙ код цвета перед текстом, т.к. сервер может
    // отправить &Y&q сразу перед &R&q если объектов нет
    private val colorPattern = Regex("""\u001B\[1;(31|33)m""")

    /**
     * Попытаться распарсить текст с ANSI-кодами для извлечения мобов и объектов.
     *
     * Правила:
     * - Каждая СТРОКА может дать не более одного моба/объекта
     * - Первый цветовой код строки определяет тип сущности (red=mob, yellow=object)
     * - Строка без цветового кода продолжает предыдущую сущность (multi-line)
     * - Сбросовые коды ([0;37m, [0;0m) игнорируются при определении типа
     *
     * @param rawText - текст С ANSI-кодами (не stripped)
     */
    fun tryParse(rawText: String): RoomContent? {
        // Проверяем, есть ли вообще ANSI-коды
        if (!rawText.contains(ESC)) {
            logger.debug { "RoomContentParser: no ANSI codes in text (${rawText.length} chars)" }
            return null
        }

        // LLM уже проверила, что это описание комнаты - просто парсим
        logger.debug { "RoomContentParser: parsing text with ${rawText.lines().size} lines" }

        val mobs = mutableListOf<String>()
        val objects = mutableListOf<String>()

        // Тип текущей сущности для multi-line (null = нет, "mob" или "object")
        var currentEntityType: String? = null
        var currentEntityText = StringBuilder()

        // Разбиваем на строки
        val lines = rawText.split("\n")

        for (line in lines) {
            // Пустая строка (или только ANSI-коды) - завершает текущую сущность
            val strippedLine = ansiStripPattern.replace(line, "").trim()
            if (strippedLine.isEmpty()) {
                finishEntity(currentEntityType, currentEntityText.toString(), mobs, objects)
                currentEntityType = null
                currentEntityText = StringBuilder()
                continue
            }

            // Ищем ВСЕ цветовые коды в строке и берём ПОСЛЕДНИЙ
            // Это важно: сервер отправляет &Y&q потом &R&q на одной строке если объектов нет
            val colorMatches = colorPattern.findAll(line).toList()
            val lastColorMatch = colorMatches.lastOrNull()

            if (lastColorMatch != null) {
                // Новая сущность - сначала завершаем предыдущую
                finishEntity(currentEntityType, currentEntityText.toString(), mobs, objects)

                // Определяем тип по ПОСЛЕДНЕМУ коду цвета (31=red=mob, 33=yellow=object)
                val colorCode = lastColorMatch.groupValues[1]
                currentEntityType = if (colorCode == "31") "mob" else "object"

                // Извлекаем текст строки без ANSI-кодов
                currentEntityText = StringBuilder(strippedLine)
            } else if (currentEntityType != null) {
                // Продолжение предыдущей сущности (multi-line)
                if (strippedLine.isNotEmpty()) {
                    if (currentEntityText.isNotEmpty()) {
                        currentEntityText.append(" ")
                    }
                    currentEntityText.append(strippedLine)
                }
            }
            // Строки без цветового кода и без текущей сущности - пропускаем
        }

        // Завершаем последнюю сущность
        finishEntity(currentEntityType, currentEntityText.toString(), mobs, objects)

        val content = RoomContent(mobs, objects)
        lastContent = content

        if (!content.isEmpty()) {
            onRoomContentParsed?.invoke(content)
            logger.debug { "Room content parsed: ${mobs.size} mobs, ${objects.size} objects" }
        }

        return content
    }

    /**
     * Завершает текущую сущность и добавляет в соответствующий список.
     */
    private fun finishEntity(
        entityType: String?,
        text: String,
        mobs: MutableList<String>,
        objects: MutableList<String>
    ) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty() || entityType == null) return

        when (entityType) {
            "mob" -> mobs.add(trimmedText)
            "object" -> objects.add(trimmedText)
        }
    }

    /**
     * Получить последний распарсенный результат.
     */
    fun getLastContent(): RoomContent? = lastContent

    /**
     * Сбросить состояние.
     */
    fun reset() {
        lastContent = null
    }
}
