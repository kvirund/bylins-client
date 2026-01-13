package com.bylins.client.scripting

/**
 * API для скриптов - предоставляет доступ к функциям клиента
 */
interface ScriptAPI {
    // Отправка команд
    fun send(command: String)
    fun echo(text: String)

    // Триггеры
    fun addTrigger(pattern: String, callback: (String, Map<Int, String>) -> Unit): String
    fun removeTrigger(id: String)
    fun enableTrigger(id: String)
    fun disableTrigger(id: String)

    // Алиасы
    fun addAlias(pattern: String, replacement: String): String
    fun removeAlias(id: String)

    // Таймеры
    fun setTimeout(delayMs: Long, callback: () -> Unit): String
    fun setInterval(intervalMs: Long, callback: () -> Unit): String
    fun clearTimer(id: String)

    // Переменные
    fun getVariable(name: String): String?
    fun setVariable(name: String, value: String)
    fun deleteVariable(name: String)
    fun getAllVariables(): Map<String, String>

    // MSDP данные
    fun getMsdpValue(key: String): Any?
    fun getAllMsdpData(): Map<String, Any>

    // GMCP данные
    fun getGmcpValue(packageName: String): String?
    fun getAllGmcpData(): Map<String, String>

    // Маппер - чтение
    fun getCurrentRoom(): Map<String, Any>?
    fun getRoom(roomId: String): Map<String, Any>?
    fun searchRooms(query: String): List<Map<String, Any>>
    fun findPath(targetRoomId: String): List<String>?

    // Маппер - модификация
    fun setRoomNote(roomId: String, note: String)
    fun setRoomColor(roomId: String, color: String?)
    fun setRoomZone(roomId: String, zone: String)
    fun setRoomTags(roomId: String, tags: List<String>)

    // Маппер - создание комнат
    fun createRoom(id: String, name: String): Boolean
    fun createRoomWithExits(id: String, name: String, exits: Map<String, String>): Boolean
    fun linkRooms(fromRoomId: String, direction: String, toRoomId: String)
    fun addUnexploredExits(roomId: String, exits: List<String>)
    fun handleMovement(direction: String, roomName: String, exits: List<String>, roomId: String? = null): Map<String, Any>?

    // Маппер - управление
    fun setMapEnabled(enabled: Boolean)
    fun isMapEnabled(): Boolean
    fun clearMap()
    fun setCurrentRoom(roomId: String)

    // Утилиты
    fun log(message: String)
    fun print(message: String)
}

/**
 * Реализация ScriptAPI
 */
