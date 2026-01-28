package com.bylins.client.bot

import com.bylins.client.bot.combat.CombatManager
import com.bylins.client.bot.llm.LLMParser
import com.bylins.client.bot.navigation.Navigator
import com.bylins.client.bot.perception.*
import com.bylins.client.plugins.PluginAPI
import com.bylins.client.plugins.scripting.ScriptEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

/**
 * Ядро AI-бота
 * Координирует все подсистемы: FSM, бой, навигацию, парсинг
 *
 * Использует PluginAPI для взаимодействия с клиентом:
 * - Отправка команд: api.send()
 * - Вывод в консоль: api.echo()
 * - Данные MSDP: api.getMsdpValue()
 * - Текущая комната: api.getCurrentRoom()
 * - Поиск пути: api.findPath()
 * - События скриптам: api.fireScriptEvent()
 */
private val logger = KotlinLogging.logger("BotCore")

class BotCore(
    private val api: PluginAPI
) {
    // Состояние
    val stateMachine = BotStateMachine()
    val config = MutableStateFlow(BotConfig())

    // База данных
    val database = BotDatabase()

    // Текущая сессия
    private var currentSession: BotSession? = null

    // Подсистемы (lazy initialization)
    val entityTracker by lazy { EntityTracker(this) }
    val combatParser by lazy { CombatParser(this) }
    val combatManager by lazy { CombatManager(this) }
    val navigator by lazy { Navigator(this) }

    // LLM и адаптивные парсеры
    var llmParser: LLMParser? = null
        private set
    val promptParser by lazy {
        PromptParser().apply {
            // Подписываемся на изменения опыта для эмпирического обучения урона
            onExpChange = { expDelta, previousPrompt, currentPrompt ->
                handleExpChange(expDelta, previousPrompt, currentPrompt)
            }
        }
    }
    val adaptiveCombatParser by lazy { AdaptiveCombatParser(database, llmParser) }

    // Буфер последних боевых сообщений для корреляции с изменением опыта
    private val recentCombatMessages = mutableListOf<CombatMessage>()

    // Coroutine scope для асинхронных операций
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Job для основного цикла бота
    private var mainLoopJob: Job? = null

    // Состояние персонажа (кэш)
    private val _characterState = MutableStateFlow<CharacterState?>(null)
    val characterState: StateFlow<CharacterState?> = _characterState

    // Счётчики
    private var deathCount = 0

    // Callback'и для интеграции с клиентом
    var onStateChange: ((BotStateType, BotStateType) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null

    init {
        // Подписываемся на изменения состояния FSM
        stateMachine.addStateListener { oldState, newState, transition ->
            onStateChange?.invoke(oldState, newState)
            log("State: $oldState -> $newState ($transition)")
        }
    }

    /**
     * Запустить бота
     */
    fun start(mode: BotMode = BotMode.LEVELING, customConfig: Map<String, Any>? = null) {
        if (stateMachine.isActive()) {
            echo("[BOT] Бот уже запущен")
            return
        }

        // Применяем кастомный конфиг если есть
        customConfig?.let {
            config.value = BotConfig.fromMap(it).also { cfg ->
                cfg.mode = mode
                cfg.enabled = true
            }
        } ?: run {
            config.value = config.value.copy(mode = mode, enabled = true)
        }

        // Создаём новую сессию
        val sessionId = UUID.randomUUID().toString()
        currentSession = database.startBotSession(
            sessionId = sessionId,
            mode = mode,
            zoneId = api.getCurrentRoom()?.get("zone") as? String
        )

        deathCount = 0

        echo("[BOT] Запуск бота в режиме: ${mode.name}")
        log("Starting bot session: $sessionId, mode: $mode")

        // Запускаем FSM
        stateMachine.transition(BotTransition.START)

        // Запускаем основной цикл
        startMainLoop()
    }

    /**
     * Остановить бота
     */
    fun stop() {
        if (!stateMachine.isActive() && stateMachine.currentState.value != BotStateType.ERROR) {
            echo("[BOT] Бот не запущен")
            return
        }

        echo("[BOT] Остановка бота...")
        log("Stopping bot")

        // Останавливаем основной цикл
        mainLoopJob?.cancel()
        mainLoopJob = null

        // Переводим FSM в остановку
        stateMachine.transition(BotTransition.STOP)
        stateMachine.transition(BotTransition.STOP) // Второй раз для перехода в IDLE

        // Завершаем сессию
        currentSession?.let { session ->
            database.endBotSession(session)
            log("Bot session ended: ${session.id}, kills: ${session.totalKills}, deaths: ${session.totalDeaths}")
        }
        currentSession = null

        config.value = config.value.copy(enabled = false)

        echo("[BOT] Бот остановлен")
    }

    /**
     * Основной цикл бота
     */
    private fun startMainLoop() {
        mainLoopJob = scope.launch {
            while (isActive && stateMachine.isActive()) {
                try {
                    tick()
                    delay(100) // 10 тиков в секунду
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    logger.error(e) { "Error in bot main loop" }
                    onError?.invoke("Ошибка в главном цикле: ${e.message}")
                    stateMachine.transition(BotTransition.ERROR_OCCURRED)
                    delay(1000)
                }
            }
        }
    }

    /**
     * Один тик обработки
     */
    private suspend fun tick() {
        // Обновляем состояние персонажа
        updateCharacterState()

        val state = stateMachine.currentState.value
        val charState = _characterState.value ?: return

        // Проверка критических условий
        if (checkCriticalConditions(charState)) {
            return
        }

        // Обработка в зависимости от состояния
        when (state) {
            BotStateType.STARTING -> handleStarting()
            BotStateType.TRAVELING -> handleTraveling()
            BotStateType.COMBAT -> handleCombat()
            BotStateType.LOOTING -> handleLooting()
            BotStateType.RESTING -> handleResting(charState)
            BotStateType.BUFFING -> handleBuffing()
            BotStateType.FLEEING -> handleFleeing()
            BotStateType.EXPLORING -> handleExploring()
            BotStateType.RETURNING -> handleReturning()
            BotStateType.ERROR -> handleError()
            else -> { /* IDLE, STOPPING - ничего не делаем */ }
        }
    }

    /**
     * Обновить кэшированное состояние персонажа
     * Использует MSDP + PromptParser как fallback
     */
    private fun updateCharacterState() {
        // Bylins MSDP структура:
        // - state = {CURRENT_HP=268, CURRENT_MOVE=107}
        // - max_hit, max_move (lowercase)
        // - level, experience, gold, room

        // Получаем state как Map
        @Suppress("UNCHECKED_CAST")
        val stateMap = api.getMsdpValue("state") as? Map<String, Any>
            ?: api.getMsdpValue("STATE") as? Map<String, Any>

        // HP из MSDP state или из PromptParser
        val promptData = promptParser.lastPromptData
        val hp = stateMap?.get("CURRENT_HP")?.toString()?.toIntOrNull()
            ?: stateMap?.get("current_hp")?.toString()?.toIntOrNull()
            ?: promptData?.hp

        if (hp == null) {
            if (_characterState.value == null) {
                log("Ожидание данных HP (MSDP state или промпт)...")
            }
            return
        }

        val maxHp = api.getMsdpValue("max_hit")?.toString()?.toIntOrNull()
            ?: api.getMsdpValue("MAX_HIT")?.toString()?.toIntOrNull()
            ?: promptData?.maxHp
            ?: 1

        // Move: MSDP state или промпт (M в промпте = Move, не Mana!)
        val move = stateMap?.get("CURRENT_MOVE")?.toString()?.toIntOrNull()
            ?: stateMap?.get("current_move")?.toString()?.toIntOrNull()
            ?: promptData?.move
            ?: 0

        val maxMove = api.getMsdpValue("max_move")?.toString()?.toIntOrNull()
            ?: api.getMsdpValue("MAX_MOVE")?.toString()?.toIntOrNull()
            ?: promptData?.maxMove
            ?: 1

        // В Былинах нет маны - используется система заучивания заклинаний

        val level = api.getMsdpValue("level")?.toString()?.toIntOrNull()
            ?: api.getMsdpValue("LEVEL")?.toString()?.toIntOrNull()
            ?: 1

        val exp = api.getMsdpValue("experience")?.toString()?.toLongOrNull()
            ?: api.getMsdpValue("EXPERIENCE")?.toString()?.toLongOrNull()
            ?: 0

        // Gold может быть Map {POCKET=X, BANK=Y}
        val goldRaw = api.getMsdpValue("gold") ?: api.getMsdpValue("GOLD")
        @Suppress("UNCHECKED_CAST")
        val gold = when (goldRaw) {
            is Number -> goldRaw.toInt()
            is Map<*, *> -> (goldRaw["POCKET"] ?: goldRaw["pocket"])?.toString()?.toIntOrNull() ?: 0
            else -> goldRaw?.toString()?.toIntOrNull() ?: 0
        }

        // Room из MSDP
        @Suppress("UNCHECKED_CAST")
        val roomMap = api.getMsdpValue("room") as? Map<String, Any>
            ?: api.getMsdpValue("ROOM") as? Map<String, Any>

        val roomId = roomMap?.get("VNUM")?.toString()
            ?: roomMap?.get("vnum")?.toString()
            ?: api.getCurrentRoom()?.get("id") as? String

        val zoneId = roomMap?.get("ZONE")?.toString()
            ?: roomMap?.get("zone")?.toString()
            ?: api.getCurrentRoom()?.get("zone") as? String

        // TERRAIN из MSDP room (тип поверхности: "Внутри", "Город", "Лес" и т.д.)
        val terrain = roomMap?.get("TERRAIN")?.toString()
            ?: roomMap?.get("terrain")?.toString()

        // EXITS из MSDP room (может быть Map<String, vnum> или List<String>)
        val exitsRaw = roomMap?.get("EXITS") ?: roomMap?.get("exits")
        @Suppress("UNCHECKED_CAST")
        val exits: List<String> = when (exitsRaw) {
            is Map<*, *> -> exitsRaw.keys.mapNotNull { it?.toString() }
            is List<*> -> exitsRaw.mapNotNull { it?.toString() }
            is String -> exitsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            else -> promptData?.exits ?: emptyList()
        }

        // Позиция из промпта (MSDP её не даёт напрямую)
        val position = when {
            promptParser.isInCombat() -> Position.FIGHTING
            promptData?.position != null -> promptData.position
            else -> Position.STANDING
        }

        val isInCombat = position == Position.FIGHTING || combatManager.isInCombat() || promptParser.isInCombat()

        _characterState.value = CharacterState(
            hp = hp,
            maxHp = maxHp,
            move = move,
            maxMove = maxMove,
            level = level,
            experience = exp,
            position = position,
            roomId = roomId,
            zoneId = zoneId,
            terrain = terrain,
            exits = exits,
            isInCombat = isInCombat,
            targetName = combatManager.currentTarget?.name ?: promptData?.targetName,
            targetHpPercent = combatManager.currentTarget?.hpPercent,
            gold = gold
        )
    }

    /**
     * Проверка критических условий
     */
    private fun checkCriticalConditions(charState: CharacterState): Boolean {
        val cfg = config.value

        // Проверка смерти
        if (charState.hp <= 0) {
            handleDeath()
            return true
        }

        // Проверка низкого HP (flee)
        if (charState.hpPercent < cfg.fleeHpPercent && stateMachine.currentState.value == BotStateType.COMBAT) {
            log("Low HP detected: ${charState.hpPercent}% < ${cfg.fleeHpPercent}%")
            stateMachine.transition(BotTransition.LOW_HP)
            api.fireScriptEvent(ScriptEvent.ON_LOW_HP, charState.hpPercent)
            return true
        }

        // Проверка лимита смертей
        if (deathCount >= cfg.maxDeathsPerSession) {
            echo("[BOT] Достигнут лимит смертей ($deathCount), бот останавливается")
            stop()
            return true
        }

        return false
    }

    /**
     * Обработка смерти
     */
    private fun handleDeath() {
        deathCount++
        currentSession?.totalDeaths = deathCount

        echo("[BOT] Смерть #$deathCount")
        log("Player death #$deathCount")

        api.fireScriptEvent(ScriptEvent.ON_PLAYER_DEATH, deathCount)

        stateMachine.transition(BotTransition.COMBAT_LOSE)
    }

    // ============================================
    // Обработчики состояний
    // ============================================

    private fun handleStarting() {
        val charState = _characterState.value
        if (charState == null) {
            log("Starting: ожидание данных персонажа...")
            return
        }

        log("Starting: HP=${charState.hp}/${charState.maxHp} (${charState.hpPercent}%), room=${charState.roomId}, exits=${charState.exits}")

        val cfg = config.value

        // Проверяем баффы
        if (cfg.autoBuffs && needsBuffs()) {
            log("Starting: нужны баффы, переход в BUFFING")
            stateMachine.transition(BotTransition.BUFFS_NEEDED)
            return
        }

        // Проверяем HP для отдыха
        if (charState.hpPercent < cfg.restHpPercent) {
            log("Starting: низкий HP (${charState.hpPercent}% < ${cfg.restHpPercent}%), переход в RESTING")
            stateMachine.transition(BotTransition.LOW_HP)
            return
        }

        // В режиме EXPLORING сразу переходим в исследование
        if (cfg.mode == BotMode.EXPLORING) {
            log("Starting: режим EXPLORING, переход в EXPLORING state")
            stateMachine.transition(BotTransition.NO_PATH)
            return
        }

        // Ищем путь или переходим к исследованию
        if (navigator.hasTarget() || navigator.findNextTarget()) {
            log("Starting: цель найдена, переход в TRAVELING")
            stateMachine.transition(BotTransition.PATH_FOUND)
        } else {
            log("Starting: цель не найдена, переход в EXPLORING")
            stateMachine.transition(BotTransition.NO_PATH)
        }
    }

    private fun handleTraveling() {
        // Проверяем мобов в комнате
        val mobs = entityTracker.getMobsInRoom()
        if (mobs.isNotEmpty()) {
            log("Traveling: мобы в комнате: ${mobs.map { it.name }}")
            if (config.value.autoAttack) {
                val targetMob = combatManager.selectTarget(mobs)
                if (targetMob != null) {
                    log("Traveling: атакуем ${targetMob.name}")
                    stateMachine.transition(BotTransition.ENEMY_DETECTED)
                    return
                }
            }
        }

        // Проверяем баффы
        if (config.value.autoBuffs && needsBuffs()) {
            log("Traveling: нужны баффы")
            stateMachine.transition(BotTransition.BUFFS_NEEDED)
            return
        }

        // Двигаемся дальше
        navigator.moveNext()
    }

    private fun handleCombat() {
        combatManager.tick()

        // Проверяем завершение боя
        if (!combatManager.isInCombat()) {
            currentSession?.totalKills = (currentSession?.totalKills ?: 0) + 1
            api.fireScriptEvent(ScriptEvent.ON_MOB_KILLED, combatManager.lastKilledMob)
            stateMachine.transition(BotTransition.COMBAT_WIN)
        }
    }

    private fun handleLooting() {
        val cfg = config.value

        if (cfg.autoLoot) {
            if (cfg.autoLootGold) {
                send("взять монет труп")
            }
            if (cfg.autoLootItems) {
                send("взять все труп")
            }
        }

        // Небольшая задержка и переход
        stateMachine.transition(BotTransition.LOOT_DONE)
    }

    private fun handleResting(charState: CharacterState) {
        val cfg = config.value

        // Проверяем достаточно ли HP
        // В Былинах нет маны - заклинания заучиваются
        if (charState.hpPercent >= cfg.restHpPercent) {
            send("встать")
            stateMachine.transition(BotTransition.HP_RECOVERED)
            return
        }

        // Продолжаем отдыхать
        val position = charState.position
        if (position != Position.RESTING && position != Position.SLEEPING) {
            if (cfg.autoSleep) {
                send("спать")
            } else {
                send("отдых")
            }
        }
    }

    private fun handleBuffing() {
        val cfg = config.value

        if (cfg.buffList.isEmpty()) {
            stateMachine.transition(BotTransition.BUFFS_APPLIED)
            return
        }

        // Применяем баффы
        for (buff in cfg.buffList) {
            // Проверяем есть ли уже этот бафф
            if (!hasAffect(buff)) {
                send("колд '$buff'")
                return // Один бафф за тик
            }
        }

        // Все баффы наложены
        stateMachine.transition(BotTransition.BUFFS_APPLIED)
    }

    private fun handleFleeing() {
        val cfg = config.value

        // Пытаемся убежать
        send("бежать")

        // Если есть авто-рекол
        if (cfg.autoRecallOnLowHp) {
            send("колд 'возврат'")
        }
    }

    private fun handleExploring() {
        val charState = _characterState.value
        log("handleExploring: room=${charState?.roomId}, exits=${charState?.exits}, terrain=${charState?.terrain}")

        // Ищем новую цель для исследования
        if (navigator.findNextTarget()) {
            log("handleExploring: цель найдена, переход в TRAVELING")
            stateMachine.transition(BotTransition.PATH_FOUND)
        } else {
            // Нет куда идти - пробуем случайное направление
            log("handleExploring: цель не найдена, пробую случайное направление")
            navigator.exploreRandom()
        }
    }

    private fun handleReturning() {
        // Возврат в безопасную зону
        val safeZones = config.value.safeZones
        if (safeZones.isNotEmpty()) {
            // Ищем путь в безопасную зону
            navigator.setTarget(safeZones.first())
        }
        navigator.moveNext()
    }

    private fun handleError() {
        // Ждём восстановления или ручного вмешательства
        log("Bot in error state, waiting for recovery")
    }

    // ============================================
    // Вспомогательные методы
    // ============================================

    private fun needsBuffs(): Boolean {
        val cfg = config.value
        if (!cfg.autoBuffs || cfg.buffList.isEmpty()) return false

        for (buff in cfg.buffList) {
            if (!hasAffect(buff)) {
                return true
            }
        }
        return false
    }

    @Suppress("UNUSED_PARAMETER")
    private fun hasAffect(_affectName: String): Boolean {
        // TODO: Реализовать проверку аффектов через MSDP/парсинг
        return false
    }

    fun send(command: String) {
        log("Sending: $command")
        api.send(command)
    }

    fun echo(text: String) {
        api.echo(text)
    }

    fun log(message: String) {
        logger.info { message }
        onLog?.invoke(message)
    }

    /**
     * Получить текущую комнату (для подсистем)
     */
    fun getRoom(): Map<String, Any>? = api.getCurrentRoom()

    /**
     * Найти ближайшую комнату, удовлетворяющую условию
     * Возвращает пару (комната, путь) или null
     */
    fun findNearestRoom(predicate: (Map<String, Any>) -> Boolean): Pair<Map<String, Any>, List<String>>? {
        return api.findNearestMatching(predicate)
    }

    /**
     * Найти путь к комнате по ID
     */
    fun findPathToRoom(roomId: String): List<String>? {
        return api.findPath(roomId)
    }

    /**
     * Обработать входящую строку от сервера
     */
    fun processLine(line: String) {
        // Парсим промпт для определения состояния боя
        val combatStateChanges = promptParser.processPrompt(line)
        for (change in combatStateChanges) {
            handleCombatStateChange(change)
        }

        // Если в бою, парсим боевые сообщения адаптивным парсером
        if (promptParser.isInCombat()) {
            scope.launch {
                val combatMessage = adaptiveCombatParser.parseMessage(line)
                if (combatMessage != null) {
                    handleCombatMessage(combatMessage)
                }
            }
        }

        // Парсим боевые сообщения (старый rule-based парсер)
        combatParser.parseLine(line)

        // Обновляем трекер сущностей
        entityTracker.processLine(line)
    }

    /**
     * Обработать изменение состояния боя (из PromptParser)
     */
    private fun handleCombatStateChange(change: CombatStateChange) {
        when (change) {
            is CombatStateChange.CombatStarted -> {
                log("Combat started with: ${change.targetName}")
                combatManager.onCombatStarted(change.targetName, change.targetCondition)
                api.fireScriptEvent(ScriptEvent.ON_COMBAT_START, mapOf(
                    "target" to change.targetName,
                    "targetCondition" to (change.targetCondition ?: "unknown")
                ))
                // Переводим FSM в боевой режим если бот активен
                if (stateMachine.isActive() && stateMachine.currentState.value != BotStateType.COMBAT) {
                    stateMachine.transition(BotTransition.ENEMY_DETECTED)
                }
            }
            is CombatStateChange.CombatEnded -> {
                log("Combat ended: ${change.reason}")
                combatManager.onCombatEnded(change.reason)
                api.fireScriptEvent(ScriptEvent.ON_COMBAT_END, mapOf(
                    "reason" to change.reason.name
                ))
            }
            is CombatStateChange.TargetChanged -> {
                log("Target changed to: ${change.newTarget}")
                combatManager.onTargetChanged(change.newTarget, change.newCondition)
            }
            is CombatStateChange.TargetConditionChanged -> {
                log("Target ${change.targetName} condition: ${change.oldCondition} -> ${change.newCondition}")
                combatManager.onTargetConditionChanged(change.targetName, change.newCondition)
            }
            is CombatStateChange.PlayerConditionChanged -> {
                log("Player condition: ${change.oldCondition} -> ${change.newCondition}")
            }
        }
    }

    /**
     * Обработать распознанное боевое сообщение
     */
    private fun handleCombatMessage(message: CombatMessage) {
        // Добавляем в буфер для корреляции с изменением опыта
        if (message.type == CombatMessageType.DAMAGE_DEALT || message.type == CombatMessageType.DAMAGE_RECEIVED) {
            recentCombatMessages.add(message)
            // Ограничиваем размер буфера (между промптами обычно не больше 4-6 сообщений)
            if (recentCombatMessages.size > 10) {
                recentCombatMessages.removeAt(0)
            }
        }

        when (message.type) {
            CombatMessageType.DAMAGE_DEALT -> {
                // Зафиксировать нанесённый урон
                combatManager.onDamageDealt(message.target, message.intensity)
            }
            CombatMessageType.DAMAGE_RECEIVED -> {
                // Зафиксировать полученный урон
                combatManager.onDamageReceived(message.source, message.intensity)
            }
            CombatMessageType.MOB_DEATH -> {
                // Моб убит
                log("Mob killed: ${message.target}")
                currentSession?.totalKills = (currentSession?.totalKills ?: 0) + 1
                api.fireScriptEvent(ScriptEvent.ON_MOB_KILLED, mapOf(
                    "name" to (message.target ?: "unknown")
                ))
            }
            CombatMessageType.PLAYER_DEATH -> {
                // Игрок погиб
                handleDeath()
            }
            CombatMessageType.PLAYER_FLED -> {
                // Игрок сбежал
                log("Player fled")
                api.fireScriptEvent(ScriptEvent.ON_COMBAT_END, mapOf("reason" to "PLAYER_FLED"))
            }
            CombatMessageType.MOB_FLED -> {
                // Моб сбежал
                log("Mob fled: ${message.source}")
                api.fireScriptEvent(ScriptEvent.ON_COMBAT_END, mapOf("reason" to "MOB_FLED"))
            }
            CombatMessageType.EXP_GAIN -> {
                // Получен опыт
                message.amount?.let { exp ->
                    currentSession?.totalExpGained = (currentSession?.totalExpGained ?: 0) + exp
                    api.fireScriptEvent(ScriptEvent.ON_EXP_GAIN, exp)
                }
            }
            CombatMessageType.LEVEL_UP -> {
                // Повышение уровня
                log("Level up: ${message.amount}")
                api.fireScriptEvent(ScriptEvent.ON_LEVEL_UP, message.amount)
            }
            else -> { /* MISS, SKILL_USED, AFFECT_APPLIED, AFFECT_EXPIRED, UNKNOWN */ }
        }
    }

    /**
     * Инициализировать LLM парсер
     */
    fun initializeLLM(baseUrl: String = "http://localhost:11434", modelName: String = "llama3"): Boolean {
        llmParser = LLMParser(baseUrl, modelName)
        val success = llmParser?.initialize() ?: false
        if (success) {
            echo("[BOT] LLM парсер инициализирован: $modelName")
        } else {
            echo("[BOT] Не удалось инициализировать LLM парсер")
        }
        return success
    }

    /**
     * Обработать изменение опыта (для эмпирического обучения урона)
     *
     * Когда получен опыт (expToLevel уменьшился), мы можем соотнести его
     * с недавними боевыми сообщениями для оценки урона.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun handleExpChange(expDelta: Int, _previousPrompt: PromptData?, _currentPrompt: PromptData) {
        if (!promptParser.isInCombat()) {
            // Вне боя - очищаем буфер
            recentCombatMessages.clear()
            return
        }

        // В бою: опыт получен за удары
        // Если в буфере есть сообщения о нанесённом уроне, можем оценить урон
        val damageMessages = recentCombatMessages.filter {
            it.type == CombatMessageType.DAMAGE_DEALT
        }

        if (damageMessages.isNotEmpty()) {
            // Примерный расчёт: expDelta распределяется между ударами
            // (упрощённо - равномерно, в реальности зависит от урона каждого удара)
            val avgExpPerHit = expDelta / damageMessages.size
            log("Exp gained: $expDelta from ${damageMessages.size} hits (~$avgExpPerHit exp/hit)")

            // Сохраняем статистику для каждого уникального текста удара
            for (msg in damageMessages) {
                database.recordHitExp(msg.rawText, avgExpPerHit)
            }
        }

        // Очищаем буфер после обработки промпта
        recentCombatMessages.clear()
    }

    /**
     * Обработать вход в комнату
     */
    fun onRoomEnter(room: Map<String, Any>) {
        entityTracker.onRoomEnter(room)
        navigator.onRoomEnter(room)
    }

    /**
     * Получить статус бота для API
     */
    fun getStatus(): Map<String, Any> {
        return mapOf(
            "running" to stateMachine.isActive(),
            "state" to stateMachine.currentState.value.name,
            "mode" to config.value.mode.name,
            "session" to (currentSession?.let {
                mapOf(
                    "id" to it.id,
                    "startTime" to it.startTime,
                    "kills" to it.totalKills,
                    "deaths" to it.totalDeaths,
                    "exp" to it.totalExpGained
                )
            } ?: emptyMap<String, Any>()),
            "stateMachine" to stateMachine.getStatus()
        )
    }

    /**
     * Завершить работу бота и освободить ресурсы
     */
    fun shutdown() {
        stop()
        scope.cancel()
        database.close()
    }
}
