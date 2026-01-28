package com.bylins.client.bot.perception

import com.bylins.client.bot.Position
import mu.KotlinLogging

/**
 * Парсер промпта Былин для определения состояния персонажа и боя
 *
 * Форматы промпта:
 * - Обычный: "248H 104M 40980o Зауч:0 ОЗ:0 8L 1136G Вых:СЮЗ>"
 * - В бою:   "248H 104M 41096o Зауч:0 ОЗ:0 [Ходэр:Невредим] [куница:Смертельно ранена] >"
 */
private val logger = KotlinLogging.logger("PromptParser")

/**
 * Данные из промпта Былин
 * Формат: 248H 104M 40980o - H=HP, M=Move, o=exp до уровня
 * (Маны в промпте нет!)
 */
data class PromptData(
    val hp: Int? = null,
    val maxHp: Int? = null,
    val move: Int? = null,  // M в промпте - это Move, не Mana!
    val maxMove: Int? = null,
    val mana: Int? = null,  // Маны в промпте нет, только из других источников
    val maxMana: Int? = null,
    val expToLevel: Int? = null,
    val level: Int? = null,
    val gold: Int? = null,
    val exits: List<String> = emptyList(),
    val inCombat: Boolean = false,
    val playerName: String? = null,
    val playerCondition: String? = null,
    val targetName: String? = null,
    val targetCondition: String? = null,
    val position: Position? = null,
    val rawPrompt: String = ""
)

/**
 * Состояния персонажа/моба в бою
 */
enum class CombatCondition(val russian: String, val hpPercent: IntRange) {
    UNHARMED("Невредим", 100..100),
    SCRATCHED("Царапины", 90..99),
    SLIGHTLY_WOUNDED("Слегка ранен", 75..89),
    LIGHTLY_WOUNDED("Легко ранен", 60..74),
    WOUNDED("Ранен", 45..59),
    HEAVILY_WOUNDED("Тяжело ранен", 30..44),
    VERY_HEAVILY_WOUNDED("О.тяжело ранен", 15..29),
    MORTALLY_WOUNDED("Смертельно ранен", 1..14),
    UNCONSCIOUS("Без сознания", 0..0);

    companion object {
        fun fromRussian(text: String): CombatCondition? {
            val normalized = text.trim()
            return values().find {
                normalized.equals(it.russian, ignoreCase = true) ||
                normalized.startsWith(it.russian.take(6), ignoreCase = true)
            }
        }

        fun estimateHpPercent(condition: CombatCondition): Int {
            return condition.hpPercent.let { (it.first + it.last) / 2 }
        }
    }
}

class PromptParser {
    // Callback для уведомления об изменении опыта (для эмпирического обучения урона)
    var onExpChange: ((expDelta: Int, previousPrompt: PromptData?, currentPrompt: PromptData) -> Unit)? = null

    // Регулярка для обычного промпта
    // 248H 104M 40980o Зауч:0 ОЗ:0 8L 1136G Вых:СЮЗ>
    private val normalPromptRegex = Regex(
        """(\d+)H\s+(\d+)M\s+(\d+)o\s+.*?(\d+)L\s+(\d+)G\s+Вых:([СВЮЗ]+)>"""
    )

    // Регулярка для промпта в бою
    // 248H 104M 41096o Зауч:0 ОЗ:0 [Ходэр:Невредим] [куница:Смертельно ранена] >
    private val combatPromptRegex = Regex(
        """(\d+)H\s+(\d+)M\s+(\d+)o\s+.*?\[([^:]+):([^\]]+)\]\s*\[([^:]+):([^\]]+)\]\s*>"""
    )

    // Упрощённая регулярка для базовых значений
    private val basicStatsRegex = Regex("""(\d+)H\s+(\d+)M\s+(\d+)o""")

    // Регулярка для боевых скобок
    private val combatBracketsRegex = Regex("""\[([^:]+):([^\]]+)\]""")

    // Последнее состояние для отслеживания изменений
    var lastPromptData: PromptData? = null
        private set
    private var wasInCombat = false

    /**
     * Парсит строку промпта
     */
    fun parsePrompt(line: String): PromptData? {
        // Проверяем, похоже ли на промпт
        if (!line.contains("H ") || !line.contains("M ") || !line.endsWith(">")) {
            return null
        }

        val prompt = line.trim()

        // Сначала пробуем боевой промпт
        combatPromptRegex.find(prompt)?.let { match ->
            val (hp, move, exp, playerName, playerCondition, targetName, targetCondition) = match.destructured
            return PromptData(
                hp = hp.toIntOrNull(),
                move = move.toIntOrNull(),  // M = Move!
                expToLevel = exp.toIntOrNull(),
                inCombat = true,
                playerName = playerName.trim(),
                playerCondition = playerCondition.trim(),
                targetName = targetName.trim(),
                targetCondition = targetCondition.trim(),
                rawPrompt = prompt
            )
        }

        // Пробуем обычный промпт
        normalPromptRegex.find(prompt)?.let { match ->
            val (hp, move, exp, level, gold, exits) = match.destructured
            return PromptData(
                hp = hp.toIntOrNull(),
                move = move.toIntOrNull(),  // M = Move!
                expToLevel = exp.toIntOrNull(),
                level = level.toIntOrNull(),
                gold = gold.toIntOrNull(),
                exits = parseExits(exits),
                inCombat = false,
                rawPrompt = prompt
            )
        }

        // Fallback: парсим хотя бы базовые статы
        basicStatsRegex.find(prompt)?.let { match ->
            val (hp, move, exp) = match.destructured

            // Проверяем наличие боевых скобок
            val brackets = combatBracketsRegex.findAll(prompt).toList()
            val inCombat = brackets.size >= 2

            var playerName: String? = null
            var playerCondition: String? = null
            var targetName: String? = null
            var targetCondition: String? = null

            if (brackets.size >= 2) {
                playerName = brackets[0].groupValues[1].trim()
                playerCondition = brackets[0].groupValues[2].trim()
                targetName = brackets[1].groupValues[1].trim()
                targetCondition = brackets[1].groupValues[2].trim()
            }

            return PromptData(
                hp = hp.toIntOrNull(),
                move = move.toIntOrNull(),  // M = Move!
                expToLevel = exp.toIntOrNull(),
                inCombat = inCombat,
                playerName = playerName,
                playerCondition = playerCondition,
                targetName = targetName,
                targetCondition = targetCondition,
                rawPrompt = prompt
            )
        }

        return null
    }

