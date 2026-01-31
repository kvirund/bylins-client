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
 */
private val logger = KotlinLogging.logger("BotCore")

class BotCore(
    private val api: PluginAPI
) {
    // Парсинг
    val promptParser by lazy { PromptParser() }
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
        val lineCount = batchText.lines().size
        log("Text: $lineCount lines")
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handlePromptReceived(prompt: String, parsed: Map<String, String>?) {
        if (parsed != null && parsed.isNotEmpty()) {
            log("Prompt: $prompt -> ${parsed.entries.joinToString { "${it.key}=${it.value}" }}")
        } else {
            log("Prompt: $prompt")
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
