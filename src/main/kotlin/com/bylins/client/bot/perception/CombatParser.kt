package com.bylins.client.bot.perception

import com.bylins.client.bot.*
import com.bylins.client.scripting.ScriptEvent
import mu.KotlinLogging

/**
 * Парсер боевых сообщений
 * Извлекает информацию о бое из текста
 */
private val logger = KotlinLogging.logger("CombatParser")

class CombatParser(private val bot: BotCore) {

    // Кэш последних строк для multi-line парсинга
    private val recentLines = mutableListOf<String>()
    private val maxRecentLines = 10

    // Паттерны для парсинга урона
    private val damagePatterns = listOf(
        // Вы нанесли урон
        DamagePattern(
            pattern = Regex("Вы (легко |слегка |сильно |очень сильно )?(?:ранили|задели|ударили|поразили) (.+?)( своим ударом)?[!.]?"),
            type = DamageType.DEALT,
            getDamage = { estimateDamage(it.groupValues[1]) }
        ),
        DamagePattern(
            pattern = Regex("Ваш удар (легко |слегка |сильно |очень сильно )?(?:ранил|задел|ударил|поразил) (.+?)[!.]?"),
            type = DamageType.DEALT,
            getDamage = { estimateDamage(it.groupValues[1]) }
        ),
        DamagePattern(
            pattern = Regex("Вы нанесли (.+?) (\\d+) повреждени[йя]"),
            type = DamageType.DEALT,
            getDamage = { it.groupValues[2].toIntOrNull() ?: 0 }
        ),

        // Вам нанесли урон
        DamagePattern(
            pattern = Regex("(.+?) (легко |слегка |сильно |очень сильно )?(?:ранил|задел|ударил|поразил) вас"),
            type = DamageType.RECEIVED,
            getDamage = { estimateDamage(it.groupValues[2]) }
        ),
        DamagePattern(
            pattern = Regex("(.+?) нан[её]с(?:ла)? вам (\\d+) повреждени[йя]"),
            type = DamageType.RECEIVED,
            getDamage = { it.groupValues[2].toIntOrNull() ?: 0 }
        ),

        // Критический удар
        DamagePattern(
            pattern = Regex("КРИТ(?:ИЧЕСКИЙ)?(?:УДАР)?[!:] (.+)"),
            type = DamageType.CRITICAL,
            getDamage = { 0 }
        ),

        // Промах
        DamagePattern(
            pattern = Regex("Вы промахнулись"),
            type = DamageType.MISS,
            getDamage = { 0 }
        ),
        DamagePattern(
            pattern = Regex("(.+?) промахнул(?:ся|ась)"),
            type = DamageType.ENEMY_MISS,
            getDamage = { 0 }
        ),

        // Уклонение
        DamagePattern(
            pattern = Regex("Вы уклонились от атаки"),
            type = DamageType.DODGED,
            getDamage = { 0 }
        ),
        DamagePattern(
            pattern = Regex("(.+?) уклонил(?:ся|ась) от вашей атаки"),
            type = DamageType.ENEMY_DODGED,
            getDamage = { 0 }
        ),

        // Парирование
        DamagePattern(
            pattern = Regex("Вы парировали атаку"),
            type = DamageType.PARRIED,
            getDamage = { 0 }
        ),

        // Блокирование
        DamagePattern(
            pattern = Regex("Вы заблокировали удар"),
            type = DamageType.BLOCKED,
            getDamage = { 0 }
        )
    )

    // Паттерны боевых событий
    private val combatEventPatterns = listOf(
        // Начало боя
        EventPattern(
            pattern = Regex("Вы напали на (.+)"),
            eventType = CombatEventType.COMBAT_START
        ),
        EventPattern(
            pattern = Regex("(.+?) напал(?:а)? на вас"),
            eventType = CombatEventType.COMBAT_START
        ),
        EventPattern(
            pattern = Regex("Вы (?:начали сражаться|вступили в бой) с (.+)"),
            eventType = CombatEventType.COMBAT_START
        ),

        // Убийство
        EventPattern(
            pattern = Regex("(.+?) пал(?:а)? замертво"),
            eventType = CombatEventType.MOB_KILLED
        ),
        EventPattern(
            pattern = Regex("Вы убили (.+)"),
            eventType = CombatEventType.MOB_KILLED
        ),
        EventPattern(
            pattern = Regex("(.+?) погиб(?:ла)?"),
            eventType = CombatEventType.MOB_KILLED
        ),

        // Смерть игрока
        EventPattern(
            pattern = Regex("Вы погибли"),
            eventType = CombatEventType.PLAYER_DEATH
        ),
        EventPattern(
            pattern = Regex("Вы пали замертво"),
            eventType = CombatEventType.PLAYER_DEATH
        ),

        // Бегство
        EventPattern(
            pattern = Regex("Вы в панике убежали"),
            eventType = CombatEventType.FLEE_SUCCESS
        ),
        EventPattern(
            pattern = Regex("Вы не смогли убежать"),
            eventType = CombatEventType.FLEE_FAILED
        ),
        EventPattern(
            pattern = Regex("ПАНИКА! Вы не можете убежать!"),
            eventType = CombatEventType.FLEE_FAILED
        ),

        // Использование скилла
        EventPattern(
            pattern = Regex("Вы прочитали заклинание '(.+?)'"),
            eventType = CombatEventType.SKILL_USED
        ),
        EventPattern(
            pattern = Regex("Вы использовали умение '(.+?)'"),
            eventType = CombatEventType.SKILL_USED
        )
    )

