package com.bylins.client.bot

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mu.KotlinLogging

/**
 * Состояния конечного автомата бота
 */
enum class BotStateType {
    IDLE,           // Ожидание (бот не активен)
    STARTING,       // Запуск
    TRAVELING,      // Перемещение к цели
    COMBAT,         // В бою
    LOOTING,        // Лутание
    RESTING,        // Отдых (восстановление HP/маны)
    BUFFING,        // Наложение баффов
    FLEEING,        // Бегство
    EXPLORING,      // Исследование
    RETURNING,      // Возврат в безопасную зону
    ERROR,          // Ошибка
    STOPPING        // Остановка
}

/**
 * Переходы между состояниями
 */
enum class BotTransition {
    START,              // Запуск бота
    STOP,               // Остановка бота
    ARRIVE,             // Прибытие в комнату
    ENEMY_DETECTED,     // Обнаружен враг
    COMBAT_START,       // Начало боя
    COMBAT_WIN,         // Победа в бою
    COMBAT_LOSE,        // Поражение (смерть)
    LOW_HP,             // Низкий HP
    HP_RECOVERED,       // HP восстановлен
    MANA_RECOVERED,     // Мана восстановлена
    BUFFS_NEEDED,       // Нужны баффы
    BUFFS_APPLIED,      // Баффы наложены
    LOOT_AVAILABLE,     // Есть лут
    LOOT_DONE,          // Лут собран
    PATH_FOUND,         // Найден путь
    PATH_BLOCKED,       // Путь заблокирован
    NO_PATH,            // Путь не найден, нужно исследовать
    ZONE_COMPLETE,      // Зона пройдена
    ERROR_OCCURRED,     // Произошла ошибка
    RECOVERED           // Восстановление после ошибки
}

private val logger = KotlinLogging.logger("BotState")

/**
 * Машина состояний бота
 */
class BotStateMachine {
    private val _currentState = MutableStateFlow(BotStateType.IDLE)
    val currentState: StateFlow<BotStateType> = _currentState

    private val _previousState = MutableStateFlow(BotStateType.IDLE)
    val previousState: StateFlow<BotStateType> = _previousState

    // Время входа в текущее состояние
    private var stateEntryTime = System.currentTimeMillis()

    // Счётчик переходов (для отладки)
    private var transitionCount = 0

    // Слушатели изменения состояния
    private val stateListeners = mutableListOf<(BotStateType, BotStateType, BotTransition) -> Unit>()

