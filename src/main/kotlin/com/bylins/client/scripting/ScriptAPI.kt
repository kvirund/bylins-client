package com.bylins.client.scripting

/**
 * Утилита для автоматической конвертации объектов скриптов в Java типы.
 * Поддерживает: Python (Jython), Lua (LuaJ), JavaScript (Nashorn/GraalJS)
 */
object ScriptObjectConverter {
    /**
     * Конвертирует объект скрипта в Java/Kotlin тип
     */
    @Suppress("UNCHECKED_CAST")
    fun toJava(obj: Any?): Any? {
        if (obj == null) return null

        val className = obj.javaClass.name

        return when {
            // Примитивы и строки - возвращаем как есть
            obj is String || obj is Number || obj is Boolean -> obj

            // Python (Jython) типы
            className.startsWith("org.python.core.Py") -> convertPythonObject(obj)

            // Lua (LuaJ) типы
            className.startsWith("org.luaj.vm2.") -> convertLuaObject(obj)

            // JavaScript типы (Nashorn)
            className.contains("ScriptObject") || className.contains("NativeObject") -> convertJsObject(obj)

            // Java Map - рекурсивно конвертируем значения
            obj is Map<*, *> -> obj.entries.associate { (k, v) ->
                k.toString() to toJava(v)
            }

            // Java List - рекурсивно конвертируем элементы
            obj is List<*> -> obj.map { toJava(it) }

            // Остальное - toString
            else -> obj.toString()
        }
    }

    /**
     * Конвертирует объект скрипта в Map<String, Any>
     */
    @Suppress("UNCHECKED_CAST")
    fun toMap(obj: Any?): Map<String, Any> {
        val converted = toJava(obj)
        return when (converted) {
            is Map<*, *> -> converted as Map<String, Any>
            else -> emptyMap()
        }
    }

