package com.bylins.client.bot

import com.bylins.client.bot.combat.CombatManager
import com.bylins.client.bot.navigation.Navigator
import com.bylins.client.bot.perception.EntityTracker
import com.bylins.client.bot.perception.CombatParser
import com.bylins.client.scripting.ScriptEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

/**
 * Ядро AI-бота
 * Координирует все подсистемы: FSM, бой, навигацию, парсинг
 */
private val logger = KotlinLogging.logger("BotCore")

class BotCore(
    private val sendCommand: (String) -> Unit,
    private val echoText: (String) -> Unit,
    private val getMsdpValue: (String) -> Any?,
    private val getCurrentRoom: () -> Map<String, Any>?,
    private val findPath: (String) -> List<String>?,
    private val fireEvent: (ScriptEvent, Any?) -> Unit
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
            zoneId = getCurrentRoom()?.get("zone") as? String
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
     */
    private fun updateCharacterState() {
        val hp = getMsdpValue("HIT")?.toString()?.toIntOrNull() ?: return
        val maxHp = getMsdpValue("MAX_HIT")?.toString()?.toIntOrNull() ?: 1
        val mana = getMsdpValue("MANA")?.toString()?.toIntOrNull() ?: 0
        val maxMana = getMsdpValue("MAX_MANA")?.toString()?.toIntOrNull() ?: 1
        val move = getMsdpValue("MOVE")?.toString()?.toIntOrNull() ?: 0
        val maxMove = getMsdpValue("MAX_MOVE")?.toString()?.toIntOrNull() ?: 1
        val level = getMsdpValue("LEVEL")?.toString()?.toIntOrNull() ?: 1
        val exp = getMsdpValue("EXPERIENCE")?.toString()?.toLongOrNull() ?: 0
        val gold = getMsdpValue("GOLD")?.toString()?.toIntOrNull() ?: 0

        val room = getCurrentRoom()
        val roomId = room?.get("id") as? String
        val zoneId = room?.get("zone") as? String

        // Определяем позицию из MSDP или по косвенным признакам
        val positionStr = getMsdpValue("POSITION")?.toString() ?: "STANDING"
        val position = try {
            Position.valueOf(positionStr.uppercase())
        } catch (e: Exception) {
            Position.STANDING
        }

        val isInCombat = position == Position.FIGHTING || combatManager.isInCombat()

        _characterState.value = CharacterState(
            hp = hp,
            maxHp = maxHp,
            mana = mana,
            maxMana = maxMana,
            move = move,
            maxMove = maxMove,
            level = level,
            experience = exp,
            position = position,
            roomId = roomId,
            zoneId = zoneId,
            isInCombat = isInCombat,
            targetName = combatManager.currentTarget?.name,
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
            fireEvent(ScriptEvent.ON_LOW_HP, charState.hpPercent)
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

        fireEvent(ScriptEvent.ON_PLAYER_DEATH, deathCount)

        stateMachine.transition(BotTransition.COMBAT_LOSE)
    }

    // ============================================
    // Обработчики состояний
    // ============================================

    private fun handleStarting() {
        log("Starting: checking initial conditions")

        val charState = _characterState.value ?: return
        val cfg = config.value

        // Проверяем баффы
        if (cfg.autoBuffs && needsBuffs()) {
            stateMachine.transition(BotTransition.BUFFS_NEEDED)
            return
        }

        // Проверяем HP для отдыха
        if (charState.hpPercent < cfg.restHpPercent) {
            stateMachine.transition(BotTransition.LOW_HP)
            return
        }

        // Ищем путь или переходим к исследованию
        if (navigator.hasTarget() || navigator.findNextTarget()) {
            stateMachine.transition(BotTransition.PATH_FOUND)
        } else {
            // Нет цели - исследуем
            stateMachine.transition(BotTransition.PATH_FOUND)
        }
    }

    private fun handleTraveling() {
        // Проверяем мобов в комнате
        val mobs = entityTracker.getMobsInRoom()
        if (mobs.isNotEmpty() && config.value.autoAttack) {
            val targetMob = combatManager.selectTarget(mobs)
            if (targetMob != null) {
                stateMachine.transition(BotTransition.ENEMY_DETECTED)
                return
            }
        }

        // Проверяем баффы
        if (config.value.autoBuffs && needsBuffs()) {
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
            fireEvent(ScriptEvent.ON_MOB_KILLED, combatManager.lastKilledMob)
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
        if (charState.hpPercent >= cfg.restHpPercent) {
            // Проверяем ману
            if (charState.manaPercent >= cfg.restManaPercent || !cfg.autoBuffs) {
                send("встать")
                stateMachine.transition(BotTransition.HP_RECOVERED)
                return
            }
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
        // Ищем новую цель для исследования
        if (navigator.findNextTarget()) {
            stateMachine.transition(BotTransition.PATH_FOUND)
        } else {
            // Нет куда идти - пробуем случайное направление
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

    private fun hasAffect(affectName: String): Boolean {
        // TODO: Реализовать проверку аффектов через MSDP/парсинг
        return false
    }

    fun send(command: String) {
        log("Sending: $command")
        sendCommand(command)
    }

    fun echo(text: String) {
        echoText(text)
    }

    fun log(message: String) {
        logger.info { message }
        onLog?.invoke(message)
    }

    /**
     * Получить текущую комнату (для подсистем)
     */
    fun getRoom(): Map<String, Any>? = getCurrentRoom()

    /**
     * Обработать входящую строку от сервера
     */
    fun processLine(line: String) {
        // Парсим боевые сообщения
        combatParser.parseLine(line)

        // Обновляем трекер сущностей
        entityTracker.processLine(line)
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
