package com.bylins.client.bot.combat

import com.bylins.client.bot.*
import com.bylins.client.scripting.ScriptEvent
import mu.KotlinLogging

/**
 * Менеджер боя
 * Управляет выбором целей, атаками и боевыми навыками
 */
private val logger = KotlinLogging.logger("CombatManager")

class CombatManager(private val bot: BotCore) {

    // Текущая цель
    var currentTarget: MobInfo? = null
        private set

    // Последний убитый моб (для статистики)
    var lastKilledMob: MobInfo? = null
        private set

    // Флаг боя
    private var _isInCombat = false

    // Время начала боя
    private var combatStartTime: Long = 0

    // Счётчик урона за бой
    private var damageDealt = 0
    private var damageReceived = 0

    // Очередь скиллов
    private val skillQueue = mutableListOf<String>()

    // Кулдауны скиллов (skill -> timestamp когда будет готов)
    private val skillCooldowns = mutableMapOf<String, Long>()

    /**
     * Проверить, в бою ли персонаж
     */
    fun isInCombat(): Boolean = _isInCombat

    /**
     * Выбрать цель из списка мобов
     */
    fun selectTarget(mobs: List<MobInfo>): MobInfo? {
        if (mobs.isEmpty()) return null

        val config = bot.config.value
        val filteredMobs = mobs.filter { mob ->
            // Фильтруем по уровню
            val mobLevel = mob.level ?: 0
            val minLevel = config.minMobLevel ?: 0
            val maxLevel = config.maxMobLevel ?: Int.MAX_VALUE

            val levelOk = mobLevel in minLevel..maxLevel

            // Фильтруем агрессивных если нужно
            val aggroOk = !config.avoidAggressive || mob.isAggressive != true

            levelOk && aggroOk
        }

        if (filteredMobs.isEmpty()) return null

        return when (config.targetPriority) {
            TargetPriority.WEAKEST -> {
                filteredMobs.minByOrNull { it.hpPercent ?: 100 }
            }
            TargetPriority.STRONGEST -> {
                filteredMobs.maxByOrNull { it.hpPercent ?: 0 }
            }
            TargetPriority.LOWEST_LEVEL -> {
                filteredMobs.minByOrNull { it.level ?: Int.MAX_VALUE }
            }
            TargetPriority.HIGHEST_LEVEL -> {
                filteredMobs.maxByOrNull { it.level ?: 0 }
            }
            TargetPriority.MOST_EXP -> {
                // Ищем в базе данных бота информацию об опыте
                filteredMobs.maxByOrNull { mob ->
                    bot.database.getMob(mob.name)?.expReward ?: 0
                }
            }
            TargetPriority.NEAREST -> {
                filteredMobs.firstOrNull()
            }
            TargetPriority.RANDOM -> {
                filteredMobs.randomOrNull()
            }
        }
    }

    /**
     * Начать бой с мобом
     */
    fun startCombat(target: MobInfo) {
        currentTarget = target
        _isInCombat = true
        combatStartTime = System.currentTimeMillis()
        damageDealt = 0
        damageReceived = 0

        bot.log("Starting combat with: ${target.name}")

        // Формируем команду атаки
        val attackCommand = buildAttackCommand(target)
        bot.send(attackCommand)

        // Логируем в БД
        bot.database.saveCombatEvent(CombatLogEntry(
            sessionId = null, // TODO: передать sessionId
            timestamp = System.currentTimeMillis(),
            roomId = bot.characterState.value?.roomId,
            mobId = target.name, // TODO: нормальный ID
            mobName = target.name,
            eventType = CombatEventType.COMBAT_START
        ))
    }

    /**
     * Тик боя
     */
    fun tick() {
        if (!_isInCombat || currentTarget == null) return

        // Используем навыки из очереди
        processSkillQueue()

        // TODO: Проверяем состояние цели и принимаем решения
    }

    /**
     * Обработать нанесённый урон
     */
    fun onDamageDealt(damage: Int, target: String?, skill: String? = null) {
        damageDealt += damage

        // Логируем в БД
        bot.database.saveCombatEvent(CombatLogEntry(
            sessionId = null,
            timestamp = System.currentTimeMillis(),
            roomId = bot.characterState.value?.roomId,
            mobId = target,
            mobName = target,
            eventType = CombatEventType.DAMAGE_DEALT,
            damageDealt = damage,
            skillUsed = skill
        ))
    }

