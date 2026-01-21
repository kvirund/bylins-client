package com.bylins.client.bot.perception

import com.bylins.client.bot.BotDatabase
import com.bylins.client.bot.llm.LLMParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging

/**
 * Адаптивный парсер боевых сообщений
 *
 * Использует комбинацию:
 * 1. Известные паттерны (rule-based)
 * 2. LLM для распознавания новых сообщений
 * 3. Обучение на основе обратной связи
 */
private val logger = KotlinLogging.logger("AdaptiveCombatParser")

class AdaptiveCombatParser(
    private val database: BotDatabase,
    private val llmParser: LLMParser? = null
) {
    private val mutex = Mutex()

    // Кэш известных паттернов (загружается из БД)
    private val knownPatterns = mutableMapOf<String, CombatMessageType>()

    // Паттерны для rule-based парсинга
    // Нанесение урона игроком
    private val damageDealtPatterns = listOf(
        // "Вы легонько ударили волка." / "Вы слегка ударили волка."
        Regex("""Вы (\w+) ударили (.+)\."""),
        // "Вы нанесли волку прекрасный удар - после этого ему уже не встать."
        Regex("""Вы нанесли (.+?) (\w+) удар"""),
        // "Ваше меткое попадание тяжело ранило волка."
        Regex("""Ваше (\w+) попадание (\w+) ранило (.+)\.""")
    )

    // Получение урона от моба
    private val damageReceivedPatterns = listOf(
        // "Волк легонько укусил вас." / "Волк слегка укусил вас."
        Regex("""(.+) (\w+) укусил[аи]? вас\."""),
        // "Куница оцарапала вас."
        Regex("""(.+) (\w+) оцарапал[аи]? вас\."""),
        // "Меткое попадание волка тяжело ранило вас."
        Regex("""[Мм]еткое попадание (.+?) (\w+) ранило вас\.""")
    )

    // Промахи (игрока и моба)
    private val missPatterns = listOf(
        // Промах игрока
        Regex("""Вы попытались ударить (.+) - неудачно"""),
        Regex("""Ваша рука не достигла (.+) - нужно было лучше тренироваться"""),
        // Промах моба
        Regex("""(.+) попыталс[яь] .+ вас, но промахнул[ась]+"""),
        Regex("""(.+) попыталс[яь] .+ вас\. Ну ее с такими шутками"""),
        Regex("""(.+) попыталс[яь] укусить вас, но лишь громко клацнул[аи]? зубами"""),
        Regex("""(.+) попыталс[яь] укусить вас, но поймал[аи]? зубами лишь воздух"""),
        Regex("""(.+) попыталс[яь] оцарапать вас, но промахнул[ась]+""")
    )

    // Смерть моба
    private val mobDeathPatterns = listOf(
        // "Волк мертв, его душа медленно подымается в небеса."
        Regex("""(.+) мертв[аы]?, (?:его|ее|их) душа"""),
        // "Волк мертв."
        Regex("""(.+) мертв[аы]?\.?$"""),
        // "Волк без сознания и медленно умирает. Помогите же ему."
        Regex("""(.+) без сознания и медленно умирает"""),
        // "Волк смертельно ранен и умрет, если ему не помогут."
        Regex("""(.+) смертельно ранен[аы]? и умрет""")
    )

    private val expGainPatterns = listOf(
        Regex("""Ваш опыт повысился на (\d+) очк"""),
        Regex("""Вы получили (\d+) очков опыта""")
    )

    private val levelUpPatterns = listOf(
        Regex("""Вы достигли (\d+) уровня"""),
        Regex("""Поздравляем! Вы получили (\d+) уровень""")
    )

    private val fleePatterns = listOf(
        Regex("""Вы запаниковали и попытались убежать"""),
        Regex("""Вы убежали"""),
        Regex("""(.+) убежал[аи]? на (\w+)"""),
        Regex("""(.+) ускакал[аи]? на (\w+)"""),
        Regex("""(.+) уполз[лаи]? на (\w+)""")
    )

    init {
        loadPatternsFromDatabase()
    }

    /**
     * Загрузить выученные паттерны из БД
     */
    private fun loadPatternsFromDatabase() {
        try {
            val patterns = database.getLearnedCombatPatterns()
            for ((message, typeName) in patterns) {
                try {
                    val type = CombatMessageType.valueOf(typeName)
                    knownPatterns[message] = type
                } catch (e: Exception) {
                    logger.warn { "Unknown combat message type: $typeName" }
                }
            }
            logger.info { "Loaded ${knownPatterns.size} learned combat patterns from database" }
        } catch (e: Exception) {
            logger.error { "Error loading combat patterns: ${e.message}" }
        }
    }

    /**
     * Парсит боевое сообщение
     */
    suspend fun parseMessage(message: String): CombatMessage? {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return null

        // 1. Проверяем известные паттерны
        val knownType = mutex.withLock { knownPatterns[trimmed] }
        if (knownType != null) {
            return createCombatMessage(trimmed, knownType)
        }

        // 2. Rule-based парсинг
        val ruleBasedResult = parseWithRules(trimmed)
        if (ruleBasedResult != null) {
            // Сохраняем в кэш
            mutex.withLock {
                knownPatterns[trimmed] = ruleBasedResult.type
            }
            return ruleBasedResult
        }

        // 3. LLM парсинг для неизвестных сообщений
        if (llmParser?.isAvailable() == true) {
            val llmResult = parseWithLLM(trimmed)
            if (llmResult != null && llmResult.confidence > 0.7) {
                // Сохраняем выученный паттерн
                learnPattern(trimmed, llmResult.type, llmResult.confidence)
                return llmResult
            }
        }

        return null
    }

    /**
     * Rule-based парсинг
     */
    private fun parseWithRules(message: String): CombatMessage? {
        // Нанесение урона
        for (pattern in damageDealtPatterns) {
            pattern.find(message)?.let { match ->
                return CombatMessage(
                    type = CombatMessageType.DAMAGE_DEALT,
                    rawText = message,
                    target = extractTarget(match),
                    intensity = extractIntensity(match),
                    confidence = 0.9
                )
            }
        }

        // Получение урона
        for (pattern in damageReceivedPatterns) {
            pattern.find(message)?.let { match ->
                return CombatMessage(
                    type = CombatMessageType.DAMAGE_RECEIVED,
                    rawText = message,
                    source = match.groupValues.getOrNull(1),
                    intensity = extractIntensity(match),
                    confidence = 0.9
                )
            }
        }

        // Промах
        for (pattern in missPatterns) {
            if (pattern.containsMatchIn(message)) {
                return CombatMessage(
                    type = CombatMessageType.MISS,
                    rawText = message,
                    confidence = 0.95
                )
            }
        }

        // Смерть моба
        for (pattern in mobDeathPatterns) {
            pattern.find(message)?.let { match ->
                return CombatMessage(
                    type = CombatMessageType.MOB_DEATH,
                    rawText = message,
                    target = match.groupValues.getOrNull(1)?.trim(),
                    confidence = 0.95
                )
            }
        }

        // Получение опыта
        for (pattern in expGainPatterns) {
            pattern.find(message)?.let { match ->
                return CombatMessage(
                    type = CombatMessageType.EXP_GAIN,
                    rawText = message,
                    amount = match.groupValues.getOrNull(1)?.toIntOrNull(),
                    confidence = 0.99
                )
            }
        }

        // Повышение уровня
        for (pattern in levelUpPatterns) {
            pattern.find(message)?.let { match ->
                return CombatMessage(
                    type = CombatMessageType.LEVEL_UP,
                    rawText = message,
                    amount = match.groupValues.getOrNull(1)?.toIntOrNull(),
                    confidence = 0.99
                )
            }
        }

        // Бегство
        for (pattern in fleePatterns) {
            pattern.find(message)?.let { match ->
                val source = match.groupValues.getOrNull(1)
                val isPlayer = source == null || source.lowercase() == "вы"
                return CombatMessage(
                    type = if (isPlayer) CombatMessageType.PLAYER_FLED else CombatMessageType.MOB_FLED,
                    rawText = message,
                    source = source,
                    confidence = 0.9
                )
            }
        }

        return null
    }

    /**
     * LLM парсинг для неизвестных сообщений
     */
    private fun parseWithLLM(message: String): CombatMessage? {
        if (llmParser == null || !llmParser.isAvailable()) return null

        try {
            val result = llmParser.parseCombatMessage(message)
            val event = result?.event
            if (result != null && event != null) {
                val type = when (event.type.name) {
                    "DAMAGE_DEALT" -> CombatMessageType.DAMAGE_DEALT
                    "DAMAGE_RECEIVED" -> CombatMessageType.DAMAGE_RECEIVED
                    "MOB_KILLED" -> CombatMessageType.MOB_DEATH
                    "PLAYER_DEATH" -> CombatMessageType.PLAYER_DEATH
                    "FLEE_SUCCESS" -> CombatMessageType.PLAYER_FLED
                    "MISS", "DODGE", "PARRY", "BLOCK" -> CombatMessageType.MISS
                    else -> CombatMessageType.UNKNOWN
                }

                if (type != CombatMessageType.UNKNOWN) {
                    return CombatMessage(
                        type = type,
                        rawText = message,
                        source = event.source,
                        target = event.target,
                        amount = event.damage,
                        confidence = result.confidence
                    )
                }
            }
        } catch (e: Exception) {
            logger.error { "LLM parsing error: ${e.message}" }
        }

        return null
    }

    /**
     * Выучить новый паттерн
     */
    suspend fun learnPattern(message: String, type: CombatMessageType, confidence: Double) {
        if (confidence < 0.6) return

        mutex.withLock {
            knownPatterns[message] = type
        }

        try {
            database.saveCombatPattern(message, type.name, confidence)
            logger.info { "Learned new combat pattern: '$message' -> $type (confidence: $confidence)" }
        } catch (e: Exception) {
            logger.error { "Error saving combat pattern: ${e.message}" }
        }
    }

    /**
     * Отметить паттерн как ошибочный
     */
    suspend fun markPatternAsWrong(message: String) {
        mutex.withLock {
            knownPatterns.remove(message)
        }

        try {
            database.removeCombatPattern(message)
            logger.info { "Removed wrong combat pattern: '$message'" }
        } catch (e: Exception) {
            logger.error { "Error removing combat pattern: ${e.message}" }
        }
    }

    /**
     * Обратная связь: подтвердить или опровергнуть классификацию
     */
    suspend fun provideFeedback(message: String, correctType: CombatMessageType?, isCorrect: Boolean) {
        if (isCorrect && correctType != null) {
            learnPattern(message, correctType, 1.0)
        } else {
            markPatternAsWrong(message)
        }
    }

    private fun extractTarget(match: MatchResult): String? {
        return match.groupValues.drop(1).lastOrNull { it.isNotBlank() }?.trim()
    }

    /**
     * Извлечь интенсивность урона из сообщения
     *
     * TODO: Реализовать эмпирическое обучение на основе изменения опыта (поле "o" в промпте).
     * Изменение опыта после удара коррелирует с нанесённым уроном.
     * Например: 40187o -> 40181o = -6 exp после "Вы легонько ударили"
     *
     * Шкала качества ударов (от слабых к сильным, примерная):
     * - легонько (самый слабый)
     * - слегка
     * - хорошо/сильно
     * - очень хорошо
     * - великолепно
     * - меткое попадание
     * - прекрасный удар (добивающий)
     * - [синий/критический] (самый сильный, нужно уточнить)
     */
    private fun extractIntensity(match: MatchResult): DamageIntensity {
        val text = match.value.lowercase()
        return when {
            // Добивающий удар (не обязательно сильный, но финальный)
            text.contains("прекрасн") && text.contains("уже не встать") -> DamageIntensity.HEAVY
            // Сильные удары
            text.contains("меткое попадание") -> DamageIntensity.HEAVY
            text.contains("великолепн") -> DamageIntensity.HEAVY
            text.contains("очень хорошо") -> DamageIntensity.HEAVY
            // Средние удары
            text.contains("хорошо") || text.contains("сильно") -> DamageIntensity.MEDIUM
            text.contains("слегка") -> DamageIntensity.MEDIUM
            // Лёгкие удары
            text.contains("легонько") -> DamageIntensity.LIGHT
            else -> DamageIntensity.MEDIUM
        }
    }

    private fun createCombatMessage(message: String, type: CombatMessageType): CombatMessage {
        return CombatMessage(
            type = type,
            rawText = message,
            confidence = 1.0 // Известный паттерн
        )
    }
}

