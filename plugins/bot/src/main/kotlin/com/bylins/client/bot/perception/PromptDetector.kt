package com.bylins.client.bot.perception

import mu.KotlinLogging

/**
 * Адаптивный детектор промпта на основе временных интервалов.
 *
 * Логика определения:
 * - Промпт = строка ПЕРЕД большим интервалом
 * - Если время ожидания следующей строки превысило время получения ВСЕХ строк
 *   с момента последнего промпта - значит последняя полученная строка была промптом.
 *
 * Разделение текста:
 * - Batch (текст между промптами) - все строки до промпта
 * - Prompt - последняя строка в батче (определённая по таймауту)
 *
 * Валидация regex:
 * - Doubtful: regex сработал, но после него в батче ещё пришёл текст
 * - Invalid: определили промпт по таймаутам, но regex не матчится
 */
private val logger = KotlinLogging.logger("PromptDetector")

class PromptDetector(
    private val onTextReceived: (batchText: String) -> Unit,
    private val onPromptReceived: (prompt: String, parsed: Map<String, String>?) -> Unit,
    private val onPatternInvalid: (prompt: String, pattern: Regex) -> Unit
) {
    // Минимальный таймаут для определения промпта (мс)
    // Если после строки прошло больше этого времени - это промпт
    var promptTimeoutMs: Long = 300

    private var promptRegex: Regex? = null
    private var isRegexDoubtful = false
    private var regexMatchedLine: String? = null  // Строка, на которой сработал regex в текущем батче

    private var pendingLine: String? = null
    private var pendingLineTimestamp: Long = 0

    private val currentBatch = mutableListOf<String>()
    private val lineTimestamps = mutableListOf<Long>()
    private val recentPrompts = mutableListOf<String>()

    /**
     * Установить regex паттерн для парсинга промпта.
     * Именованные группы будут извлечены при матче.
     */
    fun setPromptPattern(regex: Regex?) {
        promptRegex = regex
        isRegexDoubtful = false
        regexMatchedLine = null
        logger.info { "Prompt pattern set: ${regex?.pattern}" }
    }

    /**
     * Получить текущий паттерн
     */
    fun getPromptPattern(): Regex? = promptRegex

    /**
     * Проверить, находится ли паттерн "под сомнением".
     * Это означает, что regex сработал, но после него в батче пришёл текст.
     */
    fun isPatternDoubtful(): Boolean = isRegexDoubtful

    /**
     * Обработать входящую строку.
     * @param line - текст строки
     * @param timestamp - время получения строки
     */
    fun processLine(line: String, timestamp: Long) {
        // Проверяем таймаут для предыдущей строки
        if (pendingLine != null) {
            val waitTime = timestamp - pendingLineTimestamp

            if (waitTime > promptTimeoutMs) {
                // Время ожидания превысило таймаут - pendingLine был промптом
                finalizeBatch(pendingLine!!)
            }
        }

        // Добавляем строку в текущий батч
        currentBatch.add(line)
        lineTimestamps.add(timestamp)

        // Проверяем regex для отслеживания doubtful состояния
        val regex = promptRegex
        if (regex != null && regex.containsMatchIn(line)) {
            if (regexMatchedLine != null) {
                // Второй матч в батче - паттерн под сомнением
                isRegexDoubtful = true
                logger.debug { "Pattern doubtful: multiple matches in batch" }
            }
            regexMatchedLine = line
        } else if (regexMatchedLine != null) {
            // После матча пришла строка без матча - паттерн под сомнением
            isRegexDoubtful = true
            logger.debug { "Pattern doubtful: text after regex match" }
        }

        pendingLine = line
        pendingLineTimestamp = timestamp
    }

    /**
     * Проверить таймаут для определения промпта.
     * Вызывать периодически (например, в tick бота).
     */
    fun checkTimeout(currentTime: Long) {
        if (pendingLine == null) return

        val waitTime = currentTime - pendingLineTimestamp

        if (waitTime > promptTimeoutMs) {
            finalizeBatch(pendingLine!!)
            pendingLine = null
        }
    }

    /**
     * Завершить батч: определить промпт и вызвать callback'и.
     */
    private fun finalizeBatch(promptLine: String) {
        // Текст батча = все строки кроме последней (которая промпт)
        val batchText = if (currentBatch.size > 1) {
            currentBatch.dropLast(1).joinToString("\n")
        } else ""

        // Пытаемся применить regex к ОПРЕДЕЛЁННОМУ промпту
        val regex = promptRegex
        val match = regex?.find(promptLine)

        // Извлекаем именованные группы
        val parsed: Map<String, String>? = match?.let { m ->
            val result = mutableMapOf<String, String>()
            // Получаем все именованные группы из regex
            val namedGroups = extractNamedGroups(regex.pattern, m)
            result.putAll(namedGroups)
            result.ifEmpty { null }
        }

        // Если есть regex, но он НЕ сработал на определённый промпт - он INVALID
        if (regex != null && match == null) {
            logger.warn { "Pattern invalid: regex did not match prompt: $promptLine" }
            onPatternInvalid(promptLine, regex)
        }

        // Вызываем callback'и
        if (batchText.isNotEmpty()) {
            onTextReceived(batchText)
        }
        onPromptReceived(promptLine, parsed)

        // Сохраняем промпт для истории
        recentPrompts.add(promptLine)
        if (recentPrompts.size > 100) {
            recentPrompts.removeAt(0)
        }

        logger.debug { "Batch finalized: ${currentBatch.size} lines, prompt: $promptLine" }

        // Сбрасываем состояние батча
        currentBatch.clear()
        lineTimestamps.clear()
        regexMatchedLine = null
    }

    /**
     * Извлечь именованные группы из match result.
     */
    private fun extractNamedGroups(pattern: String, match: MatchResult): Map<String, String> {
        val result = mutableMapOf<String, String>()

        // Находим все именованные группы в паттерне: (?<name>...)
        val namedGroupRegex = Regex("""\(\?<([a-zA-Z][a-zA-Z0-9]*)>""")
        val groupNames = namedGroupRegex.findAll(pattern).map { it.groupValues[1] }.toList()

        for (name in groupNames) {
            try {
                val value = match.groups[name]?.value
                if (value != null) {
                    result[name] = value
                }
            } catch (e: Exception) {
                // Группа не найдена - пропускаем
            }
        }

        return result
    }

    /**
     * Получить последние N промптов (для обучения AI).
     */
    fun getRecentPrompts(count: Int): List<String> = recentPrompts.takeLast(count)

    /**
     * Сбросить состояние детектора.
     */
    fun reset() {
        pendingLine = null
        pendingLineTimestamp = 0
        currentBatch.clear()
        lineTimestamps.clear()
        regexMatchedLine = null
        isRegexDoubtful = false
        logger.info { "PromptDetector reset" }
    }

    /**
     * Получить статус детектора.
     */
    fun getStatus(): Map<String, Any> = mapOf(
        "hasPattern" to (promptRegex != null),
        "patternDoubtful" to isRegexDoubtful,
        "currentBatchSize" to currentBatch.size,
        "recentPromptsCount" to recentPrompts.size,
        "pattern" to (promptRegex?.pattern ?: "")
    )
}
