package com.bylins.client.plugins

import mu.KotlinLogging
import com.bylins.client.plugins.events.EventBus
import com.bylins.client.plugins.events.EventPriority
import com.bylins.client.plugins.events.PluginEvent
import com.bylins.client.plugins.scripting.ScriptEvent
import com.bylins.client.plugins.ui.PluginTab
import com.bylins.client.plugins.ui.PluginTabImpl
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Реализация PluginAPI.
 *
 * Адаптер между плагинами и ClientState.
 *
 * @param pluginId ID плагина-владельца этого API
 * @param sendCommand Функция для отправки команды на сервер
 * @param echoText Функция для вывода текста в клиент
 * @param eventBus Шина событий для подписок
 * @param variableGetter Функция для получения переменной
 * @param variableSetter Функция для установки переменной
 * @param variableDeleter Функция для удаления переменной
 * @param getAllVariablesFunc Функция для получения всех переменных
 * @param msdpGetter Функция для получения MSDP значения
 * @param getAllMsdpFunc Функция для получения всех MSDP данных
 * @param gmcpGetter Функция для получения GMCP пакета
 * @param getAllGmcpFunc Функция для получения всех GMCP данных
 * @param gmcpSender Функция для отправки GMCP данных
 * @param getCurrentRoomFunc Функция для получения текущей комнаты
 * @param getRoomFunc Функция для получения комнаты по ID
 * @param searchRoomsFunc Функция для поиска комнат
 * @param setRoomNoteFunc Функция для установки заметки комнаты
 * @param setRoomColorFunc Функция для установки цвета комнаты
 * @param isPluginLoadedFunc Функция для проверки загрузки плагина
 * @param dataFolder Директория данных плагина
 */
