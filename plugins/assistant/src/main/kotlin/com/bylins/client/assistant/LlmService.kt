package com.bylins.client.assistant

import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.ollama.OllamaChatModel
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.time.Duration

private val logger = KotlinLogging.logger("LlmService")

/**
 * Сервис для работы с LLM (Ollama).
 *
 * Rate limiting: не чаще 1 запроса в секунду.
 */
class LlmService(
    private val scope: CoroutineScope
) {
    private var chatModel: ChatLanguageModel? = null
    private var baseUrl: String = "http://localhost:11434"
    private var modelName: String = "llama3"

    // Rate limiting
    private var lastRequestTime: Long = 0
    private val minIntervalMs: Long = 1000  // Минимум 1 секунда между запросами

    /**
     * Инициализирован ли LLM.
     */
    val isConnected: Boolean
        get() = chatModel != null

    /**
     * Подключиться к Ollama.
     */
    fun connect(baseUrl: String, modelName: String): Boolean {
        this.baseUrl = baseUrl
        this.modelName = modelName

        return try {
            chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(30))
                .build()

            // Проверяем подключение простым запросом
            val response = chatModel?.generate("ping")
            if (response != null) {
                logger.info { "LLM connected: $modelName @ $baseUrl" }
                true
            } else {
                logger.warn { "LLM connection failed: no response" }
                chatModel = null
                false
            }
        } catch (e: Exception) {
            logger.error { "LLM connection error: ${e.message}" }
            chatModel = null
            false
        }
    }

    /**
     * Отключиться от LLM.
     */
    fun disconnect() {
        chatModel = null
        logger.info { "LLM disconnected" }
    }

    /**
     * Проверить, можно ли сейчас делать запрос (rate limiting).
     */
    private fun canMakeRequest(): Boolean {
        val now = System.currentTimeMillis()
        return now - lastRequestTime >= minIntervalMs
    }

    /**
     * Проверить, является ли текст описанием комнаты с мобами/объектами.
     *
     * @param text - текст без ANSI-кодов
     * @return true если это описание комнаты
     */
    suspend fun isRoomDescription(text: String): Boolean {
        val model = chatModel ?: return false

        // Rate limiting
        if (!canMakeRequest()) {
            logger.debug { "LLM rate limited, skipping isRoomDescription" }
            return false
        }

        val prompt = """
Ты анализируешь вывод MUD-игры Былины.

Определи: является ли этот текст описанием комнаты?

Описание комнаты содержит:
- Название локации (например: "Южные ворота Киева", "Комната отдыха")
- Описание местности (опционально)
- Мобы (существа) - обычно показаны красным цветом
- Объекты (предметы) - обычно показаны жёлтым цветом
- Выходы (например: "Выходы: север, юг")

НЕ является описанием комнаты:
- Вывод команды "счёт" (характеристики персонажа, опыт, уровень)
- Вывод команды "аффекты"
- Системные сообщения
- Диалоги

Текст:
---
$text
---

Ответь ТОЛЬКО одним словом: ДА или НЕТ
""".trim()

        return withContext(Dispatchers.IO) {
            try {
                lastRequestTime = System.currentTimeMillis()
                val response = model.generate(prompt)
                val answer = response.trim().uppercase()
                logger.debug { "LLM isRoomDescription: '$answer'" }
                answer.startsWith("ДА") || answer.startsWith("YES")
            } catch (e: Exception) {
                logger.error { "LLM error: ${e.message}" }
                false
            }
        }
    }

    /**
     * Общий запрос к LLM.
     */
    suspend fun ask(prompt: String): String? {
        val model = chatModel ?: return null

        // Rate limiting
        if (!canMakeRequest()) {
            logger.debug { "LLM rate limited, skipping ask" }
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                lastRequestTime = System.currentTimeMillis()
                model.generate(prompt)
            } catch (e: Exception) {
                logger.error { "LLM error: ${e.message}" }
                null
            }
        }
    }
}