    /**
     * Обрабатывает промпт и возвращает события
     */
    fun processPrompt(line: String): List<CombatStateChange> {
        val promptData = parsePrompt(line) ?: return emptyList()
        val events = mutableListOf<CombatStateChange>()

        // Определяем изменения состояния
        val previousData = lastPromptData

        // Отслеживаем изменение опыта (для эмпирического обучения урона)
        // Поле "o" - это "опыт до уровня", уменьшается при получении опыта
        if (previousData != null && promptData.expToLevel != null && previousData.expToLevel != null) {
            val expDelta = previousData.expToLevel - promptData.expToLevel
            if (expDelta > 0) {
                // Получен опыт (expToLevel уменьшился)
                onExpChange?.invoke(expDelta, previousData, promptData)
            }
        }

        // Вход в бой
        if (promptData.inCombat && !wasInCombat) {
            events.add(CombatStateChange.CombatStarted(
                targetName = promptData.targetName ?: "unknown",
                targetCondition = promptData.targetCondition
            ))
            logger.info { "Combat started with: ${promptData.targetName}" }
        }

        // Выход из боя
        if (!promptData.inCombat && wasInCombat) {
            events.add(CombatStateChange.CombatEnded(
                reason = CombatEndReason.UNKNOWN // Будет уточнено по сообщениям
            ))
            logger.info { "Combat ended" }
        }

        // Смена цели в бою
        if (promptData.inCombat && wasInCombat &&
            promptData.targetName != previousData?.targetName) {
            events.add(CombatStateChange.TargetChanged(
                newTarget = promptData.targetName ?: "unknown",
                newCondition = promptData.targetCondition
            ))
            logger.info { "Target changed to: ${promptData.targetName}" }
        }

        // Изменение состояния цели
        if (promptData.inCombat && promptData.targetCondition != previousData?.targetCondition) {
            events.add(CombatStateChange.TargetConditionChanged(
                targetName = promptData.targetName ?: "unknown",
                oldCondition = previousData?.targetCondition,
                newCondition = promptData.targetCondition ?: "unknown"
            ))
        }

        // Изменение состояния игрока
        if (promptData.inCombat && promptData.playerCondition != previousData?.playerCondition) {
            events.add(CombatStateChange.PlayerConditionChanged(
                oldCondition = previousData?.playerCondition,
                newCondition = promptData.playerCondition ?: "unknown"
            ))
        }

        // Обновляем состояние
        lastPromptData = promptData
        wasInCombat = promptData.inCombat

        return events
    }

    /**
     * Парсит выходы из промпта (СЮЗ -> [С, Ю, З])
     */
    private fun parseExits(exits: String): List<String> {
        return exits.map { char ->
            when (char) {
                'С' -> "север"
                'Ю' -> "юг"
                'З' -> "запад"
                'В' -> "восток"
                else -> char.toString()
            }
        }
    }

    /**
     * Сброс состояния
     */
    fun reset() {
        lastPromptData = null
        wasInCombat = false
    }

    /**
     * Проверяет, находится ли персонаж в бою
     */
    fun isInCombat(): Boolean = wasInCombat
}

/**
 * События изменения состояния боя
 */
sealed class CombatStateChange {
    data class CombatStarted(
        val targetName: String,
        val targetCondition: String?
    ) : CombatStateChange()

    data class CombatEnded(
        val reason: CombatEndReason
    ) : CombatStateChange()

    data class TargetChanged(
        val newTarget: String,
        val newCondition: String?
    ) : CombatStateChange()

    data class TargetConditionChanged(
        val targetName: String,
        val oldCondition: String?,
        val newCondition: String
    ) : CombatStateChange()

    data class PlayerConditionChanged(
        val oldCondition: String?,
        val newCondition: String
    ) : CombatStateChange()
}

enum class CombatEndReason {
    MOB_KILLED,      // Моб убит
    PLAYER_FLED,     // Игрок сбежал
    PLAYER_DIED,     // Игрок умер
    MOB_FLED,        // Моб сбежал
    UNKNOWN          // Неизвестно
}
