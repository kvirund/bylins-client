package com.bylins.client.bot.combat

import com.bylins.client.bot.*
import com.bylins.client.bot.perception.CombatEndReason
import com.bylins.client.bot.perception.DamageIntensity
import com.bylins.client.scripting.ScriptEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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

    // ============================================
    // Combat Profile tracking
    // ============================================

    private val objectMapper = ObjectMapper().registerKotlinModule()

    // Current combat profile being recorded
    private var currentProfileId: Long? = null
    private var profileMobsKilled = mutableListOf<String>()
    private var profileExpGained: Long = 0
    private var profileGoldGained: Long = 0
    private var profileHpBefore: Int? = null
    private var profileMoveBefore: Int? = null
    private var profileZoneId: String? = null
    private var profileRoomId: String? = null

    // Raw data for detailed combat analysis
    private var profileHits = mutableListOf<Map<String, Any>>()
    private var profileHpTimeline = mutableListOf<Map<String, Any>>()
    private var profileExpTimeline = mutableListOf<Map<String, Any>>()
    private var profileSkillsUsed = mutableSetOf<String>()

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

        // Record hit in combat profile
        recordHit("dealt", damage, skill, target = target)

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

        // Record hit in combat profile
        recordHit("received", damage, null, source = source)

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

        // Add mob to combat profile
        profileMobsKilled.add(mobName)

        // Note: combat doesn't end here - player might switch to another target
        // Combat ends when onCombatEnded is called (from PromptParser detecting no combat)
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

        // Finalize combat profile
        finalizeCombatProfile(outcome, duration)

        currentTarget = null
        _isInCombat = false
        damageDealt = 0
        damageReceived = 0
    }

    /**
     * Finalize and save the combat profile
     */
    private fun finalizeCombatProfile(outcome: String, duration: Long) {
        val profileId = currentProfileId ?: return

        val charState = bot.characterState.value

        // Record final HP point
        charState?.let {
            profileHpTimeline.add(mapOf(
                "t" to System.currentTimeMillis(),
                "hp" to it.hp,
                "max" to it.maxHp
            ))
        }

        // Build raw data JSON
        val rawData = try {
            objectMapper.writeValueAsString(mapOf(
                "hits" to profileHits,
                "hp_timeline" to profileHpTimeline,
                "exp_timeline" to profileExpTimeline,
                "skills_used" to profileSkillsUsed.toList(),
                "damage_dealt" to damageDealt,
                "damage_received" to damageReceived
            ))
        } catch (e: Exception) {
            logger.error { "Error serializing combat raw data: ${e.message}" }
            "{}"
        }

        // Build mobs killed JSON
        val mobsKilledJson = try {
            objectMapper.writeValueAsString(profileMobsKilled)
        } catch (e: Exception) {
            "[]"
        }

        // Determine result based on outcome
        val result = when {
            outcome.contains("VICTORY", ignoreCase = true) || profileMobsKilled.isNotEmpty() -> "win"
            outcome.contains("FLEE", ignoreCase = true) || outcome.contains("ESCAPED", ignoreCase = true) -> "flee"
            outcome.contains("DEATH", ignoreCase = true) -> "death"
            else -> outcome.lowercase()
        }

        // Update profile in database
        val profile = CombatProfile(
            id = profileId,
            startedAt = combatStartTime / 1000,
            endedAt = System.currentTimeMillis() / 1000,
            zoneId = profileZoneId,
            roomId = profileRoomId,
            killsCount = profileMobsKilled.size,
            mobsKilled = mobsKilledJson,
            hpBefore = profileHpBefore,
            hpAfter = charState?.hp,
            moveBefore = profileMoveBefore,
            moveAfter = charState?.move,
            expGained = profileExpGained,
            goldGained = profileGoldGained,
            result = result,
            durationMs = duration,
            rawData = rawData
        )

        bot.database.updateCombatProfile(profile)

        logger.info { "Finalized combat profile #$profileId: ${profileMobsKilled.size} kills, ${profileExpGained} exp, $result" }

        // Reset profile tracking
        currentProfileId = null
    }

    /**
     * Record experience gain during combat
     */
    fun onExpGain(amount: Int) {
        profileExpGained += amount

        profileExpTimeline.add(mapOf(
            "t" to System.currentTimeMillis(),
            "exp" to (bot.characterState.value?.experience ?: 0),
            "delta" to amount
        ))

        logger.debug { "Combat exp gain: $amount (total: $profileExpGained)" }
    }

    /**
     * Record gold gain during combat
     */
    fun onGoldGain(amount: Int) {
        profileGoldGained += amount
        logger.debug { "Combat gold gain: $amount (total: $profileGoldGained)" }
    }

    /**
     * Record a hit in combat profile
     */
    fun recordHit(type: String, damage: Int?, skill: String?, source: String? = null, target: String? = null) {
        val hit = mutableMapOf<String, Any>(
            "t" to System.currentTimeMillis(),
            "type" to type
        )
        damage?.let { hit["dmg"] = it }
        skill?.let {
            hit["skill"] = it
            profileSkillsUsed.add(it)
        }
        source?.let { hit["source"] = it }
        target?.let { hit["target"] = it }

        profileHits.add(hit)
    }

    /**
     * Record HP change in timeline
     */
    fun recordHpChange(hp: Int, maxHp: Int) {
        profileHpTimeline.add(mapOf(
            "t" to System.currentTimeMillis(),
            "hp" to hp,
            "max" to maxHp
        ))
    }

    // ============================================
    // Методы для интеграции с PromptParser
    // ============================================

    /**
     * Обработать начало боя (из PromptParser)
     */
    fun onCombatStarted(targetName: String, targetCondition: String?) {
        _isInCombat = true
        combatStartTime = System.currentTimeMillis()
        damageDealt = 0
        damageReceived = 0

        currentTarget = MobInfo(
            name = targetName,
            hpPercent = conditionToHpPercent(targetCondition)
        )

        bot.log("Combat started with: $targetName ($targetCondition)")

        // Логируем в БД
        bot.database.saveCombatEvent(CombatLogEntry(
            sessionId = null,
            timestamp = System.currentTimeMillis(),
            roomId = bot.characterState.value?.roomId,
            mobId = targetName,
            mobName = targetName,
            eventType = CombatEventType.COMBAT_START
        ))

        // Start combat profile recording
        startCombatProfile()
    }

    /**
     * Start recording a new combat profile
     */
    private fun startCombatProfile() {
        val charState = bot.characterState.value

        // Initialize profile data
        profileMobsKilled.clear()
        profileExpGained = 0
        profileGoldGained = 0
        profileHpBefore = charState?.hp
        profileMoveBefore = charState?.move
        profileZoneId = charState?.zoneId
        profileRoomId = charState?.roomId
        profileHits.clear()
        profileHpTimeline.clear()
        profileExpTimeline.clear()
        profileSkillsUsed.clear()

        // Record initial HP point
        charState?.let {
            profileHpTimeline.add(mapOf(
                "t" to System.currentTimeMillis(),
                "hp" to it.hp,
                "max" to it.maxHp
            ))
        }

        // Create profile in database
        val profile = CombatProfile(
            startedAt = combatStartTime / 1000, // Convert to seconds
            zoneId = profileZoneId,
            roomId = profileRoomId,
            hpBefore = profileHpBefore,
            moveBefore = profileMoveBefore
        )
        currentProfileId = bot.database.createCombatProfile(profile)

        logger.debug { "Started combat profile #$currentProfileId" }
    }

    /**
     * Обработать окончание боя (из PromptParser)
     */
    fun onCombatEnded(reason: CombatEndReason) {
        val outcome = reason.name
        onCombatEnd(outcome)
    }

    /**
     * Обработать смену цели в бою
     */
    fun onTargetChanged(newTarget: String, newCondition: String?) {
        currentTarget = MobInfo(
            name = newTarget,
            hpPercent = conditionToHpPercent(newCondition)
        )
    }

    /**
     * Обработать изменение состояния цели
     */
    @Suppress("UNUSED_PARAMETER")
    fun onTargetConditionChanged(_targetName: String, newCondition: String) {
        currentTarget = currentTarget?.copy(
            hpPercent = conditionToHpPercent(newCondition)
        )
    }

    /**
     * Обработать нанесённый урон (с интенсивностью)
     */
    fun onDamageDealt(target: String?, intensity: DamageIntensity) {
        val estimatedDamage = intensityToDamage(intensity)
        damageDealt += estimatedDamage

        // Record hit in combat profile
        recordHit("dealt", estimatedDamage, null, target = target)
    }

    /**
     * Обработать полученный урон (с интенсивностью)
     */
    fun onDamageReceived(source: String?, intensity: DamageIntensity) {
        val estimatedDamage = intensityToDamage(intensity)
        damageReceived += estimatedDamage

        // Record hit in combat profile
        recordHit("received", estimatedDamage, null, source = source)
    }

    /**
     * Преобразовать состояние в процент HP
     */
    private fun conditionToHpPercent(condition: String?): Int? {
        if (condition == null) return null
        return when {
            condition.contains("Невредим", ignoreCase = true) -> 100
            condition.contains("Слегка ранен", ignoreCase = true) -> 90
            condition.contains("Легко ранен", ignoreCase = true) -> 75
            condition.contains("Ранен", ignoreCase = true) && !condition.contains("тяжело", ignoreCase = true) -> 55
            condition.contains("Тяжело ранен", ignoreCase = true) -> 35
            condition.contains("О.тяжело ранен", ignoreCase = true) -> 20
            condition.contains("Смертельно ранен", ignoreCase = true) -> 5
            else -> null
        }
    }

    /**
     * Преобразовать интенсивность в примерный урон
     */
    private fun intensityToDamage(intensity: DamageIntensity): Int {
        return when (intensity) {
            DamageIntensity.LIGHT -> 5
            DamageIntensity.MEDIUM -> 15
            DamageIntensity.HEAVY -> 30
            DamageIntensity.CRITICAL -> 50
        }
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
