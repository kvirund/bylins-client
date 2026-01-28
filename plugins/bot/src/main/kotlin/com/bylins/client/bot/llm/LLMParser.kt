package com.bylins.client.bot.llm

import com.bylins.client.bot.*
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.chat.ChatLanguageModel
import kotlinx.serialization.json.Json
import mu.KotlinLogging

/**
 * Парсер текста с использованием LLM (Ollama)
 * Используется для понимания описаний комнат, боевых сообщений и результатов осмотра
 */
private val logger = KotlinLogging.logger("LLMParser")

class LLMParser(
    private val baseUrl: String = "http://localhost:11434",
    private val modelName: String = "llama3"
) {
    private var model: ChatLanguageModel? = null
    private var isInitialized = false
    private var lastError: String? = null

    /**
     * Инициализировать подключение к Ollama
     */
    fun initialize(): Boolean {
        return try {
            model = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.1)  // Низкая температура для детерминированных ответов
                .build()

            // Тестовый запрос
            val response = model?.generate("Say 'OK' if you can understand this.")
            isInitialized = response?.contains("OK") == true

            if (isInitialized) {
                logger.info { "LLM Parser initialized with model: $modelName" }
            } else {
                logger.warn { "LLM Parser initialization test failed" }
            }

            isInitialized
        } catch (e: Exception) {
            lastError = e.message
            logger.error { "Failed to initialize LLM Parser: ${e.message}" }
            false
        }
    }

    /**
     * Проверить доступность
     */
    fun isAvailable(): Boolean = isInitialized && model != null

    /**
     * Парсить описание комнаты для извлечения мобов
     */
    fun parseRoomDescription(roomDescription: String): RoomParseResult {
        if (!isAvailable()) {
            return RoomParseResult(confidence = 0.0)
        }

        val prompt = """
            Analyze this Russian MUD game room description and extract information about creatures (mobs) present.

            Room description:
            ---
            $roomDescription
            ---

            Return a JSON object with the following structure:
            {
                "mobs": [
                    {
                        "name": "full name in Russian",
                        "shortName": "short name for commands (last word or key word)",
                        "isAggressive": true/false (if seems hostile/aggressive),
                        "condition": "EXCELLENT/GOOD/SLIGHTLY_HURT/HURT/BADLY_HURT/AWFUL",
                        "position": "STANDING/SITTING/RESTING/SLEEPING/FIGHTING"
                    }
                ],
                "isDangerous": true/false (if room seems dangerous),
                "features": ["list of notable features"]
            }

            Return ONLY valid JSON, no explanation.
        """.trimIndent()

        return try {
            val response = model?.generate(prompt) ?: return RoomParseResult(confidence = 0.0)
            parseRoomResponse(response)
        } catch (e: Exception) {
            logger.error { "Error parsing room description: ${e.message}" }
            RoomParseResult(confidence = 0.0)
        }
    }

    /**
     * Парсить боевое сообщение
     */
    fun parseCombatMessage(message: String): CombatParseResult? {
        if (!isAvailable()) {
            return null
        }

        val prompt = """
            Analyze this Russian MUD combat message and extract combat information.

            Message:
            ---
            $message
            ---

            Return a JSON object with:
            {
                "type": "DAMAGE_DEALT/DAMAGE_RECEIVED/MOB_KILLED/PLAYER_DEATH/FLEE_SUCCESS/FLEE_FAILED/SKILL_USED/MISS/DODGE/PARRY/BLOCK",
                "source": "who performed the action (or null)",
                "target": "who received the action (or null)",
                "damage": number (estimated damage, 0 if not applicable),
                "skill": "skill name if used (or null)",
                "isCritical": true/false
            }

            Return ONLY valid JSON, no explanation. If cannot parse, return {"type": "UNKNOWN"}.
        """.trimIndent()

        return try {
            val response = model?.generate(prompt) ?: return null
            parseCombatResponse(response, message)
        } catch (e: Exception) {
            logger.error { "Error parsing combat message: ${e.message}" }
            null
        }
    }

    /**
     * Парсить результат команды "осмотреть"
     */
    fun parseInspectResult(inspectText: String): InspectParseResult? {
        if (!isAvailable()) {
            return null
        }

        val prompt = """
            Analyze this Russian MUD "look at mob/player" result and extract information.

            Inspect result:
            ---
            $inspectText
            ---

            Return a JSON object with:
            {
                "name": "full name",
                "type": "MOB/PLAYER/ITEM",
                "level": estimated level (1-50 range, based on description like "looks very strong" = high level),
                "estimatedHp": estimated HP (based on description),
                "condition": "EXCELLENT/GOOD/SLIGHTLY_HURT/HURT/BADLY_HURT/AWFUL",
                "equipment": ["list of notable equipment"],
                "affects": ["list of visible magical effects"],
                "description": "brief description"
            }

            Return ONLY valid JSON, no explanation.
        """.trimIndent()

        return try {
            val response = model?.generate(prompt) ?: return null
            parseInspectResponse(response)
        } catch (e: Exception) {
            logger.error { "Error parsing inspect result: ${e.message}" }
            null
        }
    }

    /**
     * Оценить уровень моба по описанию
     */
    fun estimateMobLevel(mobDescription: String, playerLevel: Int): Int? {
        if (!isAvailable()) {
            return null
        }

        val prompt = """
            Based on this Russian MUD mob description, estimate the mob's level.
            Player level is $playerLevel for reference.

            Description:
            ---
            $mobDescription
            ---

            Common patterns:
            - "выглядит слабее вас" = lower level
            - "примерно вашего уровня" = same level
            - "выглядит сильнее вас" = higher level
            - "очень силен" = much higher level

            Return ONLY a number (1-50), nothing else.
        """.trimIndent()

        return try {
            val response = model?.generate(prompt)?.trim() ?: return null
            response.toIntOrNull()
        } catch (e: Exception) {
            logger.error { "Error estimating mob level: ${e.message}" }
            null
        }
    }

    // ============================================
    // Вспомогательные методы парсинга JSON ответов
    // ============================================

    private fun parseRoomResponse(response: String): RoomParseResult {
        return try {
            val json = extractJson(response)
            val parsed = Json.decodeFromString<RoomJsonResponse>(json)

            RoomParseResult(
                mobs = parsed.mobs.mapIndexed { index, mob ->
                    MobInfo(
                        name = mob.name,
                        shortName = mob.shortName,
                        isAggressive = mob.isAggressive,
                        condition = try { MobCondition.valueOf(mob.condition) } catch (e: Exception) { MobCondition.UNKNOWN },
                        position = try { Position.valueOf(mob.position) } catch (e: Exception) { Position.STANDING },
                        roomIndex = index + 1
                    )
                },
                isDangerous = parsed.isDangerous,
                features = parsed.features,
                confidence = 0.8
            )
        } catch (e: Exception) {
            logger.error { "Error parsing room JSON response: ${e.message}" }
            RoomParseResult(confidence = 0.0)
        }
    }

    private fun parseCombatResponse(response: String, rawText: String): CombatParseResult? {
        return try {
            val json = extractJson(response)
            val parsed = Json.decodeFromString<CombatJsonResponse>(json)

            if (parsed.type == "UNKNOWN") return null

            CombatParseResult(
                event = CombatEvent(
                    type = try { CombatEventType.valueOf(parsed.type) } catch (e: Exception) { CombatEventType.DAMAGE_DEALT },
                    source = parsed.source,
                    target = parsed.target,
                    damage = parsed.damage,
                    skill = parsed.skill,
                    isCritical = parsed.isCritical ?: false
                ),
                confidence = 0.7,
                rawText = rawText
            )
        } catch (e: Exception) {
            logger.error { "Error parsing combat JSON response: ${e.message}" }
            null
        }
    }

    private fun parseInspectResponse(response: String): InspectParseResult? {
        return try {
            val json = extractJson(response)
            val parsed = Json.decodeFromString<InspectJsonResponse>(json)

            InspectParseResult(
                mobInfo = if (parsed.type == "MOB") {
                    MobInfo(
                        name = parsed.name,
                        level = parsed.level,
                        hpPercent = null,
                        condition = try { MobCondition.valueOf(parsed.condition) } catch (e: Exception) { MobCondition.UNKNOWN }
                    )
                } else null,
                playerInfo = if (parsed.type == "PLAYER") {
                    PlayerInfo(name = parsed.name)
                } else null,
                estimatedLevel = parsed.level,
                estimatedHp = parsed.estimatedHp,
                confidence = 0.6
            )
        } catch (e: Exception) {
            logger.error { "Error parsing inspect JSON response: ${e.message}" }
            null
        }
    }

    /**
     * Извлечь JSON из ответа LLM (может содержать markdown или лишний текст)
     */
    private fun extractJson(response: String): String {
        // Ищем JSON в ответе
        val jsonStart = response.indexOf('{')
        val jsonEnd = response.lastIndexOf('}')

        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd + 1)
        }

        return response
    }

    fun getStatus(): Map<String, Any> {
        return mapOf(
            "initialized" to isInitialized,
            "available" to isAvailable(),
            "model" to modelName,
            "baseUrl" to baseUrl,
            "lastError" to (lastError ?: "")
        )
    }
}

// JSON response classes
@kotlinx.serialization.Serializable
private data class RoomJsonResponse(
    val mobs: List<MobJsonData> = emptyList(),
    val isDangerous: Boolean? = null,
    val features: List<String> = emptyList()
)

@kotlinx.serialization.Serializable
private data class MobJsonData(
    val name: String,
    val shortName: String? = null,
    val isAggressive: Boolean? = null,
    val condition: String = "UNKNOWN",
    val position: String = "STANDING"
)

@kotlinx.serialization.Serializable
private data class CombatJsonResponse(
    val type: String,
    val source: String? = null,
    val target: String? = null,
    val damage: Int? = null,
    val skill: String? = null,
    val isCritical: Boolean? = null
)

@kotlinx.serialization.Serializable
private data class InspectJsonResponse(
    val name: String,
    val type: String = "MOB",
    val level: Int? = null,
    val estimatedHp: Int? = null,
    val condition: String = "UNKNOWN",
    val equipment: List<String> = emptyList(),
    val affects: List<String> = emptyList(),
    val description: String? = null
)