    /**
     * Обработать строку
     */
    fun parseLine(line: String): CombatEvent? {
        // Добавляем в кэш
        recentLines.add(line)
        if (recentLines.size > maxRecentLines) {
            recentLines.removeAt(0)
        }

        // Проверяем паттерны урона
        for (dp in damagePatterns) {
            val match = dp.pattern.find(line)
            if (match != null) {
                val damage = dp.getDamage(match)
                val event = createDamageEvent(dp.type, damage, match, line)
                handleDamageEvent(event)
                return event
            }
        }

        // Проверяем паттерны событий
        for (ep in combatEventPatterns) {
            val match = ep.pattern.find(line)
            if (match != null) {
                val event = createCombatEvent(ep.eventType, match, line)
                handleCombatEvent(event)
                return event
            }
        }

        return null
    }

    /**
     * Создать событие урона
     */
    private fun createDamageEvent(type: DamageType, damage: Int, match: MatchResult, rawLine: String): CombatEvent {
        val (source, target) = when (type) {
            DamageType.DEALT, DamageType.CRITICAL -> "player" to extractTarget(match)
            DamageType.RECEIVED -> extractSource(match) to "player"
            DamageType.MISS, DamageType.DODGED, DamageType.PARRIED, DamageType.BLOCKED -> "player" to null
            DamageType.ENEMY_MISS, DamageType.ENEMY_DODGED -> extractSource(match) to "player"
        }

        val eventType = when (type) {
            DamageType.DEALT, DamageType.CRITICAL -> CombatEventType.DAMAGE_DEALT
            DamageType.RECEIVED -> CombatEventType.DAMAGE_RECEIVED
            else -> CombatEventType.DAMAGE_DEALT
        }

        return CombatEvent(
            type = eventType,
            source = source,
            target = target,
            damage = damage,
            isCritical = type == DamageType.CRITICAL,
            isMiss = type in listOf(DamageType.MISS, DamageType.ENEMY_MISS),
            isDodged = type in listOf(DamageType.DODGED, DamageType.ENEMY_DODGED),
            isParried = type == DamageType.PARRIED,
            isBlocked = type == DamageType.BLOCKED,
            message = rawLine
        )
    }

    /**
     * Создать боевое событие
     */
    private fun createCombatEvent(eventType: CombatEventType, match: MatchResult, rawLine: String): CombatEvent {
        val target = if (match.groupValues.size > 1) match.groupValues[1] else null

        return CombatEvent(
            type = eventType,
            target = target,
            message = rawLine
        )
    }

    /**
     * Обработать событие урона
     */
    private fun handleDamageEvent(event: CombatEvent) {
        when (event.type) {
            CombatEventType.DAMAGE_DEALT -> {
                bot.combatManager.onDamageDealt(event.damage ?: 0, event.target, event.skill)
                // Отправляем событие в скрипты
                // bot.fireEvent(ScriptEvent.ON_DAMAGE_DEALT, event)
            }
            CombatEventType.DAMAGE_RECEIVED -> {
                bot.combatManager.onDamageReceived(event.damage ?: 0, event.source)
                // Отправляем событие в скрипты
                // bot.fireEvent(ScriptEvent.ON_DAMAGE_RECEIVED, event)
            }
            else -> {}
        }
    }

    /**
     * Обработать боевое событие
     */
    private fun handleCombatEvent(event: CombatEvent) {
        when (event.type) {
            CombatEventType.COMBAT_START -> {
                logger.info { "Combat started with: ${event.target}" }
                // Если бот в режиме путешествия, можно переключить в бой
            }
            CombatEventType.MOB_KILLED -> {
                logger.info { "Mob killed: ${event.target}" }
                bot.combatManager.onMobKilled(event.target ?: "unknown")
            }
            CombatEventType.PLAYER_DEATH -> {
                logger.info { "Player died!" }
                // Бот обработает это через проверку HP
            }
            CombatEventType.FLEE_SUCCESS -> {
                logger.info { "Flee successful" }
                bot.combatManager.onCombatEnd("flee")
            }
            CombatEventType.FLEE_FAILED -> {
                logger.info { "Flee failed!" }
            }
            CombatEventType.SKILL_USED -> {
                logger.debug { "Skill used: ${event.target}" }
            }
            else -> {}
        }
    }

    /**
     * Извлечь имя цели из match result
     */
    private fun extractTarget(match: MatchResult): String? {
        // Обычно цель во второй группе
        return match.groupValues.getOrNull(2)?.trim()
            ?: match.groupValues.getOrNull(1)?.trim()
    }

    /**
     * Извлечь имя источника из match result
     */
    private fun extractSource(match: MatchResult): String? {
        return match.groupValues.getOrNull(1)?.trim()
    }

    /**
     * Оценить урон по описанию
     */
    private fun estimateDamage(descriptor: String): Int {
        return when (descriptor.trim().lowercase()) {
            "легко", "слегка" -> 5
            "" -> 15
            "сильно" -> 30
            "очень сильно" -> 50
            else -> 15
        }
    }

    /**
     * Получить лог боевых событий для API
     */
    fun getCombatLogAsMap(limit: Int): List<Map<String, Any>> {
        // TODO: Хранить историю событий
        return emptyList()
    }

    // Вспомогательные классы
    private data class DamagePattern(
        val pattern: Regex,
        val type: DamageType,
        val getDamage: (MatchResult) -> Int
    )

    private enum class DamageType {
        DEALT, RECEIVED, CRITICAL, MISS, ENEMY_MISS, DODGED, ENEMY_DODGED, PARRIED, BLOCKED
    }

    private data class EventPattern(
        val pattern: Regex,
        val eventType: CombatEventType
    )
}