class ScriptAPIImpl(
    private val sendCommand: (String) -> Unit,
    private val echoText: (String) -> Unit,
    private val logMessage: (String) -> Unit,
    private val triggerActions: TriggerActions,
    private val aliasActions: AliasActions,
    private val timerActions: TimerActions,
    private val variableActions: VariableActions,
    private val msdpActions: MsdpActions,
    private val gmcpActions: GmcpActions,
    private val mapperActions: MapperActions
) : ScriptAPI {

    override fun send(command: String) = sendCommand(command)
    override fun echo(text: String) = echoText(text)

    override fun addTrigger(pattern: String, callback: (String, Map<Int, String>) -> Unit): String {
        return triggerActions.addTrigger(pattern, callback)
    }

    override fun removeTrigger(id: String) = triggerActions.removeTrigger(id)
    override fun enableTrigger(id: String) = triggerActions.enableTrigger(id)
    override fun disableTrigger(id: String) = triggerActions.disableTrigger(id)

    override fun addAlias(pattern: String, replacement: String): String {
        return aliasActions.addAlias(pattern, replacement)
    }

    override fun removeAlias(id: String) = aliasActions.removeAlias(id)

    override fun setTimeout(delayMs: Long, callback: () -> Unit): String {
        return timerActions.setTimeout(delayMs, callback)
    }

    override fun setInterval(intervalMs: Long, callback: () -> Unit): String {
        return timerActions.setInterval(intervalMs, callback)
    }

    override fun clearTimer(id: String) = timerActions.clearTimer(id)

    override fun getVariable(name: String): String? = variableActions.getVariable(name)
    override fun setVariable(name: String, value: String) = variableActions.setVariable(name, value)
    override fun deleteVariable(name: String) = variableActions.deleteVariable(name)
    override fun getAllVariables(): Map<String, String> = variableActions.getAllVariables()

    override fun getMsdpValue(key: String): Any? = msdpActions.getMsdpValue(key)
    override fun getAllMsdpData(): Map<String, Any> = msdpActions.getAllMsdpData()

    override fun getGmcpValue(packageName: String): String? = gmcpActions.getGmcpValue(packageName)
    override fun getAllGmcpData(): Map<String, String> = gmcpActions.getAllGmcpData()

    override fun getCurrentRoom(): Map<String, Any>? = mapperActions.getCurrentRoom()
    override fun getRoom(roomId: String): Map<String, Any>? = mapperActions.getRoom(roomId)
    override fun searchRooms(query: String): List<Map<String, Any>> = mapperActions.searchRooms(query)
    override fun findPath(targetRoomId: String): List<String>? = mapperActions.findPath(targetRoomId)

    override fun setRoomNote(roomId: String, note: String) = mapperActions.setRoomNote(roomId, note)
    override fun setRoomColor(roomId: String, color: String?) = mapperActions.setRoomColor(roomId, color)
    override fun setRoomZone(roomId: String, zone: String) = mapperActions.setRoomZone(roomId, zone)
    override fun setRoomTags(roomId: String, tags: List<String>) = mapperActions.setRoomTags(roomId, tags)

    override fun createRoom(id: String, name: String): Boolean =
        mapperActions.createRoom(id, name)
    override fun createRoomWithExits(id: String, name: String, exits: Map<String, String>): Boolean =
        mapperActions.createRoomWithExits(id, name, exits)
    override fun linkRooms(fromRoomId: String, direction: String, toRoomId: String) =
        mapperActions.linkRooms(fromRoomId, direction, toRoomId)
    override fun addUnexploredExits(roomId: String, exits: List<String>) =
        mapperActions.addUnexploredExits(roomId, exits)
    override fun handleMovement(direction: String, roomName: String, exits: List<String>, roomId: String?): Map<String, Any>? =
        mapperActions.handleMovement(direction, roomName, exits, roomId)

    override fun setMapEnabled(enabled: Boolean) = mapperActions.setMapEnabled(enabled)
    override fun isMapEnabled(): Boolean = mapperActions.isMapEnabled()
    override fun clearMap() = mapperActions.clearMap()
    override fun setCurrentRoom(roomId: String) = mapperActions.setCurrentRoom(roomId)

    override fun log(message: String) = logMessage("[SCRIPT] $message")
    override fun print(message: String) = echoText(message)
}

// Интерфейсы для действий
interface TriggerActions {
    fun addTrigger(pattern: String, callback: (String, Map<Int, String>) -> Unit): String
    fun removeTrigger(id: String)
    fun enableTrigger(id: String)
    fun disableTrigger(id: String)
}

interface AliasActions {
    fun addAlias(pattern: String, replacement: String): String
    fun removeAlias(id: String)
}

interface TimerActions {
    fun setTimeout(delayMs: Long, callback: () -> Unit): String
    fun setInterval(intervalMs: Long, callback: () -> Unit): String
    fun clearTimer(id: String)
}

interface VariableActions {
    fun getVariable(name: String): String?
    fun setVariable(name: String, value: String)
    fun deleteVariable(name: String)
    fun getAllVariables(): Map<String, String>
}

interface MsdpActions {
    fun getMsdpValue(key: String): Any?
    fun getAllMsdpData(): Map<String, Any>
}

interface GmcpActions {
    fun getGmcpValue(packageName: String): String?
    fun getAllGmcpData(): Map<String, String>
}

interface MapperActions {
    // Чтение
    fun getCurrentRoom(): Map<String, Any>?
    fun getRoom(roomId: String): Map<String, Any>?
    fun searchRooms(query: String): List<Map<String, Any>>
    fun findPath(targetRoomId: String): List<String>?

    // Модификация
    fun setRoomNote(roomId: String, note: String)
    fun setRoomColor(roomId: String, color: String?)
    fun setRoomZone(roomId: String, zone: String)
    fun setRoomTags(roomId: String, tags: List<String>)

    // Создание
    fun createRoom(id: String, name: String): Boolean
    fun createRoomWithExits(id: String, name: String, exits: Map<String, String>): Boolean
    fun linkRooms(fromRoomId: String, direction: String, toRoomId: String)
    fun addUnexploredExits(roomId: String, exits: List<String>)
    fun handleMovement(direction: String, roomName: String, exits: List<String>, roomId: String? = null): Map<String, Any>?

    // Управление
    fun setMapEnabled(enabled: Boolean)
    fun isMapEnabled(): Boolean
    fun clearMap()
    fun setCurrentRoom(roomId: String)
}
