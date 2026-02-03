package com.bylins.client.bot

import com.bylins.client.bot.perception.*
import com.bylins.client.plugins.PluginAPI
import kotlinx.coroutines.*
import mu.KotlinLogging

/**
 * Ядро бота - упрощённая версия
 *
 * Функционал:
 * - Определение промпта (PromptDetector)
 * - Парсинг статов из промпта через regex
 * - Парсинг команды "сч" (ScoreParser)
 */
private val logger = KotlinLogging.logger("BotCore")

class BotCore(
    private val api: PluginAPI
) {
    // Парсинг
    val promptParser by lazy { PromptParser() }
    val scoreParser by lazy { ScoreParser() }
    val promptDetector by lazy {
        PromptDetector(
            onTextReceived = { batchText -> handleTextReceived(batchText) },
            onPromptReceived = { prompt, parsed -> handlePromptReceived(prompt, parsed) },
            onPatternInvalid = { _, _ -> }
        )
    }

    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Callback для логирования
    var onLog: ((String) -> Unit)? = null

    // Callback для обработки распарсенного промпта
    var onPromptParsed: ((prompt: String, parsed: Map<String, String>) -> Unit)? = null

    // Callback для обработки блока текста (для ScoreParser и др.)
    var onTextBlock: ((text: String) -> Unit)? = null

    /**
     * Обработать входящую строку от сервера
     */
    fun processLine(line: String, timestamp: Long = System.currentTimeMillis()) {
        promptDetector.processLine(line, timestamp)
    }

    /**
     * Проверить таймаут для определения промпта
     */
    fun checkPromptTimeout(timestamp: Long = System.currentTimeMillis()) {
        promptDetector.checkTimeout(timestamp)
    }

    // ============================================
    // Обработчики PromptDetector
    // ============================================

    private fun handleTextReceived(batchText: String) {
        // Пробуем распарсить как вывод "сч"
        val score = scoreParser.tryParse(batchText)
        if (score != null) {
            log("Score parsed: level=${score.level}, hp=${score.hp}/${score.maxHp}")
        }

        // Вызываем callback для внешней обработки
        onTextBlock?.invoke(batchText)
    }

    private fun handlePromptReceived(prompt: String, parsed: Map<String, String>?) {
        if (parsed != null && parsed.isNotEmpty()) {
            log("Prompt parsed: ${parsed.entries.joinToString { "${it.key}=${it.value}" }}")
            // Вызываем callback с распарсенными данными
            onPromptParsed?.invoke(prompt, parsed)
        } else {
            log("Prompt (no match): $prompt")
        }
    }

    // ============================================
    // Утилиты
    // ============================================

    fun log(message: String) {
        onLog?.invoke(message)
    }

    fun getStatus(): Map<String, Any> {
        return mapOf(
            "promptDetector" to promptDetector.getStatus()
        )
    }

    fun shutdown() {
        scope.cancel()
    }
}