    /**
     * Таблица переходов состояний
     * (текущее состояние, переход) -> новое состояние
     */
    private val transitionTable: Map<Pair<BotStateType, BotTransition>, BotStateType> = mapOf(
        // Из IDLE
        (BotStateType.IDLE to BotTransition.START) to BotStateType.STARTING,

        // Из STARTING
        (BotStateType.STARTING to BotTransition.BUFFS_NEEDED) to BotStateType.BUFFING,
        (BotStateType.STARTING to BotTransition.PATH_FOUND) to BotStateType.TRAVELING,
        (BotStateType.STARTING to BotTransition.NO_PATH) to BotStateType.EXPLORING,
        (BotStateType.STARTING to BotTransition.LOW_HP) to BotStateType.RESTING,
        (BotStateType.STARTING to BotTransition.ENEMY_DETECTED) to BotStateType.COMBAT,
        (BotStateType.STARTING to BotTransition.ERROR_OCCURRED) to BotStateType.ERROR,

        // Из TRAVELING
        (BotStateType.TRAVELING to BotTransition.ARRIVE) to BotStateType.TRAVELING,
        (BotStateType.TRAVELING to BotTransition.ENEMY_DETECTED) to BotStateType.COMBAT,
        (BotStateType.TRAVELING to BotTransition.COMBAT_START) to BotStateType.COMBAT,
        (BotStateType.TRAVELING to BotTransition.LOW_HP) to BotStateType.FLEEING,
        (BotStateType.TRAVELING to BotTransition.BUFFS_NEEDED) to BotStateType.BUFFING,
        (BotStateType.TRAVELING to BotTransition.ZONE_COMPLETE) to BotStateType.EXPLORING,
        (BotStateType.TRAVELING to BotTransition.PATH_BLOCKED) to BotStateType.EXPLORING,
        (BotStateType.TRAVELING to BotTransition.STOP) to BotStateType.STOPPING,
        (BotStateType.TRAVELING to BotTransition.ERROR_OCCURRED) to BotStateType.ERROR,

        // Из COMBAT
        (BotStateType.COMBAT to BotTransition.COMBAT_WIN) to BotStateType.LOOTING,
        (BotStateType.COMBAT to BotTransition.COMBAT_LOSE) to BotStateType.RETURNING,
        (BotStateType.COMBAT to BotTransition.LOW_HP) to BotStateType.FLEEING,
        (BotStateType.COMBAT to BotTransition.STOP) to BotStateType.STOPPING,
        (BotStateType.COMBAT to BotTransition.ERROR_OCCURRED) to BotStateType.ERROR,

        // Из LOOTING
        (BotStateType.LOOTING to BotTransition.LOOT_DONE) to BotStateType.TRAVELING,
        (BotStateType.LOOTING to BotTransition.ENEMY_DETECTED) to BotStateType.COMBAT,
        (BotStateType.LOOTING to BotTransition.LOW_HP) to BotStateType.RESTING,
        (BotStateType.LOOTING to BotTransition.BUFFS_NEEDED) to BotStateType.BUFFING,
        (BotStateType.LOOTING to BotTransition.STOP) to BotStateType.STOPPING,

        // Из RESTING
        (BotStateType.RESTING to BotTransition.HP_RECOVERED) to BotStateType.TRAVELING,
        (BotStateType.RESTING to BotTransition.MANA_RECOVERED) to BotStateType.TRAVELING,
        (BotStateType.RESTING to BotTransition.ENEMY_DETECTED) to BotStateType.COMBAT,
        (BotStateType.RESTING to BotTransition.COMBAT_START) to BotStateType.COMBAT,
        (BotStateType.RESTING to BotTransition.BUFFS_NEEDED) to BotStateType.BUFFING,
        (BotStateType.RESTING to BotTransition.STOP) to BotStateType.STOPPING,

        // Из BUFFING
        (BotStateType.BUFFING to BotTransition.BUFFS_APPLIED) to BotStateType.TRAVELING,
        (BotStateType.BUFFING to BotTransition.ENEMY_DETECTED) to BotStateType.COMBAT,
        (BotStateType.BUFFING to BotTransition.COMBAT_START) to BotStateType.COMBAT,
        (BotStateType.BUFFING to BotTransition.LOW_HP) to BotStateType.RESTING,
        (BotStateType.BUFFING to BotTransition.STOP) to BotStateType.STOPPING,

        // Из FLEEING
        (BotStateType.FLEEING to BotTransition.ARRIVE) to BotStateType.RESTING,
        (BotStateType.FLEEING to BotTransition.HP_RECOVERED) to BotStateType.TRAVELING,
        (BotStateType.FLEEING to BotTransition.COMBAT_START) to BotStateType.COMBAT,
        (BotStateType.FLEEING to BotTransition.COMBAT_LOSE) to BotStateType.RETURNING,
        (BotStateType.FLEEING to BotTransition.STOP) to BotStateType.STOPPING,
        (BotStateType.FLEEING to BotTransition.ERROR_OCCURRED) to BotStateType.ERROR,

        // Из EXPLORING
        (BotStateType.EXPLORING to BotTransition.PATH_FOUND) to BotStateType.TRAVELING,
        (BotStateType.EXPLORING to BotTransition.ENEMY_DETECTED) to BotStateType.COMBAT,
        (BotStateType.EXPLORING to BotTransition.LOW_HP) to BotStateType.RESTING,
        (BotStateType.EXPLORING to BotTransition.BUFFS_NEEDED) to BotStateType.BUFFING,
        (BotStateType.EXPLORING to BotTransition.STOP) to BotStateType.STOPPING,
        (BotStateType.EXPLORING to BotTransition.ERROR_OCCURRED) to BotStateType.ERROR,

        // Из RETURNING
        (BotStateType.RETURNING to BotTransition.ARRIVE) to BotStateType.RESTING,
        (BotStateType.RETURNING to BotTransition.HP_RECOVERED) to BotStateType.TRAVELING,
        (BotStateType.RETURNING to BotTransition.STOP) to BotStateType.STOPPING,

        // Из ERROR
        (BotStateType.ERROR to BotTransition.RECOVERED) to BotStateType.IDLE,
        (BotStateType.ERROR to BotTransition.STOP) to BotStateType.STOPPING,
        (BotStateType.ERROR to BotTransition.START) to BotStateType.STARTING,

        // Из STOPPING
        (BotStateType.STOPPING to BotTransition.STOP) to BotStateType.IDLE
    )