    /**
     * Конвертирует объект скрипта в List<T>
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> toList(obj: Any?): List<T> {
        val converted = toJava(obj)
        return when (converted) {
            is List<*> -> converted as List<T>
            else -> emptyList()
        }
    }

    private fun convertPythonObject(obj: Any): Any? {
        val className = obj.javaClass.simpleName
        return when (className) {
            "PyNone" -> null
            "PyUnicode", "PyString" -> {
                // Используем рефлексию чтобы не зависеть от Jython в compile time
                try {
                    val method = obj.javaClass.getMethod("getString")
                    method.invoke(obj) as? String ?: obj.toString()
                } catch (e: Exception) {
                    obj.toString()
                }
            }
            "PyInteger", "PyLong" -> {
                try {
                    val method = obj.javaClass.getMethod("getValue")
                    method.invoke(obj)
                } catch (e: Exception) {
                    try {
                        val method = obj.javaClass.getMethod("asInt")
                        method.invoke(obj)
                    } catch (e2: Exception) {
                        obj.toString().toLongOrNull() ?: obj.toString()
                    }
                }
            }
            "PyFloat" -> {
                try {
                    val method = obj.javaClass.getMethod("getValue")
                    method.invoke(obj)
                } catch (e: Exception) {
                    obj.toString().toDoubleOrNull() ?: obj.toString()
                }
            }
            "PyBoolean" -> {
                try {
                    val method = obj.javaClass.getMethod("getBooleanValue")
                    method.invoke(obj)
                } catch (e: Exception) {
                    obj.toString() == "True"
                }
            }
            "PyDictionary" -> {
                try {
                    val result = mutableMapOf<String, Any?>()
                    val itemsMethod = obj.javaClass.getMethod("items")
                    val items = itemsMethod.invoke(obj) as? Iterable<*>
                    items?.forEach { item ->
                        if (item != null) {
                            val getItemMethod = item.javaClass.getMethod("__getitem__", Int::class.java)
                            val key = getItemMethod.invoke(item, 0)
                            val value = getItemMethod.invoke(item, 1)
                            val keyStr = toJava(key)?.toString() ?: return@forEach
                            result[keyStr] = toJava(value)
                        }
                    }
                    result
                } catch (e: Exception) {
                    // Fallback: пробуем через Map интерфейс
                    if (obj is Map<*, *>) {
                        obj.entries.associate { (k, v) -> k.toString() to toJava(v) }
                    } else {
                        emptyMap<String, Any>()
                    }
                }
            }
            "PyList", "PyTuple" -> {
                try {
                    val result = mutableListOf<Any?>()
                    val sizeMethod = obj.javaClass.getMethod("__len__")
                    val size = sizeMethod.invoke(obj) as Int
                    val getItemMethod = obj.javaClass.getMethod("__getitem__", Int::class.java)
                    for (i in 0 until size) {
                        result.add(toJava(getItemMethod.invoke(obj, i)))
                    }
                    result
                } catch (e: Exception) {
                    if (obj is List<*>) {
                        obj.map { toJava(it) }
                    } else {
                        emptyList<Any>()
                    }
                }
            }
            else -> obj.toString()
        }
    }

    private fun convertLuaObject(obj: Any): Any? {
        val className = obj.javaClass.simpleName
        return when (className) {
            "LuaNil" -> null
            "LuaString" -> {
                try {
                    val method = obj.javaClass.getMethod("tojstring")
                    method.invoke(obj) as? String ?: obj.toString()
                } catch (e: Exception) {
                    obj.toString()
                }
            }
            "LuaInteger" -> {
                try {
                    val method = obj.javaClass.getMethod("toint")
                    method.invoke(obj)
                } catch (e: Exception) {
                    obj.toString().toIntOrNull() ?: obj.toString()
                }
            }
            "LuaDouble" -> {
                try {
                    val method = obj.javaClass.getMethod("todouble")
                    method.invoke(obj)
                } catch (e: Exception) {
                    obj.toString().toDoubleOrNull() ?: obj.toString()
                }
            }
            "LuaBoolean" -> {
                try {
                    val method = obj.javaClass.getMethod("toboolean")
                    method.invoke(obj)
                } catch (e: Exception) {
                    obj.toString() == "true"
                }
            }
            "LuaTable" -> {
                try {
                    val result = mutableMapOf<String, Any?>()
                    val keysMethod = obj.javaClass.getMethod("keys")
                    val keys = keysMethod.invoke(obj) as? Array<*>
                    val getMethod = obj.javaClass.getMethod("get", Class.forName("org.luaj.vm2.LuaValue"))
                    keys?.forEach { key ->
                        if (key != null) {
                            val value = getMethod.invoke(obj, key)
                            val keyStr = toJava(key)?.toString() ?: return@forEach
                            result[keyStr] = toJava(value)
                        }
                    }
                    result
                } catch (e: Exception) {
                    emptyMap<String, Any>()
                }
            }
            else -> obj.toString()
        }
    }

    private fun convertJsObject(obj: Any): Any? {
        return when {
            obj is Map<*, *> -> obj.entries.associate { (k, v) -> k.toString() to toJava(v) }
            obj is List<*> -> obj.map { toJava(it) }
            else -> obj.toString()
        }
    }
}

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

    // Маппер - контекстное меню
    fun registerMapCommand(name: String, callback: Any)
    fun unregisterMapCommand(name: String)

    // Маппер - подсветка пути
    fun setPathHighlight(roomIds: List<String>, targetRoomId: String?)
    fun clearPathHighlight()

    // Статус-панель
    fun statusAddBar(id: String, label: String, value: Int, max: Int, color: String = "green", showText: Boolean = true, showMax: Boolean = true, order: Int = -1)
    fun statusAddText(id: String, label: String, value: String? = null, color: String? = null, bold: Boolean = false, background: String? = null, order: Int = -1)
    fun statusAddFlags(id: String, label: String, flags: List<Map<String, Any>>, order: Int = -1)
    fun statusAddMiniMap(id: String, currentRoomId: String? = null, visible: Boolean = true, order: Int = -1)
    fun statusAddPathPanel(id: String, targetName: String, stepsCount: Int, directions: List<String>, onClear: Any? = null, onFollow: Any? = null, order: Int = -1)
    fun statusUpdate(id: String, updates: Map<String, Any>)
    fun statusRemove(id: String)
    fun statusClear()
    fun statusGet(id: String): Map<String, Any>?
    fun statusExists(id: String): Boolean

    // Утилиты
    fun log(message: String)
    fun print(message: String)

    // Фокус
    fun requestInputFocus()
}

/**
 * Реализация ScriptAPI
 */
class ScriptAPIImpl(
    private val sendCommand: (String) -> Unit,
    private val echoText: (String) -> Unit,
    private val logMessage: (String) -> Unit,
    private val requestFocus: () -> Unit,
    private val triggerActions: TriggerActions,
    private val aliasActions: AliasActions,
    private val timerActions: TimerActions,
    private val variableActions: VariableActions,
    private val msdpActions: MsdpActions,
    private val gmcpActions: GmcpActions,
    private val mapperActions: MapperActions,
    private val statusActions: StatusActions
) : ScriptAPI {

    /** Имя текущего выполняемого скрипта (для логирования) */
    var currentScriptName: String? = null

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
    override fun setRoomTags(roomId: String, tags: List<String>) {
        // Автоматическая конвертация из скриптовых типов
        val convertedTags: List<String> = ScriptObjectConverter.toList(tags)
        mapperActions.setRoomTags(roomId, convertedTags)
    }

    override fun createRoom(id: String, name: String): Boolean =
        mapperActions.createRoom(id, name)

    override fun createRoomWithExits(id: String, name: String, exits: Map<String, String>): Boolean {
        // Автоматическая конвертация из скриптовых типов
        @Suppress("UNCHECKED_CAST")
        val convertedExits = ScriptObjectConverter.toMap(exits) as Map<String, String>
        return mapperActions.createRoomWithExits(id, name, convertedExits)
    }

    override fun linkRooms(fromRoomId: String, direction: String, toRoomId: String) =
        mapperActions.linkRooms(fromRoomId, direction, toRoomId)

    override fun addUnexploredExits(roomId: String, exits: List<String>) {
        // Автоматическая конвертация из скриптовых типов
        val convertedExits: List<String> = ScriptObjectConverter.toList(exits)
        mapperActions.addUnexploredExits(roomId, convertedExits)
    }

    override fun handleMovement(direction: String, roomName: String, exits: List<String>, roomId: String?): Map<String, Any>? {
        // Автоматическая конвертация из скриптовых типов
        val convertedExits: List<String> = ScriptObjectConverter.toList(exits)
        return mapperActions.handleMovement(direction, roomName, convertedExits, roomId)
    }

    override fun handleRoom(params: Map<String, Any>): Map<String, Any>? {
        // Автоматическая конвертация из скриптовых типов (PyDictionary, LuaTable, etc.)
        val convertedParams = ScriptObjectConverter.toMap(params)
        return mapperActions.handleRoom(convertedParams)
    }

    override fun setMapEnabled(enabled: Boolean) = mapperActions.setMapEnabled(enabled)
    override fun isMapEnabled(): Boolean = mapperActions.isMapEnabled()
    override fun clearMap() = mapperActions.clearMap()
    override fun setCurrentRoom(roomId: String) = mapperActions.setCurrentRoom(roomId)

    override fun registerMapCommand(name: String, callback: Any) = mapperActions.registerMapCommand(name, callback)
    override fun unregisterMapCommand(name: String) = mapperActions.unregisterMapCommand(name)

    override fun setPathHighlight(roomIds: List<String>, targetRoomId: String?) {
        val convertedRoomIds: List<String> = ScriptObjectConverter.toList(roomIds)
        mapperActions.setPathHighlight(convertedRoomIds, targetRoomId)
    }
    override fun clearPathHighlight() = mapperActions.clearPathHighlight()

    override fun statusAddBar(id: String, label: String, value: Int, max: Int, color: String, showText: Boolean, showMax: Boolean, order: Int) =
        statusActions.addBar(id, label, value, max, color, showText, showMax, order)
    override fun statusAddText(id: String, label: String, value: String?, color: String?, bold: Boolean, background: String?, order: Int) =
        statusActions.addText(id, label, value, color, bold, background, order)
    override fun statusAddFlags(id: String, label: String, flags: List<Map<String, Any>>, order: Int) {
        // Автоматическая конвертация из скриптовых типов
        @Suppress("UNCHECKED_CAST")
        val convertedFlags: List<Map<String, Any>> = ScriptObjectConverter.toList(flags)
        statusActions.addFlags(id, label, convertedFlags, order)
    }
    override fun statusAddMiniMap(id: String, currentRoomId: String?, visible: Boolean, order: Int) =
        statusActions.addMiniMap(id, currentRoomId, visible, order)
    override fun statusAddPathPanel(id: String, targetName: String, stepsCount: Int, directions: List<String>, onClear: Any?, onFollow: Any?, order: Int) {
        val convertedDirections: List<String> = ScriptObjectConverter.toList(directions)
        // Convert JS callbacks to Kotlin lambdas
        val clearCallback: (() -> Unit)? = if (onClear != null) {
            { statusActions.invokeJsCallback(onClear) }
        } else null
        val followCallback: (() -> Unit)? = if (onFollow != null) {
            { statusActions.invokeJsCallback(onFollow) }
        } else null
        statusActions.addPathPanel(id, targetName, stepsCount, convertedDirections, clearCallback, followCallback, order)
    }
    override fun statusUpdate(id: String, updates: Map<String, Any>) {
        // Автоматическая конвертация из скриптовых типов (PyDictionary, LuaTable, etc.)
        val convertedUpdates = ScriptObjectConverter.toMap(updates)
        statusActions.update(id, convertedUpdates)
    }
    override fun statusRemove(id: String) = statusActions.remove(id)
    override fun statusClear() = statusActions.clear()
    override fun statusGet(id: String): Map<String, Any>? = statusActions.get(id)
    override fun statusExists(id: String): Boolean = statusActions.exists(id)

    override fun log(message: String) {
        val prefix = currentScriptName ?: "SCRIPT"
        logMessage("[$prefix] $message")
    }
    override fun print(message: String) = echoText(message)
    override fun requestInputFocus() = requestFocus()
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

    // Контекстное меню
    fun registerMapCommand(name: String, callback: Any)
    fun unregisterMapCommand(name: String)

    // Подсветка пути
    fun setPathHighlight(roomIds: List<String>, targetRoomId: String?)
    fun clearPathHighlight()
}

interface StatusActions {
    fun addBar(id: String, label: String, value: Int, max: Int, color: String, showText: Boolean, showMax: Boolean, order: Int)
    fun addText(id: String, label: String, value: String?, color: String?, bold: Boolean, background: String?, order: Int)
    fun addFlags(id: String, label: String, flags: List<Map<String, Any>>, order: Int)
    fun addMiniMap(id: String, currentRoomId: String?, visible: Boolean, order: Int)
    fun addPathPanel(id: String, targetName: String, stepsCount: Int, directions: List<String>, onClear: (() -> Unit)?, onFollow: (() -> Unit)?, order: Int)
    fun update(id: String, updates: Map<String, Any>)
    fun remove(id: String)
    fun clear()
    fun get(id: String): Map<String, Any>?
    fun exists(id: String): Boolean
    fun invokeJsCallback(callback: Any)
}