    /**
     * Обработать полученный урон
     */
    fun onDamageReceived(damage: Int, source: String?) {
        damageReceived += damage

        // Логируем в БД
        bot.database.saveCombatEvent(CombatLogEntry(
            sessionId = null,
            timestamp = System.currentTimeMillis(),
            roomId = bot.characterState.value?.roomId,
            mobId = source,
            mobName = source,
            eventType = CombatEventType.DAMAGE_RECEIVED,
            damageReceived = damage
        ))
    }

    /**
     * Обработать убийство моба
     */
    fun onMobKilled(mobName: String) {
        val duration = System.currentTimeMillis() - combatStartTime

        lastKilledMob = currentTarget
        currentTarget = null
        _isInCombat = false

        bot.log("Mob killed: $mobName (duration: ${duration}ms, dealt: $damageDealt, received: $damageReceived)")

        // Обновляем статистику в БД
        val mobId = mobName // TODO: нормальный ID
        bot.database.incrementMobKillCount(mobId)

        // Логируем завершение боя
        bot.database.saveCombatEvent(CombatLogEntry(
            sessionId = null,
            timestamp = System.currentTimeMillis(),
            roomId = bot.characterState.value?.roomId,
            mobId = mobId,
            mobName = mobName,
            eventType = CombatEventType.MOB_KILLED,
            damageDealt = damageDealt,
            damageReceived = damageReceived,
            outcome = "victory"
        ))
    }

    /**
     * Обработать окончание боя (бегство, смерть и т.д.)
     */
    fun onCombatEnd(outcome: String) {
        val duration = System.currentTimeMillis() - combatStartTime

        bot.log("Combat ended: $outcome (duration: ${duration}ms)")

        // Логируем в БД
        bot.database.saveCombatEvent(CombatLogEntry(
            sessionId = null,
            timestamp = System.currentTimeMillis(),
            roomId = bot.characterState.value?.roomId,
            mobId = currentTarget?.name,
            mobName = currentTarget?.name,
            eventType = CombatEventType.COMBAT_END,
            damageDealt = damageDealt,
            damageReceived = damageReceived,
            outcome = outcome
        ))

        currentTarget = null
        _isInCombat = false
        damageDealt = 0
        damageReceived = 0
    }

    /**
     * Добавить навык в очередь
     */
    fun queueSkill(skill: String) {
        skillQueue.add(skill)
    }

    /**
     * Использовать навык (если готов)
     */
    fun useSkill(skill: String): Boolean {
        val now = System.currentTimeMillis()
        val cooldownEnd = skillCooldowns[skill] ?: 0

        if (now < cooldownEnd) {
            return false // Ещё на кулдауне
        }

        bot.send(skill)
        // TODO: Установить кулдаун из данных о навыке
        return true
    }

    /**
     * Обработать очередь скиллов
     */
    private fun processSkillQueue() {
        if (skillQueue.isEmpty()) return

        val skill = skillQueue.firstOrNull() ?: return
        if (useSkill(skill)) {
            skillQueue.removeFirst()
        }
    }

    /**
     * Построить команду атаки
     */
    private fun buildAttackCommand(target: MobInfo): String {
        // Если есть короткое имя - используем его
        val targetName = target.shortName ?: target.name.split(" ").lastOrNull() ?: target.name

        // Если в комнате несколько таких мобов - добавляем индекс
        val index = if (target.roomIndex > 1) "${target.roomIndex}." else ""

        return "убить $index$targetName"
    }

    /**
     * Получить статистику боя
     */
    fun getCombatStats(): Map<String, Any> {
        return mapOf(
            "isInCombat" to _isInCombat,
            "target" to (currentTarget?.name ?: ""),
            "combatDuration" to if (_isInCombat) System.currentTimeMillis() - combatStartTime else 0,
            "damageDealt" to damageDealt,
            "damageReceived" to damageReceived,
            "skillQueueSize" to skillQueue.size
        )
    }
}
