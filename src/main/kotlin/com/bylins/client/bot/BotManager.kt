package com.bylins.client.bot

import com.bylins.client.bot.llm.LLMParser
import com.bylins.client.scripting.BotActions
import com.bylins.client.scripting.ScriptEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging

/**
 * Менеджер бота - точка интеграции с клиентом
 * Реализует BotActions для ScriptAPI и управляет BotCore
 */
private val logger = KotlinLogging.logger("BotManager")

class BotManager(
    private val sendCommand: (String) -> Unit,
    private val echoText: (String) -> Unit,
    private val getMsdpValue: (String) -> Any?,
    private val getCurrentRoom: () -> Map<String, Any>?,
    private val findPath: (String) -> List<String>?,
    private val fireEvent: (ScriptEvent, Any?) -> Unit
) : BotActions {

    private val scope = CoroutineScope(Dispatchers.Default)

    // Ядро бота
    val botCore: BotCore by lazy {
        BotCore(
            sendCommand = sendCommand,
            echoText = echoText,
            getMsdpValue = getMsdpValue,
            getCurrentRoom = getCurrentRoom,
            findPath = findPath,
            fireEvent = fireEvent
        ).also { core ->
            core.onLog = { message ->
                if (core.config.value.verboseLogging) {
                    echoText("[BOT LOG] $message")
                }
            }
            core.onError = { error ->
                echoText("[BOT ERROR] $error")
            }
        }
    }

    // LLM парсер
    private var llmParser: LLMParser? = null

    /**
     * Инициализировать LLM парсер
     */
    fun initializeLLM(baseUrl: String = "http://localhost:11434", model: String = "llama3") {
        scope.launch {
            llmParser = LLMParser(baseUrl, model)
            if (llmParser?.initialize() == true) {
                echoText("[BOT] LLM парсер инициализирован (модель: $model)")
            } else {
                echoText("[BOT] Не удалось инициализировать LLM парсер")
            }
        }
    }

    /**
     * Обработать входящую строку от сервера
     */
    fun processLine(line: String) {
        if (botCore.stateMachine.isActive()) {
            botCore.processLine(line)
        }
    }

    /**
     * Обработать вход в комнату
     */
    fun onRoomEnter(room: Map<String, Any>) {
        if (botCore.stateMachine.isActive()) {
            botCore.onRoomEnter(room)
        }
    }

    /**
     * Обработать команду бота (#bot ...)
     */
    fun handleCommand(args: String): Boolean {
        val parts = args.trim().split("\\s+".toRegex())
        if (parts.isEmpty()) {
            showHelp()
            return true
        }

        return when (parts[0].lowercase()) {
            "start" -> {
                val mode = if (parts.size > 1) {
                    try {
                        BotMode.valueOf(parts[1].uppercase())
                    } catch (e: Exception) {
                        BotMode.LEVELING
                    }
                } else {
                    BotMode.LEVELING
                }
                botCore.start(mode)
                true
            }
            "stop" -> {
                botCore.stop()
                true
            }
            "status" -> {
                showStatus()
                true
            }
            "config" -> {
                if (parts.size > 2) {
                    setConfigValue(parts[1], parts.drop(2).joinToString(" "))
                } else {
                    showConfig()
                }
                true
            }
            "llm" -> {
                when (parts.getOrNull(1)?.lowercase()) {
                    "init" -> {
                        val url = parts.getOrNull(2) ?: "http://localhost:11434"
                        val model = parts.getOrNull(3) ?: "llama3"
                        initializeLLM(url, model)
                    }
                    "status" -> {
                        val status = llmParser?.getStatus() ?: mapOf("initialized" to false)
                        echoText("[BOT LLM] Статус: $status")
                    }
                    else -> echoText("[BOT] Использование: #bot llm init [url] [model] | #bot llm status")
                }
                true
            }
            "help", "?" -> {
                showHelp()
                true
            }
            else -> {
                echoText("[BOT] Неизвестная команда: ${parts[0]}")
                showHelp()
                false
            }
        }
    }

    private fun showHelp() {
        echoText("""
            [BOT] Команды бота:
            #bot start [mode]  - Запустить бота (mode: LEVELING, FARMING, EXPLORING)
            #bot stop          - Остановить бота
            #bot status        - Показать статус
            #bot config        - Показать конфигурацию
            #bot config <key> <value> - Установить значение конфига
            #bot llm init [url] [model] - Инициализировать LLM
            #bot llm status    - Статус LLM
            #bot help          - Показать эту справку
        """.trimIndent())
    }

    private fun showStatus() {
        val status = botCore.getStatus()
        val fsm = botCore.stateMachine.getStatus()

        echoText("""
            [BOT] Статус бота:
            Запущен: ${status["running"]}
            Состояние: ${status["state"]}
            Режим: ${status["mode"]}
            В бою: ${fsm["isInCombat"]}
            Отдых: ${fsm["isResting"]}
        """.trimIndent())

        @Suppress("UNCHECKED_CAST")
        val session = status["session"] as? Map<String, Any>
        if (session?.isNotEmpty() == true) {
            echoText("""
                Сессия:
                  Убийства: ${session["kills"]}
                  Смерти: ${session["deaths"]}
                  Опыт: ${session["exp"]}
            """.trimIndent())
        }
    }

    private fun showConfig() {
        val cfg = botCore.config.value
        echoText("""
            [BOT] Конфигурация:
            mode: ${cfg.mode}
            fleeHpPercent: ${cfg.fleeHpPercent}
            restHpPercent: ${cfg.restHpPercent}
            autoAttack: ${cfg.autoAttack}
            autoLoot: ${cfg.autoLoot}
            autoBuffs: ${cfg.autoBuffs}
            useLLMParsing: ${cfg.useLLMParsing}
            verboseLogging: ${cfg.verboseLogging}
        """.trimIndent())
    }

    private fun setConfigValue(key: String, value: String) {
        val cfg = botCore.config.value
        when (key.lowercase()) {
            "fleehppercent", "fleehp" -> cfg.fleeHpPercent = value.toIntOrNull() ?: cfg.fleeHpPercent
            "resthppercent", "resthp" -> cfg.restHpPercent = value.toIntOrNull() ?: cfg.restHpPercent
            "autoattack" -> cfg.autoAttack = value.toBooleanStrictOrNull() ?: cfg.autoAttack
            "autoloot" -> cfg.autoLoot = value.toBooleanStrictOrNull() ?: cfg.autoLoot
            "autobuffs" -> cfg.autoBuffs = value.toBooleanStrictOrNull() ?: cfg.autoBuffs
            "verboselogging", "verbose" -> cfg.verboseLogging = value.toBooleanStrictOrNull() ?: cfg.verboseLogging
            else -> {
                echoText("[BOT] Неизвестный ключ конфига: $key")
                return
            }
        }
        botCore.config.value = cfg
        echoText("[BOT] $key = $value")
    }

    fun shutdown() {
        botCore.shutdown()
    }

    // ============================================
    // Реализация BotActions для ScriptAPI
    // ============================================

    override fun getMobsInRoom(): List<Map<String, Any>> {
        return botCore.entityTracker.getMobsAsMap()
    }

    override fun getPlayersInRoom(): List<Map<String, Any>> {
        return botCore.entityTracker.getPlayersAsMap()
    }

    override fun getMyTarget(): Map<String, Any>? {
        val target = botCore.combatManager.currentTarget ?: return null
        return mapOf(
            "name" to target.name,
            "shortName" to (target.shortName ?: ""),
            "hpPercent" to (target.hpPercent ?: -1),
            "condition" to (target.condition?.name ?: "UNKNOWN")
        )
    }

    override fun isInCombat(): Boolean {
        return botCore.combatManager.isInCombat()
    }

    override fun getCombatLog(limit: Int): List<Map<String, Any>> {
        return botCore.combatParser.getCombatLogAsMap(limit)
    }

    override fun getInventory(): List<Map<String, Any>> {
        // TODO: Реализовать через MSDP/парсинг
        return emptyList()
    }

    override fun getEquipment(): Map<String, Map<String, Any>> {
        // TODO: Реализовать через MSDP/парсинг
        return emptyMap()
    }

    override fun findItem(pattern: String): Map<String, Any>? {
        // TODO: Реализовать
        return null
    }

    override fun getAffects(): List<Map<String, Any>> {
        // TODO: Реализовать через MSDP/парсинг
        return emptyList()
    }

    override fun getSkills(): Map<String, Map<String, Any>> {
        // TODO: Реализовать через MSDP/парсинг
        return emptyMap()
    }

    override fun getPosition(): String {
        return botCore.characterState.value?.position?.name ?: "UNKNOWN"
    }

    override fun getCharacterState(): Map<String, Any> {
        val state = botCore.characterState.value ?: return emptyMap()
        return mapOf(
            "hp" to state.hp,
            "maxHp" to state.maxHp,
            "hpPercent" to state.hpPercent,
            "mana" to state.mana,
            "maxMana" to state.maxMana,
            "manaPercent" to state.manaPercent,
            "move" to state.move,
            "maxMove" to state.maxMove,
            "level" to state.level,
            "experience" to state.experience,
            "position" to state.position.name,
            "roomId" to (state.roomId ?: ""),
            "zoneId" to (state.zoneId ?: ""),
            "isInCombat" to state.isInCombat,
            "targetName" to (state.targetName ?: ""),
            "gold" to state.gold
        )
    }

    override fun botStart(mode: String, config: Map<String, Any>?) {
        val botMode = try {
            BotMode.valueOf(mode.uppercase())
        } catch (e: Exception) {
            BotMode.LEVELING
        }
        botCore.start(botMode, config)
    }

    override fun botStop() {
        botCore.stop()
    }

    override fun getBotStatus(): Map<String, Any> {
        return botCore.getStatus() + mapOf(
            "llm" to (llmParser?.getStatus() ?: mapOf("initialized" to false, "available" to false))
        )
    }

    /**
     * Получить статус LLM парсера
     */
    fun getLLMStatus(): Map<String, Any> {
        return llmParser?.getStatus() ?: mapOf("initialized" to false, "available" to false)
    }

    override fun setBotConfig(config: Map<String, Any>) {
        botCore.config.value = BotConfig.fromMap(config)
    }

    override fun getBotConfig(): Map<String, Any> {
        return botCore.config.value.toMap()
    }

    override fun saveMobData(mobId: String, data: Map<String, Any>) {
        val mobData = MobData(
            id = mobId,
            name = data["name"]?.toString() ?: mobId,
            shortName = data["shortName"]?.toString(),
            level = (data["level"] as? Number)?.toInt(),
            avgHp = (data["avgHp"] as? Number)?.toInt(),
            expReward = (data["expReward"] as? Number)?.toInt(),
            goldReward = (data["goldReward"] as? Number)?.toInt(),
            zoneId = data["zoneId"]?.toString(),
            aggressive = data["aggressive"] as? Boolean ?: false,
            notes = data["notes"]?.toString() ?: ""
        )
        botCore.database.saveMob(mobData)
    }

    override fun getMobData(mobId: String): Map<String, Any>? {
        val mob = botCore.database.getMob(mobId) ?: return null
        return mapOf(
            "id" to mob.id,
            "name" to mob.name,
            "shortName" to (mob.shortName ?: ""),
            "level" to (mob.level ?: -1),
            "avgHp" to (mob.avgHp ?: -1),
            "expReward" to (mob.expReward ?: -1),
            "goldReward" to (mob.goldReward ?: -1),
            "zoneId" to (mob.zoneId ?: ""),
            "aggressive" to mob.aggressive,
            "killCount" to mob.killCount,
            "deathCount" to mob.deathCount,
            "notes" to mob.notes
        )
    }

    override fun findMobsByName(pattern: String): List<Map<String, Any>> {
        return botCore.database.findMobsByName(pattern).map { mob ->
            mapOf(
                "id" to mob.id,
                "name" to mob.name,
                "level" to (mob.level ?: -1),
                "zoneId" to (mob.zoneId ?: ""),
                "killCount" to mob.killCount
            )
        }
    }

    override fun getZoneStats(zoneId: String): Map<String, Any>? {
        val stats = botCore.database.getZoneStats(zoneId) ?: return null
        return mapOf(
            "zoneId" to stats.zoneId,
            "levelMin" to (stats.levelMin ?: -1),
            "levelMax" to (stats.levelMax ?: -1),
            "avgExpPerHour" to (stats.avgExpPerHour ?: 0.0),
            "dangerLevel" to (stats.dangerLevel ?: 0.0),
            "totalKills" to stats.totalKills,
            "totalDeaths" to stats.totalDeaths,
            "totalExpGained" to stats.totalExpGained
        )
    }

    override fun getZonesForLevel(level: Int): List<Map<String, Any>> {
        return botCore.database.getZonesForLevel(level).map { stats ->
            mapOf(
                "zoneId" to stats.zoneId,
                "levelMin" to (stats.levelMin ?: -1),
                "levelMax" to (stats.levelMax ?: -1),
                "avgExpPerHour" to (stats.avgExpPerHour ?: 0.0),
                "dangerLevel" to (stats.dangerLevel ?: 0.0)
            )
        }
    }

    override fun parseRoomDescription(text: String): Map<String, Any> {
        if (botCore.config.value.useLLMParsing && llmParser?.isAvailable() == true) {
            val result = llmParser?.parseRoomDescription(text)
            if (result != null && result.confidence > 0.5) {
                return mapOf(
                    "mobs" to result.mobs.map { mob ->
                        mapOf(
                            "name" to mob.name,
                            "shortName" to (mob.shortName ?: ""),
                            "isAggressive" to (mob.isAggressive ?: false),
                            "condition" to (mob.condition?.name ?: "UNKNOWN")
                        )
                    },
                    "isDangerous" to (result.isDangerous ?: false),
                    "features" to result.features,
                    "confidence" to result.confidence
                )
            }
        }
        return emptyMap()
    }

    override fun parseCombatMessage(text: String): Map<String, Any>? {
        if (botCore.config.value.useLLMParsing && llmParser?.isAvailable() == true) {
            val result = llmParser?.parseCombatMessage(text)
            if (result != null && result.confidence > 0.5) {
                val event = result.event ?: return null
                return mapOf(
                    "type" to event.type.name,
                    "source" to (event.source ?: ""),
                    "target" to (event.target ?: ""),
                    "damage" to (event.damage ?: 0),
                    "skill" to (event.skill ?: ""),
                    "isCritical" to event.isCritical,
                    "confidence" to result.confidence
                )
            }
        }
        return null
    }

    override fun parseInspectResult(text: String): Map<String, Any>? {
        if (botCore.config.value.useLLMParsing && llmParser?.isAvailable() == true) {
            val result = llmParser?.parseInspectResult(text)
            if (result != null && result.confidence > 0.5) {
                return mapOf(
                    "estimatedLevel" to (result.estimatedLevel ?: -1),
                    "estimatedHp" to (result.estimatedHp ?: -1),
                    "confidence" to result.confidence
                )
            }
        }
        return null
    }

    override fun loadModel(name: String, path: String): Boolean {
        // TODO: Реализовать загрузку ONNX модели
        return false
    }

    override fun predict(modelName: String, input: List<Double>): List<Double>? {
        // TODO: Реализовать предсказание через ONNX
        return null
    }

    override fun saveExperience(type: String, data: Map<String, Any>) {
        val trainingData = TrainingData(
            type = type,
            timestamp = System.currentTimeMillis(),
            state = data["state"]?.toString() ?: "{}",
            action = data["action"]?.toString() ?: "",
            reward = (data["reward"] as? Number)?.toDouble(),
            nextState = data["nextState"]?.toString(),
            episodeId = data["episodeId"]?.toString(),
            success = data["success"] as? Boolean
        )
        botCore.database.saveTrainingData(trainingData)
    }
}
