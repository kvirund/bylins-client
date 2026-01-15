package com.bylins.client.contextcommands

import androidx.compose.ui.input.key.Key
import com.bylins.client.mapper.Room
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mu.KotlinLogging

private val logger = KotlinLogging.logger("ContextCommandManager")

/**
 * Менеджер контекстных команд
 * Управляет правилами и очередью активных контекстных команд
 */
class ContextCommandManager(
    private val onCommand: (String) -> Unit,
    private val getCurrentRoom: () -> Room?
) {
    // Правила для автоматического добавления команд
    private val _rules = MutableStateFlow<List<ContextCommandRule>>(emptyList())
    val rules: StateFlow<List<ContextCommandRule>> = _rules

    // Очередь активных команд (newest = last in list)
    private val _commandQueue = MutableStateFlow<List<ContextCommand>>(emptyList())
    val commandQueue: StateFlow<List<ContextCommand>> = _commandQueue

    // Максимальный размер очереди
    private val _maxQueueSize = MutableStateFlow(50)
    val maxQueueSize: StateFlow<Int> = _maxQueueSize

    // Отслеживание текущего контекста
    private var lastRoomId: String? = null
    private var lastZone: String? = null

    // === Управление правилами ===

    fun addRule(rule: ContextCommandRule) {
        _rules.value = _rules.value + rule
        logger.info { "Added context command rule: ${rule.command} (trigger=${rule.triggerType}, scope=${rule.scope})" }
    }

    fun removeRule(id: String) {
        _rules.value = _rules.value.filter { it.id != id }
        logger.info { "Removed context command rule: $id" }
    }

    fun updateRule(id: String, updater: (ContextCommandRule) -> ContextCommandRule) {
        _rules.value = _rules.value.map { if (it.id == id) updater(it) else it }
    }

    fun loadRules(rules: List<ContextCommandRule>) {
        _rules.value = rules
        logger.info { "Loaded ${rules.size} context command rules" }
    }

    fun setMaxQueueSize(size: Int) {
        _maxQueueSize.value = size.coerceIn(10, 200)
        evictOldCommands()
    }

    // === Операции с очередью ===

    /**
     * Добавляет команду в очередь
     * Дубликаты (по command string) удаляются
     * Новая команда становится самой свежей (в конце списка)
     */
    fun addCommand(command: ContextCommand) {
        // Удаляем дубликат если есть
        val filtered = _commandQueue.value.filter { it.command != command.command }
        _commandQueue.value = filtered + command
        logger.debug { "Added context command: ${command.command}" }
        evictOldCommands()
    }

    /**
     * Добавляет команду вручную (без правила)
     */
    fun addManualCommand(
        command: String,
        ttl: ContextCommandTTL = ContextCommandTTL.UntilRoomChange,
        description: String = ""
    ) {
        val room = getCurrentRoom()
        addCommand(ContextCommand(
            command = command,
            source = ContextCommandSource.Manual(description),
            ttl = ttl,
            roomIdWhenAdded = room?.id,
            zoneWhenAdded = room?.zone
        ))
    }

    fun removeCommand(id: String) {
        _commandQueue.value = _commandQueue.value.filter { it.id != id }
    }

    fun clearQueue() {
        _commandQueue.value = emptyList()
        logger.info { "Context command queue cleared" }
    }

    /**
     * Получает команду по индексу (0 = самая свежая)
     */
    fun getCommand(index: Int): ContextCommand? {
        val queue = _commandQueue.value
        if (index < 0 || index >= queue.size) return null
        // Свежие в конце, поэтому индекс 0 = последний элемент
        return queue.getOrNull(queue.size - 1 - index)
    }

    /**
     * Выполняет команду по индексу (0 = самая свежая)
     */
    fun executeCommand(index: Int) {
        val command = getCommand(index)
        if (command != null) {
            logger.info { "Executing context command [$index]: ${command.command}" }
            onCommand(command.command)
            // Удаляем только одноразовые команды (TTL = OneTime)
            if (command.ttl is ContextCommandTTL.OneTime) {
                removeCommand(command.id)
            }
        } else {
            logger.debug { "No context command at index $index" }
        }
    }

    // === Обработка событий ===

    /**
     * Обрабатывает строку от сервера - проверяет паттерн-правила (базовые)
     */
    fun processLine(line: String) {
        processLineWithRules(line, _rules.value)
    }

    /**
     * Обрабатывает строку от сервера с указанными правилами
     */
    fun processLineWithRules(line: String, rules: List<ContextCommandRule>) {
        val room = getCurrentRoom()
        val enabledRules = rules
            .filter { it.enabled }
            .sortedByDescending { it.priority }

        for (rule in enabledRules) {
            // Только Pattern правила обрабатываются здесь
            val triggerType = rule.triggerType
            if (triggerType is ContextTriggerType.Pattern) {
                // Проверяем scope
                val inScope = when (val scope = rule.scope) {
                    is ContextScope.World -> true
                    is ContextScope.Room -> room != null && scope.matches(room.id, room.tags)
                    is ContextScope.Zone -> room?.zone in scope.zones
                }
                if (!inScope) continue

                val matchResult = triggerType.regex.find(line)
                if (matchResult != null) {
                    val command = rule.applyGroups(matchResult)
                    addCommand(ContextCommand(
                        command = command,
                        source = ContextCommandSource.Pattern(rule.id, matchResult.value),
                        ttl = rule.ttl,
                        roomIdWhenAdded = room?.id,
                        zoneWhenAdded = room?.zone
                    ))
                    logger.debug { "Pattern rule '${rule.command}' matched: $command" }
                }
            }
        }
    }

    /**
     * Вызывается при входе в комнату (базовые правила)
     * - Проверяет room/zone правила
     * - Истекает TTL для команд
     */
    fun onRoomEnter(room: Room) {
        onRoomEnterInternal(room, expireTtl = true)
        processRoomRules(room, _rules.value)
    }

    /**
     * Обрабатывает правила для указанного списка правил при входе в комнату
     * Permanent правила добавляют команды при входе в scope
     */
    fun processRoomRules(room: Room, rules: List<ContextCommandRule>) {
        val enabledRules = rules
            .filter { it.enabled }
            .sortedByDescending { it.priority }

        for (rule in enabledRules) {
            // Только Permanent правила обрабатываются здесь
            if (rule.triggerType !is ContextTriggerType.Permanent) continue

            // Проверяем scope
            val inScope = when (val scope = rule.scope) {
                is ContextScope.Room -> scope.matches(room.id, room.tags)
                is ContextScope.Zone -> room.zone != null && room.zone in scope.zones
                is ContextScope.World -> true  // Permanent + World = всегда активно
            }

            if (inScope) {
                // TTL для Permanent правил определяется scope:
                // - Room scope -> UntilRoomChange
                // - Zone scope -> UntilZoneChange
                // - World scope -> Permanent
                val ttl = when (rule.scope) {
                    is ContextScope.Room -> ContextCommandTTL.UntilRoomChange
                    is ContextScope.Zone -> ContextCommandTTL.UntilZoneChange
                    is ContextScope.World -> ContextCommandTTL.Permanent
                }

                val source = when (rule.scope) {
                    is ContextScope.Room -> ContextCommandSource.RoomBased(rule.id, room.id)
                    is ContextScope.Zone -> ContextCommandSource.ZoneBased(rule.id, room.zone ?: "")
                    is ContextScope.World -> ContextCommandSource.Manual("permanent")
                }

                addCommand(ContextCommand(
                    command = rule.command,
                    source = source,
                    ttl = ttl,
                    roomIdWhenAdded = room.id,
                    zoneWhenAdded = room.zone
                ))
                logger.debug { "Permanent rule '${rule.command}' activated in ${rule.scope}" }
            }
        }
    }

    /**
     * Внутренний метод для обновления контекста комнаты и TTL
     */
    private fun onRoomEnterInternal(room: Room, expireTtl: Boolean) {
        val oldRoomId = lastRoomId
        val oldZone = lastZone
        lastRoomId = room.id
        lastZone = room.zone

        if (expireTtl) {
            // Истекаем команды по TTL
            if (oldRoomId != null && oldRoomId != room.id) {
                expireByRoomChange(room.id)
            }
            if (oldZone != null && oldZone != room.zone) {
                expireByZoneChange(room.zone)
            }
        }
    }

    /**
     * Проверяет истечение TTL по времени (вызывать периодически)
     */
    fun expireByTime() {
        val now = System.currentTimeMillis()
        _commandQueue.value = _commandQueue.value.filter { cmd ->
            when (val ttl = cmd.ttl) {
                is ContextCommandTTL.FixedTime -> {
                    val expireAt = cmd.addedAt + ttl.minutes * 60 * 1000L
                    if (now >= expireAt) {
                        logger.debug { "Command expired by time: ${cmd.command}" }
                        false
                    } else {
                        true
                    }
                }
                else -> true
            }
        }
    }

    // === Хоткеи Alt+1-0 ===

    /**
     * Проверяет и обрабатывает зарезервированные хоткеи Alt+1-0
     * Возвращает true если хоткей был обработан
     */
    fun processReservedHotkey(key: Key, isAltPressed: Boolean): Boolean {
        if (!isAltPressed) return false

        val index = when (key) {
            Key.One -> 0
            Key.Two -> 1
            Key.Three -> 2
            Key.Four -> 3
            Key.Five -> 4
            Key.Six -> 5
            Key.Seven -> 6
            Key.Eight -> 7
            Key.Nine -> 8
            Key.Zero -> 9
            else -> return false
        }

        // Проверяем есть ли команда с этим индексом
        val command = getCommand(index)
        if (command != null) {
            executeCommand(index)
            return true
        }

        return false
    }

    /**
     * Проверяет является ли комбинация зарезервированной для контекстных команд
     */
    fun isReservedHotkey(key: Key, isAltPressed: Boolean, isCtrlPressed: Boolean, isShiftPressed: Boolean): Boolean {
        if (!isAltPressed || isCtrlPressed || isShiftPressed) return false
        return key in listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five,
                             Key.Six, Key.Seven, Key.Eight, Key.Nine, Key.Zero)
    }

    // === Внутренние методы ===

    private fun expireByRoomChange(newRoomId: String) {
        _commandQueue.value = _commandQueue.value.filter { cmd ->
            when (cmd.ttl) {
                is ContextCommandTTL.UntilRoomChange -> {
                    if (cmd.roomIdWhenAdded != newRoomId) {
                        logger.debug { "Command expired by room change: ${cmd.command}" }
                        false
                    } else {
                        true
                    }
                }
                else -> true
            }
        }
    }

    private fun expireByZoneChange(newZone: String?) {
        _commandQueue.value = _commandQueue.value.filter { cmd ->
            when (cmd.ttl) {
                is ContextCommandTTL.UntilZoneChange -> {
                    if (cmd.zoneWhenAdded != newZone) {
                        logger.debug { "Command expired by zone change: ${cmd.command}" }
                        false
                    } else {
                        true
                    }
                }
                else -> true
            }
        }
    }

    private fun evictOldCommands() {
        val maxSize = _maxQueueSize.value
        val queue = _commandQueue.value
        if (queue.size > maxSize) {
            // Удаляем самые старые (с начала списка)
            val toRemove = queue.size - maxSize
            _commandQueue.value = queue.drop(toRemove)
            logger.debug { "Evicted $toRemove old context commands" }
        }
    }
}