    /**
     * Выполнить переход
     * @return true если переход успешен
     */
    fun transition(event: BotTransition): Boolean {
        val current = _currentState.value
        val newState = transitionTable[current to event]

        if (newState != null) {
            _previousState.value = current
            _currentState.value = newState
            stateEntryTime = System.currentTimeMillis()
            transitionCount++

            logger.info { "Bot state transition: $current --[$event]--> $newState (transition #$transitionCount)" }

            // Уведомляем слушателей
            stateListeners.forEach { listener ->
                try {
                    listener(current, newState, event)
                } catch (e: Exception) {
                    logger.error { "Error in state listener: ${e.message}" }
                }
            }

            return true
        } else {
            logger.warn { "Invalid transition: $current --[$event]--> ??? (transition ignored)" }
            return false
        }
    }

    /**
     * Принудительно установить состояние (для восстановления)
     */
    fun forceState(state: BotStateType) {
        _previousState.value = _currentState.value
        _currentState.value = state
        stateEntryTime = System.currentTimeMillis()
        logger.warn { "Bot state forced to: $state" }
    }

    /**
     * Сбросить в начальное состояние
     */
    fun reset() {
        _previousState.value = _currentState.value
        _currentState.value = BotStateType.IDLE
        stateEntryTime = System.currentTimeMillis()
        transitionCount = 0
        logger.info { "Bot state machine reset to IDLE" }
    }

    /**
     * Время в текущем состоянии (миллисекунды)
     */
    fun getTimeInCurrentState(): Long {
        return System.currentTimeMillis() - stateEntryTime
    }

    /**
     * Добавить слушателя изменения состояния
     */
    fun addStateListener(listener: (oldState: BotStateType, newState: BotStateType, transition: BotTransition) -> Unit) {
        stateListeners.add(listener)
    }

    /**
     * Удалить слушателя
     */
    fun removeStateListener(listener: (BotStateType, BotStateType, BotTransition) -> Unit) {
        stateListeners.remove(listener)
    }

    /**
     * Проверить, активен ли бот
     */
    fun isActive(): Boolean {
        return _currentState.value !in listOf(BotStateType.IDLE, BotStateType.ERROR, BotStateType.STOPPING)
    }

    /**
     * Проверить, в бою ли бот
     */
    fun isInCombat(): Boolean {
        return _currentState.value == BotStateType.COMBAT
    }

    /**
     * Проверить, отдыхает ли бот
     */
    fun isResting(): Boolean {
        return _currentState.value == BotStateType.RESTING
    }

    /**
     * Проверить, убегает ли бот
     */
    fun isFleeing(): Boolean {
        return _currentState.value == BotStateType.FLEEING
    }

    /**
     * Получить статус в виде Map для API
     */
    fun getStatus(): Map<String, Any> {
        return mapOf(
            "currentState" to _currentState.value.name,
            "previousState" to _previousState.value.name,
            "timeInState" to getTimeInCurrentState(),
            "transitionCount" to transitionCount,
            "isActive" to isActive(),
            "isInCombat" to isInCombat(),
            "isResting" to isResting(),
            "isFleeing" to isFleeing()
        )
    }
}