/**
 * Типы боевых сообщений
 */
enum class CombatMessageType {
    DAMAGE_DEALT,      // Нанесён урон
    DAMAGE_RECEIVED,   // Получен урон
    MISS,              // Промах (любой)
    MOB_DEATH,         // Смерть моба
    PLAYER_DEATH,      // Смерть игрока
    PLAYER_FLED,       // Игрок сбежал
    MOB_FLED,          // Моб сбежал
    EXP_GAIN,          // Получен опыт
    LEVEL_UP,          // Повышение уровня
    SKILL_USED,        // Использован скилл
    AFFECT_APPLIED,    // Наложен эффект
    AFFECT_EXPIRED,    // Эффект истёк
    UNKNOWN            // Неизвестно
}

/**
 * Интенсивность урона
 */
enum class DamageIntensity {
    LIGHT,     // Лёгкий
    MEDIUM,    // Средний
    HEAVY,     // Сильный
    CRITICAL   // Критический
}

/**
 * Результат парсинга боевого сообщения
 */
data class CombatMessage(
    val type: CombatMessageType,
    val rawText: String,
    val source: String? = null,
    val target: String? = null,
    val amount: Int? = null,
    val intensity: DamageIntensity = DamageIntensity.MEDIUM,
    val confidence: Double = 0.0
)
