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
    fun getMsdpReportableVariables(): List<String>
    fun getMsdpReportedVariables(): List<String>
    fun isMsdpEnabled(): Boolean
    fun msdpReport(variableName: String)
    fun msdpUnreport(variableName: String)
    fun msdpSend(variableName: String)
    fun msdpList(listType: String)

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

    // Маппер - высокоуровневые функции для MSDP
    fun handleRoom(params: Map<String, Any>): Map<String, Any>?

    // Маппер - управление
    fun setMapEnabled(enabled: Boolean)
    fun isMapEnabled(): Boolean
    fun clearMap()
    fun setCurrentRoom(roomId: String)

    // Статус-панель
    fun statusAddBar(id: String, label: String, value: Int, max: Int, color: String = "green", showText: Boolean = true, order: Int = -1)
    fun statusAddText(id: String, label: String, value: String, order: Int = -1)
    fun statusAddFlags(id: String, label: String, flags: List<Map<String, Any>>, order: Int = -1)
    fun statusAddMiniMap(id: String, currentRoomId: String? = null, visible: Boolean = true, order: Int = -1)
    fun statusUpdate(id: String, updates: Map<String, Any>)
    fun statusRemove(id: String)
    fun statusClear()
    fun statusGet(id: String): Map<String, Any>?
    fun statusExists(id: String): Boolean

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
    private val mapperActions: MapperActions,
    private val statusActions: StatusActions
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
    override fun getMsdpReportableVariables(): List<String> = msdpActions.getReportableVariables()
    override fun getMsdpReportedVariables(): List<String> = msdpActions.getReportedVariables()
    override fun isMsdpEnabled(): Boolean = msdpActions.isEnabled()
    override fun msdpReport(variableName: String) = msdpActions.report(variableName)
    override fun msdpUnreport(variableName: String) = msdpActions.unreport(variableName)
    override fun msdpSend(variableName: String) = msdpActions.send(variableName)
    override fun msdpList(listType: String) = msdpActions.list(listType)

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

    override fun handleRoom(params: Map<String, Any>): Map<String, Any>? =
        mapperActions.handleRoom(params)

    override fun setMapEnabled(enabled: Boolean) = mapperActions.setMapEnabled(enabled)
    override fun isMapEnabled(): Boolean = mapperActions.isMapEnabled()
    override fun clearMap() = mapperActions.clearMap()
    override fun setCurrentRoom(roomId: String) = mapperActions.setCurrentRoom(roomId)

    override fun statusAddBar(id: String, label: String, value: Int, max: Int, color: String, showText: Boolean, order: Int) =
        statusActions.addBar(id, label, value, max, color, showText, order)
    override fun statusAddText(id: String, label: String, value: String, order: Int) =
        statusActions.addText(id, label, value, order)
    override fun statusAddFlags(id: String, label: String, flags: List<Map<String, Any>>, order: Int) =
        statusActions.addFlags(id, label, flags, order)
    override fun statusAddMiniMap(id: String, currentRoomId: String?, visible: Boolean, order: Int) =
        statusActions.addMiniMap(id, currentRoomId, visible, order)
    override fun statusUpdate(id: String, updates: Map<String, Any>) =
        statusActions.update(id, updates)
    override fun statusRemove(id: String) = statusActions.remove(id)
    override fun statusClear() = statusActions.clear()
    override fun statusGet(id: String): Map<String, Any>? = statusActions.get(id)
    override fun statusExists(id: String): Boolean = statusActions.exists(id)

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
    fun getReportableVariables(): List<String>
    fun getReportedVariables(): List<String>
    fun isEnabled(): Boolean
    fun report(variableName: String)
    fun unreport(variableName: String)
    fun send(variableName: String)
    fun list(listType: String)
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

    // Высокоуровневые функции для MSDP
    fun handleRoom(params: Map<String, Any>): Map<String, Any>?

    // Управление
    fun setMapEnabled(enabled: Boolean)
    fun isMapEnabled(): Boolean
    fun clearMap()
    fun setCurrentRoom(roomId: String)
}

interface StatusActions {
    fun addBar(id: String, label: String, value: Int, max: Int, color: String, showText: Boolean, order: Int)
    fun addText(id: String, label: String, value: String, order: Int)
    fun addFlags(id: String, label: String, flags: List<Map<String, Any>>, order: Int)
    fun addMiniMap(id: String, currentRoomId: String?, visible: Boolean, order: Int)
    fun update(id: String, updates: Map<String, Any>)
    fun remove(id: String)
    fun clear()
    fun get(id: String): Map<String, Any>?
    fun exists(id: String): Boolean
}