private val logger = KotlinLogging.logger("PluginAPI")
class PluginAPIImpl(
    private val pluginId: String,
    private val sendCommand: (String) -> Unit,
    private val echoText: (String) -> Unit,
    private val eventBus: EventBus,
    private val variableGetter: (String) -> String?,
    private val variableSetter: (String, String) -> Unit,
    private val variableDeleter: (String) -> Unit,
    private val getAllVariablesFunc: () -> Map<String, String>,
    private val msdpGetter: (String) -> Any?,
    private val getAllMsdpFunc: () -> Map<String, Any>,
    private val gmcpGetter: (String) -> String?,
    private val getAllGmcpFunc: () -> Map<String, String>,
    private val gmcpSender: (String, String) -> Unit,
    private val getCurrentRoomFunc: () -> Map<String, Any>?,
    private val getRoomFunc: (String) -> Map<String, Any>?,
    private val searchRoomsFunc: (String) -> List<Map<String, Any>>,
    private val findPathFunc: (String) -> List<String>?,
    private val setRoomNoteFunc: (String, String) -> Unit,
    private val setRoomColorFunc: (String, String?) -> Unit,
    private val setRoomZoneFunc: (String, String) -> Unit,
    private val setRoomTagsFunc: (String, List<String>) -> Unit,
    private val createRoomFunc: (String, String) -> Boolean,
    private val createRoomWithExitsFunc: (String, String, Map<String, String>) -> Boolean,
    private val linkRoomsFunc: (String, String, String) -> Unit,
    private val handleMovementFunc: (String, String, List<String>) -> Map<String, Any>?,
    private val setMapEnabledFunc: (Boolean) -> Unit,
    private val isMapEnabledFunc: () -> Boolean,
    private val clearMapFunc: () -> Unit,
    private val setCurrentRoomFunc: (String) -> Unit,
    private val isPluginLoadedFunc: (String) -> Boolean,
    private val registerPluginTabFunc: (PluginTab) -> Unit,
    private val unregisterPluginTabFunc: (String) -> Unit,
    private val findRoomsMatchingFunc: ((Map<String, Any>) -> Boolean, Int) -> List<Map<String, Any>>,
    private val findNearestMatchingFunc: ((Map<String, Any>) -> Boolean) -> Pair<Map<String, Any>, List<String>>?,
    private val fireScriptEventFunc: (ScriptEvent, Any?) -> Unit,
    private val dataFolder: File
) : PluginAPI {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // Счётчики для генерации ID
    private val triggerIdCounter = AtomicLong(0)
    private val aliasIdCounter = AtomicLong(0)
    private val timerIdCounter = AtomicLong(0)
    private val subscriptionIdCounter = AtomicLong(0)

    // Хранилище созданных объектов
    private val triggers = ConcurrentHashMap<String, PluginTrigger>()
    private val aliases = ConcurrentHashMap<String, PluginAlias>()
    private val timers = ConcurrentHashMap<String, Job>()
    private val subscriptions = ConcurrentHashMap<String, String>() // handle.id -> eventBus subscription id
    private val pluginTabs = ConcurrentHashMap<String, PluginTab>()

    // ============================================
    // Отправка и вывод
    // ============================================

    override fun send(command: String) {
        sendCommand(command)
    }

    override fun send(vararg commands: String) {
        commands.forEach { sendCommand(it) }
    }

    override fun echo(text: String) {
        echoText("[Plugin:$pluginId] $text")
    }

    override fun echo(text: String, color: String) {
        // Добавляем ANSI код цвета
        val colorCode = when (color.lowercase()) {
            "red" -> "\u001B[31m"
            "green" -> "\u001B[32m"
            "yellow" -> "\u001B[33m"
            "blue" -> "\u001B[34m"
            "magenta" -> "\u001B[35m"
            "cyan" -> "\u001B[36m"
            "white" -> "\u001B[37m"
            else -> ""
        }
        val reset = "\u001B[0m"
        echoText("$colorCode[Plugin:$pluginId] $text$reset")
    }

    // ============================================
    // Триггеры
    // ============================================

    override fun createTrigger(
        pattern: Regex,
        priority: Int,
        handler: TriggerHandler
    ): TriggerHandle {
        val id = "pt_${pluginId}_${triggerIdCounter.incrementAndGet()}"
        val trigger = PluginTrigger(
            id = id,
            pattern = pattern,
            priority = priority,
            handler = handler
        )
        triggers[id] = trigger
        return TriggerHandle(id)
    }

    override fun createRawTrigger(
        pattern: Regex,
        priority: Int,
        handler: RawTriggerHandler
    ): TriggerHandle {
        val id = "pt_${pluginId}_${triggerIdCounter.incrementAndGet()}"
        val trigger = PluginTrigger(
            id = id,
            pattern = pattern,
            priority = priority,
            rawHandler = handler
        )
        triggers[id] = trigger
        return TriggerHandle(id)
    }

    override fun removeTrigger(handle: TriggerHandle) {
        triggers.remove(handle.id)
    }

    /**
     * Проверяет строку на совпадение с триггерами плагина.
     * Вызывается из ClientState при получении строки.
     *
     * @param line Очищенная строка (без ANSI)
     * @param rawLine Оригинальная строка с ANSI кодами
     * @return TriggerResult или null если ни один триггер не сработал
     */
    fun checkTriggers(line: String, rawLine: String): TriggerResult? {
        val sortedTriggers = triggers.values.sortedByDescending { it.priority }

        for (trigger in sortedTriggers) {
            val match = trigger.pattern.find(line)
            if (match != null) {
                val groups = listOf(match.value) + match.groupValues.drop(1)
                try {
                    val result = when {
                        trigger.handler != null -> trigger.handler.handle(line, groups)
                        trigger.rawHandler != null -> {
                            val context = TriggerContext(line, rawLine, groups)
                            trigger.rawHandler.handle(context)
                        }
                        else -> TriggerResult.CONTINUE
                    }
                    if (result != TriggerResult.CONTINUE) {
                        return result
                    }
                } catch (e: Exception) {
                    logger.error { "Error in trigger handler: ${e.message}" }
                }
            }
        }
        return null
    }

    // ============================================
    // Алиасы
    // ============================================

    override fun createAlias(
        pattern: Regex,
        priority: Int,
        handler: AliasHandler
    ): AliasHandle {
        val id = "pa_${pluginId}_${aliasIdCounter.incrementAndGet()}"
        val alias = PluginAlias(
            id = id,
            pattern = pattern,
            priority = priority,
            handler = handler
        )
        aliases[id] = alias
        return AliasHandle(id)
    }

    override fun removeAlias(handle: AliasHandle) {
        aliases.remove(handle.id)
    }

    /**
     * Проверяет команду на совпадение с алиасами плагина.
     * Вызывается из ClientState перед отправкой команды.
     *
     * @return true если алиас обработал команду
     */
    fun checkAliases(command: String): Boolean {
        val sortedAliases = aliases.values.sortedByDescending { it.priority }

        for (alias in sortedAliases) {
            val match = alias.pattern.find(command)
            if (match != null) {
                val groups = listOf(match.value) + match.groupValues.drop(1)
                try {
                    if (alias.handler.handle(command, groups)) {
                        return true
                    }
                } catch (e: Exception) {
                    logger.error { "Error in alias handler: ${e.message}" }
                }
            }
        }
        return false
    }

    // ============================================
    // Таймеры
    // ============================================

    override fun setTimeout(delayMs: Long, callback: () -> Unit): TimerHandle {
        val id = "timer_${pluginId}_${timerIdCounter.incrementAndGet()}"

        val job = scope.launch {
            delay(delayMs)
            try {
                callback()
            } catch (e: Exception) {
                logger.error { "Error in setTimeout callback: ${e.message}" }
            }
            timers.remove(id)
        }

        timers[id] = job
        return TimerHandle(id)
    }

    override fun setInterval(intervalMs: Long, callback: () -> Unit): TimerHandle {
        val id = "timer_${pluginId}_${timerIdCounter.incrementAndGet()}"

        val job = scope.launch {
            while (isActive) {
                delay(intervalMs)
                try {
                    callback()
                } catch (e: Exception) {
                    logger.error { "Error in setInterval callback: ${e.message}" }
                }
            }
        }

        timers[id] = job
        return TimerHandle(id)
    }

    override fun cancelTimer(handle: TimerHandle) {
        timers[handle.id]?.cancel()
        timers.remove(handle.id)
    }

    // ============================================
    // Переменные
    // ============================================

    override fun getVariable(name: String): String? = variableGetter(name)

    override fun setVariable(name: String, value: String) = variableSetter(name, value)

    override fun deleteVariable(name: String) = variableDeleter(name)

    override fun getAllVariables(): Map<String, String> = getAllVariablesFunc()

    // ============================================
    // MSDP
    // ============================================

    override fun getMsdpValue(key: String): Any? = msdpGetter(key)

    @Suppress("UNCHECKED_CAST")
    override fun <T> getMsdpValue(key: String, type: Class<T>): T? {
        val value = msdpGetter(key) ?: return null
        return try {
            when {
                type == String::class.java -> value.toString() as T
                type == Int::class.java || type == java.lang.Integer::class.java ->
                    value.toString().toIntOrNull() as T?
                type == Long::class.java || type == java.lang.Long::class.java ->
                    value.toString().toLongOrNull() as T?
                type == Double::class.java || type == java.lang.Double::class.java ->
                    value.toString().toDoubleOrNull() as T?
                type == Boolean::class.java || type == java.lang.Boolean::class.java ->
                    value.toString().toBoolean() as T
                else -> value as? T
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun getAllMsdpData(): Map<String, Any> = getAllMsdpFunc()

    // ============================================
    // GMCP
    // ============================================

    override fun getGmcpPackage(packageName: String): String? = gmcpGetter(packageName)

    override fun getAllGmcpData(): Map<String, String> = getAllGmcpFunc()

    override fun sendGmcp(packageName: String, data: String) = gmcpSender(packageName, data)

    // ============================================
    // Маппер - чтение
    // ============================================

    override fun getCurrentRoom(): Map<String, Any>? = getCurrentRoomFunc()

    override fun getRoom(roomId: String): Map<String, Any>? = getRoomFunc(roomId)

    override fun searchRooms(query: String): List<Map<String, Any>> = searchRoomsFunc(query)

    override fun findPath(targetRoomId: String): List<String>? = findPathFunc(targetRoomId)

    // ============================================
    // Маппер - модификация
    // ============================================

    override fun setRoomNote(roomId: String, note: String) = setRoomNoteFunc(roomId, note)

    override fun setRoomColor(roomId: String, color: String?) = setRoomColorFunc(roomId, color)

    override fun setRoomZone(roomId: String, zone: String) = setRoomZoneFunc(roomId, zone)

    override fun setRoomTags(roomId: String, tags: List<String>) = setRoomTagsFunc(roomId, tags)

    // ============================================
    // Маппер - создание
    // ============================================

    override fun createRoom(id: String, name: String): Boolean =
        createRoomFunc(id, name)

    override fun createRoomWithExits(id: String, name: String, exits: Map<String, String>): Boolean =
        createRoomWithExitsFunc(id, name, exits)

    override fun linkRooms(fromRoomId: String, direction: String, toRoomId: String) =
        linkRoomsFunc(fromRoomId, direction, toRoomId)

    override fun handleMovement(direction: String, roomName: String, exits: List<String>): Map<String, Any>? =
        handleMovementFunc(direction, roomName, exits)

    // ============================================
    // Маппер - управление
    // ============================================

    override fun setMapEnabled(enabled: Boolean) = setMapEnabledFunc(enabled)

    override fun isMapEnabled(): Boolean = isMapEnabledFunc()

    override fun clearMap() = clearMapFunc()

    override fun setCurrentRoom(roomId: String) = setCurrentRoomFunc(roomId)

    // ============================================
    // UI - вкладки
    // ============================================

    override fun createTab(id: String, title: String): PluginTab {
        val fullId = "${pluginId}_$id"
        val tab = PluginTabImpl(fullId, title) { tabId ->
            pluginTabs.remove(tabId)
            unregisterPluginTabFunc(tabId)
        }
        pluginTabs[fullId] = tab
        registerPluginTabFunc(tab)
        return tab
    }

    override fun closeTab(id: String) {
        val fullId = "${pluginId}_$id"
        pluginTabs[fullId]?.close()
    }

    override fun getTab(id: String): PluginTab? {
        val fullId = "${pluginId}_$id"
        return pluginTabs[fullId]
    }

    // ============================================
    // Маппер - поиск с callback
    // ============================================

    override fun findRoomsMatching(
        predicate: (Map<String, Any>) -> Boolean,
        maxResults: Int
    ): List<Map<String, Any>> = findRoomsMatchingFunc(predicate, maxResults)

    override fun findNearestMatching(
        predicate: (Map<String, Any>) -> Boolean
    ): Pair<Map<String, Any>, List<String>>? = findNearestMatchingFunc(predicate)

    // ============================================
    // События
    // ============================================

    override fun <T : PluginEvent> subscribe(
        eventClass: Class<T>,
        priority: EventPriority,
        handler: EventHandler<T>
    ): EventSubscription {
        val handleId = "sub_${pluginId}_${subscriptionIdCounter.incrementAndGet()}"
        val eventBusSubId = eventBus.subscribe(eventClass, priority, pluginId) { event ->
            handler.handle(event)
        }
        subscriptions[handleId] = eventBusSubId
        return EventSubscription(handleId)
    }

    override fun unsubscribe(subscription: EventSubscription) {
        val eventBusSubId = subscriptions.remove(subscription.id)
        if (eventBusSubId != null) {
            eventBus.unsubscribe(eventBusSubId)
        }
    }

    // ============================================
    // Конфигурация
    // ============================================

    @Suppress("UNCHECKED_CAST")
    override fun saveConfig(config: Any) {
        try {
            val configFile = File(dataFolder, "config.json")
            if (!dataFolder.exists()) {
                dataFolder.mkdirs()
            }
            // Получаем сериализатор для конкретного типа через рефлексию
            val kClass = config::class
            @Suppress("UNCHECKED_CAST", "USELESS_CAST")
            val serializer = kotlinx.serialization.serializer(kClass.java) as kotlinx.serialization.KSerializer<Any>
            val jsonString = json.encodeToString(serializer, config)
            configFile.writeText(jsonString)
        } catch (e: Exception) {
            logger.error { "Error saving config: ${e.message}" }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> loadConfig(type: Class<T>): T? {
        return try {
            val configFile = File(dataFolder, "config.json")
            if (!configFile.exists()) {
                return null
            }
            val jsonString = configFile.readText()
            // Для String возвращаем сырой JSON - плагин сам десериализует
            if (type == String::class.java) {
                jsonString as T
            } else {
                // Для других типов пытаемся использовать Gson если доступен
                // или возвращаем null - плагин должен сам парсить JSON
                logger.info { "loadConfig: Complex types require plugin-side deserialization. Use loadConfig(String::class.java)" }
                null
            }
        } catch (e: Exception) {
            logger.error { "Error loading config: ${e.message}" }
            null
        }
    }

    // ============================================
    // Взаимодействие с другими плагинами
    // ============================================

    override fun <T> getPluginApi(pluginId: String, apiClass: Class<T>): T? {
        // TODO: Реализовать получение API других плагинов
        return null
    }

    override fun isPluginLoaded(pluginId: String): Boolean = isPluginLoadedFunc(pluginId)

    // ============================================
    // События скриптов
    // ============================================

    override fun fireScriptEvent(event: ScriptEvent, data: Any?) = fireScriptEventFunc(event, data)

    // ============================================
    // Внутренние методы
    // ============================================

    /**
     * Очищает все ресурсы при выгрузке плагина.
     */
    fun cleanup() {
        // Отменяем все таймеры
        timers.values.forEach { it.cancel() }
        timers.clear()

        // Очищаем триггеры и алиасы
        triggers.clear()
        aliases.clear()

        // Отписываемся от всех событий
        subscriptions.values.forEach { eventBus.unsubscribe(it) }
        subscriptions.clear()

        // Закрываем все вкладки плагина
        pluginTabs.values.toList().forEach { it.close() }
        pluginTabs.clear()

        // Отменяем корутины
        scope.cancel()
    }

    /**
     * Получает количество активных триггеров.
     */
    fun getTriggerCount(): Int = triggers.size

    /**
     * Получает количество активных алиасов.
     */
    fun getAliasCount(): Int = aliases.size

    /**
     * Получает количество активных таймеров.
     */
    fun getTimerCount(): Int = timers.size
}

/**
 * Внутренний класс для хранения триггера плагина.
 */
private data class PluginTrigger(
    val id: String,
    val pattern: Regex,
    val priority: Int,
    val handler: TriggerHandler? = null,
    val rawHandler: RawTriggerHandler? = null
)

/**
 * Внутренний класс для хранения алиаса плагина.
 */
private data class PluginAlias(
    val id: String,
    val pattern: Regex,
    val priority: Int,
    val handler: AliasHandler
)
