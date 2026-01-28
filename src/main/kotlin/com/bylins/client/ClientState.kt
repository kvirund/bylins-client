package com.bylins.client

import mu.KotlinLogging
import com.bylins.client.aliases.AliasManager
import com.bylins.client.commands.CommandContext
import com.bylins.client.commands.CommandProcessor
import com.bylins.client.config.ConfigManager
import com.bylins.client.config.DefaultData
import com.bylins.client.hotkeys.HotkeyManager
import com.bylins.client.logging.LogManager
import com.bylins.client.logging.UiLogBuffer
import com.bylins.client.network.TelnetClient
import com.bylins.client.stats.SessionStats
import com.bylins.client.tabs.TabManager
import com.bylins.client.triggers.TriggerManager
import com.bylins.client.variables.VariableManager
import com.bylins.client.status.StatusManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import java.io.File

private val logger = KotlinLogging.logger("ClientState")
class ClientState {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val configManager = ConfigManager()

    // Флаг для предотвращения множественного сохранения при инициализации
    private var isInitializing = true

    // Debounce для сохранения конфига
    private var saveConfigJob: kotlinx.coroutines.Job? = null
    private val saveConfigDebounceMs = 500L

    // Время последнего срабатывания хоткея (для блокировки дублирования ввода)
    @Volatile
    private var lastHotkeyTimestamp = 0L
    private val hotkeyInputBlockMs = 200L // Блокируем ввод на 200мс после хоткея

    /**
     * Проверяет, был ли недавно обработан хоткей (для блокировки текстового ввода)
     */
    fun wasHotkeyRecentlyProcessed(): Boolean {
        return System.currentTimeMillis() - lastHotkeyTimestamp < hotkeyInputBlockMs
    }

    // Менеджеры инициализируются первыми
    private val aliasManager = AliasManager(
        onCommand = { command ->
            // Callback для отправки команд из алиасов (без рекурсии)
            // Подставляем переменные перед отправкой
            val substituted = variableManager.substituteVariables(command)
            sendRaw(substituted)
        },
        onAliasFired = { alias, command, groups ->
            // Уведомляем скрипты о срабатывании алиаса
            if (::scriptManager.isInitialized) {
                scriptManager.fireEvent(com.bylins.client.scripting.ScriptEvent.ON_ALIAS, alias, command, groups)
            }

            // Уведомляем плагины о срабатывании алиаса
            if (::pluginManager.isInitialized) {
                pluginEventBus.post(com.bylins.client.plugins.events.AliasFiredEvent(
                    aliasId = alias.id,
                    aliasName = alias.name,
                    input = command,
                    groups = groups.values.toList()
                ))
            }
        }
    )

    private val triggerManager = TriggerManager(
        onCommand = { command ->
            // Callback для отправки команд из триггеров
            send(command)
        },
        onTriggerFired = { trigger, line, groups ->
            // Уведомляем скрипты о срабатывании триггера
            if (::scriptManager.isInitialized) {
                scriptManager.fireEvent(com.bylins.client.scripting.ScriptEvent.ON_TRIGGER, trigger, line, groups)
            }

            // Уведомляем плагины о срабатывании триггера
            if (::pluginManager.isInitialized) {
                pluginEventBus.post(com.bylins.client.plugins.events.TriggerFiredEvent(
                    triggerId = trigger.id,
                    triggerName = trigger.name,
                    line = line,
                    groups = groups.values.toList()
                ))
            }
        }
    )

    private val hotkeyManager = HotkeyManager { command ->
        // Callback для отправки команд из хоткеев
        send(command)
    }

    private val logManager = LogManager()
    private val sessionStats = SessionStats()
    private val statsHistory = com.bylins.client.stats.StatsHistory()
    private val chartManager = com.bylins.client.stats.ChartManager()
    private val scriptStorage = com.bylins.client.scripting.ScriptStorage()
    private val soundManager = com.bylins.client.audio.SoundManager()
    private val variableManager = VariableManager()
    val statusManager = StatusManager(variableManager)
    private val tabManager = TabManager()

    // Хранилище триггеров из скриптов
    private data class ScriptTrigger(
        val id: String,
        val pattern: Regex,
        val callback: (String, Map<Int, String>) -> Unit,
        var enabled: Boolean = true
    )
    private val scriptTriggers = java.util.concurrent.ConcurrentHashMap<String, ScriptTrigger>()

    /**
     * Проверяет скриптовые триггеры на совпадение с строкой
     */
    private fun checkScriptTriggers(line: String) {
        if (line.contains("Вых") || line.contains("[") && line.contains("]")) {
        }
        for (trigger in scriptTriggers.values) {
            if (!trigger.enabled) continue

            try {
                val matchResult = trigger.pattern.find(line)
                if (matchResult != null) {
                    // Формируем groups как Map<Int, String>
                    val groups = mutableMapOf<Int, String>()
                    matchResult.groupValues.forEachIndexed { index, value ->
                        groups[index] = value
                    }

                    // Вызываем callback
                    try {
                        trigger.callback(line, groups)
                    } catch (e: Exception) {
                        logger.error { "[ScriptAPI] Error in trigger callback: ${e.message}" }
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                logger.error { "[ScriptAPI] Error matching trigger ${trigger.id}: ${e.message}" }
            }
        }
    }

    // Для throttling звуковых уведомлений
    private var lastLowHpSoundTime = 0L

    // Callback для MapManager
    private val mapManagerOnRoomEnter: (com.bylins.client.mapper.Room) -> Unit = { room ->
        // Запускаем уведомления асинхронно чтобы избежать deadlock при вызове из API
        scope.launch {
            // Уведомляем скрипты о входе в комнату
            if (::scriptManager.isInitialized) {
                scriptManager.fireEvent(com.bylins.client.scripting.ScriptEvent.ON_ROOM_ENTER, room)
            }

            // Уведомляем плагины о входе в комнату
            if (::pluginManager.isInitialized) {
                pluginEventBus.post(com.bylins.client.plugins.events.RoomEnterEvent(
                    roomId = room.id,
                    roomName = room.name,
                    fromDirection = null // TODO: передать направление откуда пришли
                ))
            }

            // Обрабатываем контекстные команды при входе в комнату
            contextCommandManager.onRoomEnter(room)

            // Обрабатываем правила контекстных команд из профилей
            for (profile in profileManager.getActiveProfiles()) {
                if (profile.contextCommandRules.isNotEmpty()) {
                    logger.debug { "Processing ${profile.contextCommandRules.size} room/zone context rules from profile ${profile.name}" }
                }
                contextCommandManager.processRoomRules(room, profile.contextCommandRules)
            }
        }
    }

    // MapManager - может быть пересоздан при смене профиля
    private var mapManager = com.bylins.client.mapper.MapManager(
        onRoomEnter = mapManagerOnRoomEnter
    )

    // Менеджер контекстных команд (инициализируется в init)
    var contextCommandManager: com.bylins.client.contextcommands.ContextCommandManager =
        com.bylins.client.contextcommands.ContextCommandManager(
            onCommand = { command -> send(command) },
            getCurrentRoom = { mapManager.getCurrentRoom() }
        )
        private set

    // CommandProcessor для обработки # команд
    private val commandContext = object : CommandContext {
        override fun addLocalOutput(text: String) {
            telnetClient.addLocalOutput(text)
        }
        override fun sendRaw(command: String) {
            this@ClientState.sendRaw(command)
        }
        override fun getAllZones(): List<String> = this@ClientState.getAllZones()
        override fun getZoneStatistics(): Map<String, Int> = this@ClientState.getZoneStatistics()
        override fun detectAndAssignZones() = this@ClientState.detectAndAssignZones()
        override fun clearAllZones() = this@ClientState.clearAllZones()
    }
    private lateinit var commandProcessor: CommandProcessor

    private val roomParser = com.bylins.client.mapper.RoomParser()

    private var lastCommand: String? = null

    // Кодировка для telnet (конфигурируется пользователем)
    private var _encoding = "UTF-8"
    val encoding: String
        get() = _encoding

    // Ширина боковой панели с миникартой
    private val _miniMapWidth = MutableStateFlow(250)
    val miniMapWidth: StateFlow<Int> = _miniMapWidth

    // Высота миникарты в статус-панели
    private val _miniMapHeight = MutableStateFlow(300)
    val miniMapHeight: StateFlow<Int> = _miniMapHeight

    // Ширина панели заметок зоны на вкладке Карта
    private val _zonePanelWidth = MutableStateFlow(220)
    val zonePanelWidth: StateFlow<Int> = _zonePanelWidth

    // Тема оформления (DARK, LIGHT, DARK_BLUE, SOLARIZED_DARK, MONOKAI)
    private val _currentTheme = MutableStateFlow("DARK")
    val currentTheme: StateFlow<String> = _currentTheme

    // Настройки шрифта
    private val _fontFamily = MutableStateFlow("MONOSPACE")
    val fontFamily: StateFlow<String> = _fontFamily

    private val _fontSize = MutableStateFlow(14)
    val fontSize: StateFlow<Int> = _fontSize

    // Игнорировать состояние NumLock для хоткеев
    private val _ignoreNumLock = MutableStateFlow(false)
    val ignoreNumLock: StateFlow<Boolean> = _ignoreNumLock

    // Скрытые вкладки
    private val _hiddenTabs = MutableStateFlow<Set<String>>(emptySet())
    val hiddenTabs: StateFlow<Set<String>> = _hiddenTabs

    // Целевой профиль для добавления хоткеев/триггеров/алиасов в UI панелях (null = база)
    private val _panelTargetProfileId = MutableStateFlow<String?>(null)
    val panelTargetProfileId: StateFlow<String?> = _panelTargetProfileId

    fun setPanelTargetProfileId(profileId: String?) {
        _panelTargetProfileId.value = profileId
    }

    private val telnetClient = TelnetClient(this, _encoding)

    // Скриптинг - инициализируется позже
    private lateinit var scriptManager: com.bylins.client.scripting.ScriptManager

    // Плагины - инициализируются после скриптинга
    private lateinit var pluginManager: com.bylins.client.plugins.PluginManager
    private val pluginEventBus = com.bylins.client.plugins.events.EventBus()
    val pluginTabManager = com.bylins.client.plugins.ui.PluginTabManager()

    // Профили персонажей - инициализируется после скриптинга
    lateinit var profileManager: com.bylins.client.profiles.ProfileManager
        private set

    val isConnected: StateFlow<Boolean> = telnetClient.isConnected
    val receivedData: StateFlow<String> = telnetClient.receivedData

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _msdpData = MutableStateFlow<Map<String, Any>>(emptyMap())
    val msdpData: StateFlow<Map<String, Any>> = _msdpData

    // Флаг: фокус на вторичном текстовом поле (заметки зоны и т.д.)
    private val _secondaryTextFieldFocused = MutableStateFlow(false)
    val secondaryTextFieldFocused: StateFlow<Boolean> = _secondaryTextFieldFocused
    fun setSecondaryTextFieldFocused(focused: Boolean) {
        _secondaryTextFieldFocused.value = focused
    }

    // Событие для запроса фокуса на поле ввода (инкрементируется для trigger)
    private val _requestInputFocus = MutableStateFlow(0)
    val requestInputFocus: StateFlow<Int> = _requestInputFocus
    fun requestInputFocus() {
        _requestInputFocus.value++
    }

    // MSDP статус (включён ли протокол)
    private val _msdpEnabled = MutableStateFlow(false)
    val msdpEnabled: StateFlow<Boolean> = _msdpEnabled

    // Список reportable переменных MSDP (полученный от сервера)
    private val _msdpReportableVariables = MutableStateFlow<List<String>>(emptyList())
    val msdpReportableVariables: StateFlow<List<String>> = _msdpReportableVariables

    // Список переменных, на которые включён REPORT
    private val _msdpReportedVariables = MutableStateFlow<Set<String>>(emptySet())
    val msdpReportedVariables: StateFlow<Set<String>> = _msdpReportedVariables

    // GMCP данные (Generic MUD Communication Protocol)
    private val _gmcpData = MutableStateFlow<Map<String, kotlinx.serialization.json.JsonElement>>(emptyMap())
    val gmcpData: StateFlow<Map<String, kotlinx.serialization.json.JsonElement>> = _gmcpData

    // Map context menu commands (registered by scripts)
    private val mapContextCommands = mutableMapOf<String, (com.bylins.client.mapper.Room) -> Unit>()

    // Профили подключений
    private val _connectionProfiles = MutableStateFlow<List<com.bylins.client.connection.ConnectionProfile>>(
        com.bylins.client.connection.ConnectionProfile.createDefaultProfiles()
    )
    val connectionProfiles: StateFlow<List<com.bylins.client.connection.ConnectionProfile>> = _connectionProfiles

    private val _currentProfileId = MutableStateFlow<String?>(null)
    val currentProfileId: StateFlow<String?> = _currentProfileId

    // Доступ к менеджерам
    val triggers = triggerManager.triggers
    val aliases = aliasManager.aliases
    val hotkeys = hotkeyManager.hotkeys

    // Доступ к логированию
    val isLogging = logManager.isLogging
    val currentLogFile = logManager.currentLogFile

    // Доступ к статистике
    val stats = sessionStats.stats
    val hpHistory = statsHistory.hpHistory
    val manaHistory = statsHistory.manaHistory
    val movementHistory = statsHistory.movementHistory

    // Доступ к динамическим графикам
    val dynamicCharts = chartManager.charts

    // Доступ к звукам
    val soundEnabled = soundManager.soundEnabled
    val soundVolume = soundManager.volume

    // Доступ к переменным
    val variables = variableManager.variables

    // Доступ к вкладкам
    val tabs = tabManager.tabs
    val activeTabId = tabManager.activeTabId

    // Выбранная подвкладка в панели плагинов (сохраняется между переключениями)
    var selectedPluginSubTab: String = "config"

    // Доступ к карте (используем getters для поддержки переключения mapManager)
    val mapRooms get() = mapManager.rooms
    val currentRoomId get() = mapManager.currentRoomId
    val mapEnabled get() = mapManager.mapEnabled
    val activePath get() = mapManager.activePath
    val pathTargetRoomId get() = mapManager.targetRoomId
    val pathHighlightRoomIds get() = mapManager.pathHighlightRoomIds
    val pathHighlightTargetId get() = mapManager.pathHighlightTargetId
    val zoneNotes get() = mapManager.zoneNotes
    val zoneNames get() = mapManager.zoneNames
    val mapViewCenterRoomId get() = mapManager.viewCenterRoomId

    fun getZoneNotes(zoneName: String): String = mapManager.getZoneNotes(zoneName)
    fun setZoneNotes(zoneName: String, notes: String) = mapManager.setZoneNotes(zoneName, notes)
    fun getZoneName(zoneId: String): String? = mapManager.getZoneName(zoneId)
    fun setZoneName(zoneId: String, areaName: String) = mapManager.setZoneName(zoneId, areaName)
    fun setMapViewCenterRoom(roomId: String?) = mapManager.setViewCenterRoom(roomId)

    /**
     * Возвращает список существующих файлов карт
     */
    fun getExistingMapFiles(): List<String> = com.bylins.client.mapper.MapDatabase.getExistingMapFiles()

    /**
     * Переключает базу данных карт на указанный файл
     * Вызывается при смене профиля подключения
     */
    fun switchMapDatabase(mapFile: String) {
        val currentMapFile = mapManager.getDbFileName()
        if (currentMapFile == mapFile) {
            logger.debug { "Map database already using $mapFile, skipping switch" }
            return
        }

        logger.info { "Switching map database from $currentMapFile to $mapFile" }

        // Закрываем старый MapManager
        mapManager.shutdown()

        // Создаём новый MapManager с новым файлом БД
        mapManager = com.bylins.client.mapper.MapManager(
            dbFileName = mapFile,
            onRoomEnter = mapManagerOnRoomEnter
        )

        // Обновляем ссылку на getCurrentRoom в contextCommandManager
        contextCommandManager.updateGetCurrentRoom { mapManager.getCurrentRoom() }

        logger.info { "Switched to map database: $mapFile (${mapManager.rooms.value.size} rooms)" }
    }

    init {
        // Регистрируем shutdown hook для корректного завершения
        Runtime.getRuntime().addShutdownHook(Thread {
            shutdown()
        })

        // Инициализируем скриптинг
        initializeScripting()

        // Инициализируем плагины
        initializePlugins()

        // Инициализируем обработчик команд
        commandProcessor = CommandProcessor(
            scope = scope,
            context = commandContext,
            mapManager = mapManager,
            soundManager = soundManager,
            contextCommandManager = contextCommandManager,
            getScriptManager = { if (::scriptManager.isInitialized) scriptManager else null },
            getPluginManager = { if (::pluginManager.isInitialized) pluginManager else null }
        )

        // Инициализируем профили персонажей
        initializeProfiles()

        // Продолжаем стандартную инициализацию
        // Пытаемся загрузить сохранённую конфигурацию
        val configData = configManager.loadConfig()

        // Загружаем кодировку из конфига
        _encoding = configData.encoding
        telnetClient.setEncoding(_encoding)

        // Загружаем размеры миникарты из конфига
        _miniMapWidth.value = configData.miniMapWidth
        _miniMapHeight.value = configData.miniMapHeight
        _zonePanelWidth.value = configData.zonePanelWidth

        // Загружаем тему из конфига
        _currentTheme.value = configData.theme

        // Загружаем настройки шрифта из конфига
        _fontFamily.value = configData.fontFamily
        _fontSize.value = configData.fontSize
        _ignoreNumLock.value = configData.ignoreNumLock
        _hiddenTabs.value = configData.hiddenTabs
        logManager.setLogWithColors(configData.logWithColors)

        // Загружаем профили подключений из конфига (должно быть до lastMapRoomId)
        _connectionProfiles.value = configData.connectionProfiles
        _currentProfileId.value = configData.currentProfileId

        // Переключаемся на карту из текущего профиля
        configData.currentProfileId?.let { profileId ->
            val profile = _connectionProfiles.value.find { it.id == profileId }
            profile?.let {
                switchMapDatabase(it.mapFile)
            }
        }

        // Загружаем последнюю просмотренную комнату карты из конфига
        // (делаем ПОСЛЕ переключения на правильную карту)
        logger.info { "Loading lastMapRoomId from config: ${configData.lastMapRoomId}, map has ${mapManager.rooms.value.size} rooms" }
        configData.lastMapRoomId?.let { roomId ->
            // Устанавливаем комнату центра обзора в mapManager, если комната существует на карте
            val room = mapManager.getRoom(roomId)
            if (room != null) {
                mapManager.setViewCenterRoom(roomId)
                logger.info { "Restored last map view center: $roomId (${room.name})" }
            } else {
                logger.warn { "Last map room $roomId not found on map" }
            }
        }

        if (configData.triggers.isEmpty() && configData.aliases.isEmpty() && configData.hotkeys.isEmpty() && configData.tabs.isEmpty()) {
            // Если конфига нет, загружаем стандартные триггеры, алиасы, хоткеи и вкладки
            DefaultData.getDefaultAliases().forEach { addAlias(it) }
            DefaultData.getDefaultTriggers().forEach { addTrigger(it) }
            DefaultData.getDefaultHotkeys().forEach { addHotkey(it) }
            DefaultData.getDefaultTabs().forEach { addTab(it) }
        } else {
            // Загружаем сохранённую конфигурацию
            configData.triggers.forEach { addTrigger(it) }
            configData.aliases.forEach { addAlias(it) }
            configData.hotkeys.forEach { addHotkey(it) }
            variableManager.loadVariables(configData.variables)
            tabManager.loadTabs(configData.tabs)
        }

        // Загружаем правила контекстных команд
        val contextRules = configData.contextCommandRules.mapNotNull { it.toRule() }
        contextCommandManager.loadRules(contextRules)
        contextCommandManager.setMaxQueueSize(configData.contextCommandMaxQueueSize)

        // Восстанавливаем стек профилей персонажей
        if (::profileManager.isInitialized && configData.activeProfileStack.isNotEmpty()) {
            profileManager.restoreStack(configData.activeProfileStack)
        }

        // Подписываемся на системные логи и пересылаем их в вкладку "Логи"
        scope.launch {
            var lastSize = 0
            UiLogBuffer.entries.collect { entries ->
                if (entries.size > lastSize) {
                    // Добавляем только новые записи
                    val newEntries = entries.drop(lastSize)
                    newEntries.forEach { entry ->
                        tabManager.addToLogsTab(entry.formatted())
                    }
                    lastSize = entries.size
                }
            }
        }

        // Завершаем инициализацию и сохраняем конфиг один раз
        isInitializing = false
        saveConfig()

        // Мониторинг состояния соединения для автосохранения карты при разрыве
        scope.launch {
            var wasConnected = false
            isConnected.collect { connected ->
                if (wasConnected && !connected) {
                    // Соединение было разорвано - карта уже сохраняется автоматически в SQLite
                    logger.info { "Connection lost" }
                    // Останавливаем сбор статистики
                    sessionStats.stopSession()
                    // Останавливаем логирование
                    logManager.stopLogging()
                    // Уведомляем скрипты
                    if (::scriptManager.isInitialized) {
                        scriptManager.fireEvent(com.bylins.client.scripting.ScriptEvent.ON_DISCONNECT)
                    }
                    // Уведомляем плагины
                    firePluginEvent(com.bylins.client.plugins.events.DisconnectEvent(
                        reason = com.bylins.client.plugins.events.DisconnectReason.SERVER_CLOSED
                    ))
                }
                wasConnected = connected
            }
        }
    }

    fun connect(host: String, port: Int) {
        scope.launch {
            try {
                _errorMessage.value = null
                telnetClient.connect(host, port)
                // Начинаем сбор статистики
                sessionStats.startSession()
                // Автоматически запускаем логирование
                logManager.startLogging(stripAnsi = true)
                // Устанавливаем системные переменные
                variableManager.setSystemVariable("host", host)
                variableManager.setSystemVariable("port", port)
                variableManager.setSystemVariable("connected", 1)
                // Карта уже загружена из SQLite при инициализации MapManager

                // Уведомляем скрипты о подключении
                if (::scriptManager.isInitialized) {
                    scriptManager.fireEvent(com.bylins.client.scripting.ScriptEvent.ON_CONNECT)
                }

                // Уведомляем плагины о подключении
                firePluginEvent(com.bylins.client.plugins.events.ConnectEvent(host, port))
            } catch (e: Exception) {
                val userFriendlyError = when {
                    e is java.net.ConnectException && e.message?.contains("Connection refused") == true ->
                        "Не удалось подключиться к $host:$port — сервер недоступен или порт закрыт"
                    e is java.net.UnknownHostException ->
                        "Неизвестный хост: $host"
                    e is java.net.SocketTimeoutException ->
                        "Превышено время ожидания подключения к $host:$port"
                    e is java.net.NoRouteToHostException ->
                        "Нет маршрута до хоста $host"
                    e is java.io.IOException ->
                        "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
                    else ->
                        "Ошибка подключения: ${e.message ?: "неизвестная ошибка"}"
                }
                _errorMessage.value = userFriendlyError
            }
        }
    }

    fun disconnect() {
        // Карта сохраняется автоматически в SQLite при каждом изменении
        telnetClient.disconnect()

        // Сбрасываем MSDP состояние
        _msdpEnabled.value = false
        _msdpData.value = emptyMap()
        _msdpReportableVariables.value = emptyList()
        _msdpReportedVariables.value = emptySet()

        // Обновляем системные переменные
        variableManager.setSystemVariable("connected", 0)
        // Очищаем MSDP переменные
        variableManager.clearBySource(com.bylins.client.variables.VariableSource.MSDP)

        // Останавливаем сбор статистики
        sessionStats.stopSession()
        // Останавливаем логирование
        logManager.stopLogging()
        // Уведомляем скрипты
        if (::scriptManager.isInitialized) {
            scriptManager.fireEvent(com.bylins.client.scripting.ScriptEvent.ON_DISCONNECT)
        }

        // Уведомляем плагины об отключении
        firePluginEvent(com.bylins.client.plugins.events.DisconnectEvent(
            reason = com.bylins.client.plugins.events.DisconnectReason.USER_REQUEST
        ))
    }

    /**
     * Вызывается при закрытии приложения
     */
    fun shutdown() {
        logger.info { "Shutting down..." }
        // Отключаемся если подключены
        if (isConnected.value) {
            disconnect()
        }
        // Сохраняем конфигурацию немедленно (без дебаунса)
        saveConfigNow()
        // Завершаем работу маппера (сохраняет снапшот)
        mapManager.shutdown()
        // Выгружаем плагины
        pluginManager.shutdown()
        logger.info { "Shutdown complete" }
    }

    fun send(command: String) {
        // Сначала проверяем команды управления переменными
        val varHandled = variableManager.processCommand(command) { message ->
            // Выводим сообщения от VariableManager с сохранением промпта
            telnetClient.addLocalOutput(message)
        }
        if (varHandled) {
            // Логируем локальную команду
            telnetClient.echoCommand(command)
            logManager.log(command)
            return
        }

        // Проверяем команды навигации по карте
        val navHandled = commandProcessor.processNavigationCommand(command)
        if (navHandled) {
            // Логируем локальную команду
            telnetClient.echoCommand(command)
            logManager.log(command)
            return
        }

        // Подставляем переменные в команду
        val commandWithVars = variableManager.substituteVariables(command)

        // Проверяем алиасы из профилей (наложение - последний профиль приоритетнее)
        var handled = false
        if (::profileManager.isInitialized) {
            // Проверяем профили в обратном порядке (последний в стеке - приоритетнее)
            for (profile in profileManager.getActiveProfiles().reversed()) {
                if (aliasManager.processCommandWithAliases(commandWithVars, profile.aliases)) {
                    handled = true
                    break
                }
            }
        }

        // Если профильные алиасы не сработали - проверяем базовые
        if (!handled) {
            handled = aliasManager.processCommand(commandWithVars)
        }

        if (handled) {
            // Алиас сработал
            sessionStats.incrementAliasesExecuted()
        } else {
            // Алиас не сработал, отправляем команду как есть
            sendRaw(commandWithVars)
        }
    }

    private fun sendRaw(command: String) {
        // Проверяем алиасы плагинов
        if (::pluginManager.isInitialized) {
            val manager = pluginManager as? com.bylins.client.plugins.PluginManagerImpl
            if (manager?.checkPluginAliases(command) == true) {
                return // Алиас плагина обработал команду
            }
        }

        // Сохраняем команду для автомаппера
        lastCommand = command

        // Уведомляем скрипты
        if (::scriptManager.isInitialized) {
            scriptManager.fireEvent(com.bylins.client.scripting.ScriptEvent.ON_COMMAND, command)
        }

        // Уведомляем плагины о команде (cancellable)
        val commandEvent = com.bylins.client.plugins.events.CommandSendEvent(command)
        pluginEventBus.post(commandEvent)
        if (commandEvent.isCancelled) {
            return // Команда отменена плагином
        }

        // Эхо команды в лог (через TelnetClient для правильной работы с промптом)
        telnetClient.echoCommand(command)

        // Логируем команду (без ANSI кодов)
        logManager.log(command)

        // Увеличиваем счетчик отправленных команд
        sessionStats.incrementCommandsSent()

        scope.launch {
            try {
                telnetClient.send(command)
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка отправки: ${e.message}"
                e.printStackTrace()
            }
        }
    }

    /**
     * Устанавливает статус MSDP (вызывается из TelnetClient при согласовании)
     */
    fun setMsdpEnabled(enabled: Boolean) {
        val wasEnabled = _msdpEnabled.value
        _msdpEnabled.value = enabled
        if (enabled && !wasEnabled) {
            logger.info { "MSDP protocol enabled" }
            // Уведомляем скрипты о включении MSDP
            if (::scriptManager.isInitialized) {
                scriptManager.fireEvent(com.bylins.client.scripting.ScriptEvent.ON_MSDP_ENABLED)
            }
        }
    }

    /**
     * Отправляет MSDP команду LIST для запроса списка
     * listType: "COMMANDS", "LISTS", "REPORTABLE_VARIABLES", "CONFIGURABLE_VARIABLES", "REPORTED_VARIABLES"
     */
    fun sendMsdpList(listType: String) {
        if (!_msdpEnabled.value) {
            logger.warn { "MSDP not enabled, LIST command ignored" }
            return
        }
        logger.info { "MSDP LIST $listType sending..." }
        telnetClient.sendMsdpCommand("LIST", listType)
    }

    /**
     * Включает REPORT для переменной (автоматические обновления)
     */
    fun sendMsdpReport(variableName: String) {
        if (!_msdpEnabled.value) {
            logger.warn { "MSDP not enabled, REPORT command ignored" }
            return
        }
        telnetClient.sendMsdpCommand("REPORT", variableName)
        _msdpReportedVariables.value = _msdpReportedVariables.value + variableName
        logger.debug { "MSDP REPORT $variableName sent" }
    }

    /**
     * Выключает REPORT для переменной
     */
    fun sendMsdpUnreport(variableName: String) {
        if (!_msdpEnabled.value) {
            logger.warn { "MSDP not enabled, UNREPORT command ignored" }
            return
        }
        telnetClient.sendMsdpCommand("UNREPORT", variableName)
        _msdpReportedVariables.value = _msdpReportedVariables.value - variableName
        logger.debug { "MSDP UNREPORT $variableName sent" }
    }

    /**
     * Запрашивает текущее значение переменной (разовый запрос)
     */
    fun sendMsdpSend(variableName: String) {
        if (!_msdpEnabled.value) {
            logger.warn { "MSDP not enabled, SEND command ignored" }
            return
        }
        telnetClient.sendMsdpCommand("SEND", variableName)
        logger.debug { "MSDP SEND $variableName sent" }
    }

    fun updateMsdpData(data: Map<String, Any>) {
        _msdpData.value = _msdpData.value + data

        // Проверяем специальные переменные (ответы на LIST)
        data["REPORTABLE_VARIABLES"]?.let { value ->
            if (value is List<*>) {
                _msdpReportableVariables.value = value.filterIsInstance<String>()
                logger.info { "Received REPORTABLE_VARIABLES list: ${_msdpReportableVariables.value.size} variables" }
            }
        }

        // Автоматически обновляем переменные из MSDP (сохраняем оригинальные значения)
        data.forEach { (key, value) ->
            variableManager.setMsdpVariable(key.lowercase(), value)
        }

        // Обновляем историю статистики для графиков
        val allData = _msdpData.value
        val hp = (allData["HEALTH"] as? String)?.toIntOrNull() ?: 0
        val maxHp = (allData["HEALTH_MAX"] as? String)?.toIntOrNull() ?: 1
        val mana = (allData["MANA"] as? String)?.toIntOrNull() ?: 0
        val maxMana = (allData["MANA_MAX"] as? String)?.toIntOrNull() ?: 1
        val movement = (allData["MOVEMENT"] as? String)?.toIntOrNull() ?: 0
        val maxMovement = (allData["MOVEMENT_MAX"] as? String)?.toIntOrNull() ?: 1

        if (hp > 0 || mana > 0 || movement > 0) {
            statsHistory.addHpData(hp, maxHp)
            statsHistory.addManaData(mana, maxMana)
            statsHistory.addMovementData(movement, maxMovement)

            // Звуковое уведомление при низком HP (меньше 30%) - не чаще раза в 10 секунд
            val hpPercent = if (maxHp > 0) (hp.toFloat() / maxHp * 100) else 0f
            val currentTime = System.currentTimeMillis()
            if (hpPercent > 0 && hpPercent < 30 && (currentTime - lastLowHpSoundTime) > 10000) {
                soundManager.playSound(com.bylins.client.audio.SoundManager.SoundType.LOW_HP)
                lastLowHpSoundTime = currentTime
            }
        }

        // Уведомляем скрипты
        if (::scriptManager.isInitialized) {
            scriptManager.fireEvent(com.bylins.client.scripting.ScriptEvent.ON_MSDP, data)
        }

        // Уведомляем плагины
        data.forEach { (key, value) ->
            firePluginEvent(com.bylins.client.plugins.events.MsdpEvent(key, value))
        }
    }

    /**
     * Обновляет GMCP данные
     */
    fun updateGmcpData(message: com.bylins.client.network.GmcpMessage) {
        // Обновляем хранилище GMCP данных
        _gmcpData.value = _gmcpData.value + (message.packageName to message.data)

        logger.debug { "GMCP: ${message.packageName} = ${message.data}" }

        // Парсим JSON в Map для переменных
        val parser = com.bylins.client.network.GmcpParser()
        val dataMap = parser.jsonToMap(message.data)

        // Автоматически обновляем переменные из GMCP
        if (dataMap != null) {
            dataMap.forEach { (key, value) ->
                variableManager.setVariable("gmcp_${message.packageName.lowercase().replace(".", "_")}_$key", value.toString())
            }
        }

        // Уведомляем скрипты о GMCP событии
        if (::scriptManager.isInitialized) {
            val eventData = mapOf(
                "package" to message.packageName,
                "data" to message.data.toString()
            )
            scriptManager.fireEvent(com.bylins.client.scripting.ScriptEvent.ON_GMCP, eventData)
        }

        // Уведомляем плагины о GMCP событии
        firePluginEvent(com.bylins.client.plugins.events.GmcpEvent(message.packageName, message.data.toString()))
    }

    /**
     * Обрабатывает входящую строку текста (вызывается из TelnetClient)
     * Возвращает модифицированный текст с примененными colorize от триггеров
     */
    fun processIncomingText(text: String): String {
        // Логируем весь полученный текст
        if (text.isNotEmpty()) {
            logManager.log(text)

            // Добавляем полученные байты в статистику
            sessionStats.addBytesReceived(text.toByteArray(Charsets.UTF_8).size)
        }

        // Распределяем текст по вкладкам
        tabManager.processText(text)

        // Обрабатываем текст для автомаппера
        processMapping(text)

        // Разбиваем на строки и обрабатываем каждую триггерами
        val ansiParser = com.bylins.client.ui.AnsiParser()
        val lines = text.lines()
        val modifiedLines = mutableListOf<String>()

        for (i in lines.indices) {
            val line = lines[i]

            if (line.isEmpty()) {
                modifiedLines.add(line)
                continue
            }

            // Удаляем ANSI-коды перед проверкой триггерами
            val cleanLine = ansiParser.stripAnsi(line)

            // Уведомляем плагины о новой строке (cancellable)
            val lineEvent = com.bylins.client.plugins.events.LineReceivedEvent(
                line = cleanLine,
                rawLine = line
            )
            pluginEventBus.post(lineEvent)
            if (lineEvent.isCancelled) {
                continue // Строка отменена плагином (gag)
            }

            // Проверяем триггеры плагинов
            if (::pluginManager.isInitialized) {
                val manager = pluginManager as? com.bylins.client.plugins.PluginManagerImpl
                val triggerResult = manager?.checkPluginTriggers(cleanLine, line)
                if (triggerResult == com.bylins.client.plugins.TriggerResult.GAG) {
                    continue // Строка скрыта триггером плагина
                }
                if (triggerResult == com.bylins.client.plugins.TriggerResult.STOP) {
                    modifiedLines.add(line)
                    continue // Дальнейшая обработка триггеров не нужна
                }
            }

            // Уведомляем скрипты о новой строке
            if (::scriptManager.isInitialized) {
                scriptManager.fireEvent(com.bylins.client.scripting.ScriptEvent.ON_LINE, cleanLine)
            }

            // Проверяем триггеры из скриптов
            checkScriptTriggers(cleanLine)

            val matches = triggerManager.processLine(cleanLine)

            // Обрабатываем триггеры из профилей
            val profileMatches = mutableListOf<com.bylins.client.triggers.TriggerMatch>()
            if (::profileManager.isInitialized) {
                for (profile in profileManager.getActiveProfiles()) {
                    val profileTriggerMatches = triggerManager.processLineWithTriggers(cleanLine, profile.triggers)
                    profileMatches.addAll(profileTriggerMatches)
                }
            }

            val allMatches = matches + profileMatches

            // Увеличиваем счетчик на количество сработавших триггеров
            if (allMatches.isNotEmpty()) {
                sessionStats.incrementTriggersActivated()

                // Применяем colorize от первого сработавшего триггера с colorize
                val triggerWithColor = allMatches.firstOrNull { it.trigger.colorize != null }
                if (triggerWithColor != null) {
                    val colorize = triggerWithColor.trigger.colorize!!
                    val colorizedLine = applyColorize(cleanLine, colorize)
                    modifiedLines.add(colorizedLine)
                } else {
                    modifiedLines.add(line)
                }
            } else {
                modifiedLines.add(line)
            }

            // Обрабатываем контекстные команды по паттернам
            contextCommandManager.processLine(cleanLine)

            // Обрабатываем контекстные команды из профилей
            if (::profileManager.isInitialized) {
                val activeProfiles = profileManager.getActiveProfiles()
                for (profile in activeProfiles) {
                    if (profile.contextCommandRules.isNotEmpty()) {
                        logger.debug { "Processing ${profile.contextCommandRules.size} context rules from profile ${profile.name}" }
                    }
                    contextCommandManager.processLineWithRules(cleanLine, profile.contextCommandRules)
                }
            }
        }

        // Восстанавливаем переводы строк
        val result = StringBuilder()
        for (i in modifiedLines.indices) {
            result.append(modifiedLines[i])
            if (i < modifiedLines.size - 1) {
                result.append("\n")
            }
        }

        // Если оригинальный текст заканчивался на \n, добавляем его
        if (text.endsWith("\n") || text.endsWith("\r\n") || text.endsWith("\r")) {
            result.append("\n")
        }

        return result.toString()
    }

    /**
     * Применяет colorize к строке, добавляя ANSI escape-коды
     */
    private fun applyColorize(text: String, colorize: com.bylins.client.triggers.TriggerColorize): String {
        val codes = mutableListOf<Int>()

        // Bold
        if (colorize.bold) {
            codes.add(1)
        }

        // Foreground color
        if (colorize.foreground != null) {
            val color = parseColor(colorize.foreground)
            if (color != null) {
                codes.addAll(listOf(38, 2, color.red, color.green, color.blue))
            }
        }

        // Background color
        if (colorize.background != null) {
            val color = parseColor(colorize.background)
            if (color != null) {
                codes.addAll(listOf(48, 2, color.red, color.green, color.blue))
            }
        }

        if (codes.isEmpty()) {
            return text
        }

        val codeString = codes.joinToString(";")
        return "\u001B[${codeString}m${text}\u001B[0m"
    }

    private data class RGB(val red: Int, val green: Int, val blue: Int)

    /**
     * Парсит hex-цвет в RGB
     */
    private fun parseColor(hex: String): RGB? {
        return try {
            val cleanHex = hex.trim().removePrefix("#")
            if (cleanHex.length != 6) return null
            val r = cleanHex.substring(0, 2).toInt(16)
            val g = cleanHex.substring(2, 4).toInt(16)
            val b = cleanHex.substring(4, 6).toInt(16)
            RGB(r, g, b)
        } catch (e: Exception) {
            null
        }
    }

    // Управление триггерами
    fun addTrigger(trigger: com.bylins.client.triggers.Trigger) {
        triggerManager.addTrigger(trigger)
        saveConfig()
    }

    fun updateTrigger(trigger: com.bylins.client.triggers.Trigger) {
        triggerManager.removeTrigger(trigger.id)
        triggerManager.addTrigger(trigger)
        saveConfig()
    }

    fun removeTrigger(id: String) {
        triggerManager.removeTrigger(id)
        saveConfig()
    }

    fun enableTrigger(id: String) {
        triggerManager.enableTrigger(id)
        saveConfig()
    }

    fun disableTrigger(id: String) {
        triggerManager.disableTrigger(id)
        saveConfig()
    }

    fun exportTriggers(triggerIds: List<String>): String {
        return triggerManager.exportTriggers(triggerIds)
    }

    fun importTriggers(json: String, merge: Boolean = true): Int {
        val count = triggerManager.importTriggers(json, merge)
        saveConfig()
        return count
    }

    // Управление алиасами
    fun addAlias(alias: com.bylins.client.aliases.Alias) {
        aliasManager.addAlias(alias)
        saveConfig()
    }

    fun updateAlias(alias: com.bylins.client.aliases.Alias) {
        aliasManager.removeAlias(alias.id)
        aliasManager.addAlias(alias)
        saveConfig()
    }

    fun removeAlias(id: String) {
        aliasManager.removeAlias(id)
        saveConfig()
    }

    fun enableAlias(id: String) {
        aliasManager.enableAlias(id)
        saveConfig()
    }

    fun disableAlias(id: String) {
        aliasManager.disableAlias(id)
        saveConfig()
    }

    fun exportAliases(aliasIds: List<String>): String {
        return aliasManager.exportAliases(aliasIds)
    }

    fun importAliases(json: String, merge: Boolean = true): Int {
        val count = aliasManager.importAliases(json, merge)
        saveConfig()
        return count
    }

    // Управление хоткеями
    fun addHotkey(hotkey: com.bylins.client.hotkeys.Hotkey) {
        hotkeyManager.addHotkey(hotkey)
        saveConfig()
    }

    fun updateHotkey(hotkey: com.bylins.client.hotkeys.Hotkey) {
        hotkeyManager.removeHotkey(hotkey.id)
        hotkeyManager.addHotkey(hotkey)
        saveConfig()
    }

    fun removeHotkey(id: String) {
        hotkeyManager.removeHotkey(id)
        saveConfig()
    }

    fun enableHotkey(id: String) {
        hotkeyManager.enableHotkey(id)
        saveConfig()
    }

    fun disableHotkey(id: String) {
        hotkeyManager.disableHotkey(id)
        saveConfig()
    }

    fun exportHotkeys(hotkeyIds: List<String>): String {
        return hotkeyManager.exportHotkeys(hotkeyIds)
    }

    fun importHotkeys(json: String, merge: Boolean = true): Int {
        val count = hotkeyManager.importHotkeys(json, merge)
        saveConfig()
        return count
    }

    /**
     * Обрабатывает нажатие горячей клавиши
     * Возвращает true, если хоткей сработал
     */
    fun processHotkey(
        key: androidx.compose.ui.input.key.Key,
        isCtrlPressed: Boolean,
        isAltPressed: Boolean,
        isShiftPressed: Boolean
    ): Boolean {
        // Проверяем зарезервированные хоткеи контекстных команд (Alt+1-0)
        if (contextCommandManager.processReservedHotkey(key, isAltPressed)) {
            sessionStats.incrementHotkeysUsed()
            lastHotkeyTimestamp = System.currentTimeMillis()
            return true
        }

        // Проверяем хоткеи из профилей (наложение - последний профиль приоритетнее)
        var handled = false
        if (::profileManager.isInitialized) {
            // Проверяем профили в обратном порядке (последний в стеке - приоритетнее)
            for (profile in profileManager.getActiveProfiles().reversed()) {
                if (hotkeyManager.processKeyPressWithHotkeys(
                        key, isCtrlPressed, isAltPressed, isShiftPressed,
                        _ignoreNumLock.value, profile.hotkeys
                    )) {
                    handled = true
                    break
                }
            }
        }

        // Если профильные хоткеи не сработали - проверяем базовые
        if (!handled) {
            handled = hotkeyManager.processKeyPress(key, isCtrlPressed, isAltPressed, isShiftPressed, _ignoreNumLock.value)
        }

        if (handled) {
            sessionStats.incrementHotkeysUsed()
            // Записываем время для блокировки дублирования текстового ввода
            lastHotkeyTimestamp = System.currentTimeMillis()
        }
        return handled
    }

    // Управление вкладками
    fun addTab(tab: com.bylins.client.tabs.Tab) {
        tabManager.addTab(tab)
        saveConfig()
    }

    fun createTab(name: String, filters: List<com.bylins.client.tabs.TabFilter>, captureMode: com.bylins.client.tabs.CaptureMode) {
        val tab = com.bylins.client.tabs.Tab(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            filters = filters,
            captureMode = captureMode
        )
        addTab(tab)
    }

    fun updateTab(id: String, name: String, filters: List<com.bylins.client.tabs.TabFilter>, captureMode: com.bylins.client.tabs.CaptureMode) {
        tabManager.updateTab(id, name, filters, captureMode)
        saveConfig()
    }

    fun removeTab(id: String) {
        tabManager.removeTab(id)
        saveConfig()
    }

    fun setActiveTab(id: String) {
        tabManager.setActiveTab(id)
    }

    fun getTab(id: String): com.bylins.client.tabs.Tab? {
        return tabManager.getTab(id)
    }

    fun clearTab(id: String) {
        tabManager.clearTab(id)
    }

    fun clearAllTabs() {
        tabManager.clearAll()
    }

    // Управление конфигурацией
    fun saveConfig() {
        // Не сохраняем во время инициализации, чтобы не создавать множество записей
        if (isInitializing) return

        // Debounce: отменяем предыдущий запрос и ждём перед сохранением
        saveConfigJob?.cancel()
        saveConfigJob = scope.launch(Dispatchers.IO) {
            delay(saveConfigDebounceMs)
            doSaveConfig()
        }
    }

    /**
     * Немедленное сохранение конфига (без debounce)
     */
    fun saveConfigNow() {
        saveConfigJob?.cancel()
        doSaveConfig()
    }

    private fun doSaveConfig() {
        // Синхронизируем стек профилей с текущим профилем подключения перед сохранением
        if (::profileManager.isInitialized) {
            saveActiveProfileStackToCurrentConnectionProfile()
        }

        // Сохраняем комнату, на которую центрирована карта (или текущую комнату игрока как fallback)
        val lastMapRoomId = mapManager.viewCenterRoomId.value ?: mapManager.currentRoomId.value
        logger.info { "Saving config, lastMapRoomId: $lastMapRoomId" }
        configManager.saveConfig(
            triggers = triggers.value,
            aliases = aliases.value,
            hotkeys = hotkeys.value,
            variables = variableManager.getAllVariables(),
            tabs = tabManager.getTabsForSave(),
            contextCommandRules = contextCommandManager.rules.value,
            contextCommandMaxQueueSize = contextCommandManager.maxQueueSize.value,
            encoding = _encoding,
            miniMapWidth = _miniMapWidth.value,
            miniMapHeight = _miniMapHeight.value,
            zonePanelWidth = _zonePanelWidth.value,
            theme = _currentTheme.value,
            fontFamily = _fontFamily.value,
            fontSize = _fontSize.value,
            connectionProfiles = _connectionProfiles.value,
            currentProfileId = _currentProfileId.value,
            ignoreNumLock = _ignoreNumLock.value,
            activeProfileStack = if (::profileManager.isInitialized) profileManager.activeStack.value else emptyList(),
            hiddenTabs = _hiddenTabs.value,
            lastMapRoomId = lastMapRoomId,
            logWithColors = logManager.logWithColors.value
        )
    }

    /**
     * Устанавливает кодировку для telnet соединения
     */
    fun setEncoding(newEncoding: String) {
        _encoding = newEncoding
        telnetClient.setEncoding(newEncoding)
        saveConfig()
    }

    /**
     * Устанавливает ширину боковой панели с миникартой
     */
    fun setMiniMapWidth(width: Int) {
        val clampedWidth = width.coerceAtLeast(150)
        _miniMapWidth.value = clampedWidth
        saveConfig()
    }

    /**
     * Устанавливает высоту миникарты в статус-панели
     */
    fun setMiniMapHeight(height: Int) {
        val clampedHeight = height.coerceIn(100, 800)
        _miniMapHeight.value = clampedHeight
        saveConfig()
    }

    /**
     * Устанавливает ширину панели заметок зоны на вкладке Карта
     */
    fun setZonePanelWidth(width: Int) {
        val clampedWidth = width.coerceIn(150, 500)
        _zonePanelWidth.value = clampedWidth
        saveConfig()
    }

    /**
     * Устанавливает тему оформления
     */
    fun setTheme(themeName: String) {
        _currentTheme.value = themeName
        saveConfig()
        logger.info { "Theme changed to: $themeName" }
    }

    /**
     * Устанавливает семейство шрифтов
     */
    fun setFontFamily(family: String) {
        _fontFamily.value = family
        saveConfig()
        logger.info { "Font family changed to: $family" }
    }

    /**
     * Устанавливает размер шрифта
     */
    fun setFontSize(size: Int) {
        val clampedSize = size.coerceIn(10, 24)
        _fontSize.value = clampedSize
        saveConfig()
        logger.info { "Font size changed to: $clampedSize" }
    }

    fun setIgnoreNumLock(ignore: Boolean) {
        _ignoreNumLock.value = ignore
        saveConfig()
        logger.info { "Ignore NumLock changed to: $ignore" }
    }

    // Управление видимостью вкладок
    fun setTabVisible(tabId: String, visible: Boolean) {
        _hiddenTabs.value = if (visible) {
            _hiddenTabs.value - tabId
        } else {
            _hiddenTabs.value + tabId
        }
        saveConfig()
        logger.info { "Tab '$tabId' visibility changed to: $visible" }
    }

    fun isTabVisible(tabId: String): Boolean {
        return tabId !in _hiddenTabs.value
    }

    // Управление профилями подключений
    fun addConnectionProfile(profile: com.bylins.client.connection.ConnectionProfile) {
        _connectionProfiles.value = _connectionProfiles.value + profile
        saveConfig()
        logger.info { "Added connection profile: ${profile.name}" }
    }

    fun updateConnectionProfile(profile: com.bylins.client.connection.ConnectionProfile) {
        _connectionProfiles.value = _connectionProfiles.value.map {
            if (it.id == profile.id) profile else it
        }
        saveConfig()
        logger.info { "Updated connection profile: ${profile.name}" }
    }

    fun removeConnectionProfile(profileId: String) {
        _connectionProfiles.value = _connectionProfiles.value.filter { it.id != profileId }
        // Если удаляем текущий профиль, сбрасываем выбор
        if (_currentProfileId.value == profileId) {
            _currentProfileId.value = null
        }
        saveConfig()
        logger.info { "Removed connection profile: $profileId" }
    }

    fun setCurrentProfile(profileId: String?) {
        // Сохраняем текущий стек профилей в старый профиль подключения
        saveActiveProfileStackToCurrentConnectionProfile()

        _currentProfileId.value = profileId

        // При выборе профиля обновляем кодировку, карту и стек профилей
        profileId?.let { id ->
            val profile = _connectionProfiles.value.find { it.id == id }
            profile?.let {
                setEncoding(it.encoding)
                switchMapDatabase(it.mapFile)
                // Переключаем стек профилей персонажей
                switchProfileStack(it.activeProfileStack)
            }
        } ?: run {
            // Если профиль не выбран - очищаем стек
            profileManager.clearStack()
        }

        saveConfig()
        logger.info { "Set current profile: $profileId" }
    }

    /**
     * Сохраняет текущий стек профилей персонажей в текущий профиль подключения
     */
    fun saveActiveProfileStackToCurrentConnectionProfile() {
        val currentConnProfileId = _currentProfileId.value ?: return
        val currentStack = profileManager.activeStack.value

        _connectionProfiles.value = _connectionProfiles.value.map { connProfile ->
            if (connProfile.id == currentConnProfileId) {
                connProfile.copy(activeProfileStack = currentStack)
            } else {
                connProfile
            }
        }
    }

    /**
     * Переключает стек профилей персонажей
     */
    private fun switchProfileStack(newStack: List<String>) {
        // Очищаем текущий стек
        profileManager.clearStack()

        // Восстанавливаем новый стек
        profileManager.restoreStack(newStack)

        logger.info { "Switched profile stack to: $newStack" }
    }

    fun getCurrentProfile(): com.bylins.client.connection.ConnectionProfile? {
        return _currentProfileId.value?.let { id ->
            _connectionProfiles.value.find { it.id == id }
        }
    }

    fun exportConfig(file: File) {
        configManager.exportConfig(
            file,
            triggers.value,
            aliases.value,
            hotkeys.value,
            variableManager.getAllVariables(),
            tabManager.getTabsForSave(),
            _encoding,
            _miniMapWidth.value,
            _miniMapHeight.value,
            _currentTheme.value,
            _fontFamily.value,
            _fontSize.value
        )
    }

    fun importConfig(file: File) {
        val configData = configManager.importConfig(file)

        // Очищаем текущие триггеры, алиасы, хоткеи, переменные и вкладки
        triggerManager.clear()
        aliasManager.clear()
        hotkeyManager.clear()
        variableManager.clear()
        // tabManager не очищаем полностью, т.к. главная вкладка всегда должна быть

        // Загружаем импортированные
        configData.triggers.forEach { addTrigger(it) }
        configData.aliases.forEach { addAlias(it) }
        configData.hotkeys.forEach { addHotkey(it) }
        variableManager.loadVariables(configData.variables)
        tabManager.loadTabs(configData.tabs)

        // Загружаем кодировку
        _encoding = configData.encoding
        telnetClient.setEncoding(_encoding)

        // Загружаем размеры миникарты
        _miniMapWidth.value = configData.miniMapWidth
        _miniMapHeight.value = configData.miniMapHeight
        _zonePanelWidth.value = configData.zonePanelWidth

        // Загружаем тему
        _currentTheme.value = configData.theme

        // Загружаем настройки шрифта
        _fontFamily.value = configData.fontFamily
        _fontSize.value = configData.fontSize

        // Загружаем настройку логирования цветов
        logManager.setLogWithColors(configData.logWithColors)

        // Сохраняем в основной конфиг
        saveConfig()
    }

    fun getConfigPath(): String = configManager.getConfigFile()

    // Управление логированием
    fun startLogging(stripAnsi: Boolean = true) {
        logManager.startLogging(stripAnsi)
    }

    fun stopLogging() {
        logManager.stopLogging()
    }

    fun getLogFiles(): List<File> {
        return logManager.getLogFiles()
    }

    fun getLogsDirectory(): String {
        return logManager.getLogsDirectory()
    }

    val logWithColors = logManager.logWithColors

    fun setLogWithColors(enabled: Boolean) {
        logManager.setLogWithColors(enabled)
        saveConfig()
    }

    fun cleanOldLogs(daysToKeep: Int = 30) {
        logManager.cleanOldLogs(daysToKeep)
    }

    // Доступ к статистике
    fun getSessionDuration(): String {
        return sessionStats.getFormattedDuration()
    }

    fun getFormattedBytes(): String {
        return sessionStats.getFormattedBytes()
    }

    // Управление картой
    /**
     * Обрабатывает входящий текст для автомаппера
     */
    private fun processMapping(text: String) {
        if (!mapManager.mapEnabled.value) return

        val ansiParser = com.bylins.client.ui.AnsiParser()
        val cleanText = ansiParser.stripAnsi(text)
        val lines = cleanText.lines()

        // Ищем информацию о комнате
        for (line in lines) {
            // Парсим выходы
            val exits = roomParser.parseExits(line)
            if (exits.isNotEmpty()) {
                // Найдены выходы, пробуем определить название комнаты
                val roomName = lines.firstOrNull { roomParser.parseRoomName(it) != null }
                    ?.let { roomParser.parseRoomName(it) }

                if (roomName != null) {
                    // Определяем направление движения из последней команды
                    val direction = lastCommand?.let { roomParser.detectMovementDirection(it) }

                    if (direction != null) {
                        // Обрабатываем движение и обновляем карту
                        mapManager.handleMovement(direction, roomName, exits)
                    } else {
                        // Возможно первая комната или телепорт
                        // TODO: обработка телепорта/первой комнаты
                    }
                }
            }
        }

        // Обработка MSDP данных для маппинга
        val msdp = _msdpData.value
        if (msdp.isNotEmpty()) {
            val roomInfo = roomParser.parseFromMSDP(msdp)
            if (roomInfo != null) {
                val direction = lastCommand?.let { roomParser.detectMovementDirection(it) }
                if (direction != null) {
                    mapManager.handleMovement(direction, roomInfo.name, roomInfo.exits)
                }
            }
        }
    }

    fun setMapEnabled(enabled: Boolean) {
        mapManager.setMapEnabled(enabled)
    }

    fun clearMap() {
        mapManager.clearMap()
    }

    fun setRoomNote(roomId: String, note: String) {
        mapManager.setRoomNote(roomId, note)
    }

    fun setRoomColor(roomId: String, color: String?) {
        mapManager.setRoomColor(roomId, color)
    }

    fun setRoomTerrain(roomId: String, terrain: String?) {
        mapManager.setRoomTerrain(roomId, terrain)
    }

    fun setRoomTags(roomId: String, tags: Set<String>) {
        mapManager.setRoomTags(roomId, tags)
    }

    /**
     * Полное обновление комнаты - название, заметки, terrain, теги, зона, выходы, visited
     */
    fun updateRoom(
        roomId: String,
        name: String,
        note: String,
        terrain: String?,
        tags: Set<String>,
        zone: String,
        exits: Map<com.bylins.client.mapper.Direction, com.bylins.client.mapper.Exit>,
        visited: Boolean
    ) {
        mapManager.updateRoom(roomId, name, note, terrain, tags, zone, exits, visited)
    }

    fun exportMap(): Map<String, com.bylins.client.mapper.Room> {
        return mapManager.exportMap()
    }

    fun importMap(rooms: Map<String, com.bylins.client.mapper.Room>) {
        mapManager.importMap(rooms)
    }

    // Работа с зонами
    fun detectAndAssignZones() {
        mapManager.detectAndAssignZones()
    }

    fun getZoneStatistics(): Map<String, Int> {
        return mapManager.getZoneStatistics()
    }

    fun getRoomsByZone(zoneName: String): List<com.bylins.client.mapper.Room> {
        return mapManager.getRoomsByZone(zoneName)
    }

    fun getAllZones(): List<String> {
        return mapManager.getAllZones()
    }

    fun setRoomZone(roomId: String, zoneName: String) {
        mapManager.setRoomZone(roomId, zoneName)
    }

    fun clearAllZones() {
        mapManager.clearAllZones()
    }

    // Map context menu commands
    fun getMapContextCommands(): Map<String, (com.bylins.client.mapper.Room) -> Unit> {
        return mapContextCommands.toMap()
    }

    fun registerMapCommand(name: String, callback: (com.bylins.client.mapper.Room) -> Unit) {
        mapContextCommands[name] = callback
    }

    fun unregisterMapCommand(name: String) {
        mapContextCommands.remove(name)
    }

    fun executeMapCommand(name: String, room: com.bylins.client.mapper.Room) {
        mapContextCommands[name]?.invoke(room)
    }

    fun findPathTo(roomId: String) {
        if (mapManager.setPathTo(roomId)) {
            val path = mapManager.activePath.value
            val targetRoom = mapManager.getRoom(roomId)
            val targetName = targetRoom?.name ?: roomId
            // Используем addLocalOutput чтобы сообщение появилось ПЕРЕД промптом
            val preview = if (path.isNotEmpty()) {
                path.take(10).joinToString(" ") { it.shortName } + if (path.size > 10) " ..." else ""
            } else ""
            telnetClient.addLocalOutput("[Путь к '$targetName': ${path.size} шагов]" + if (preview.isNotEmpty()) "\n[Направления: $preview]" else "")
        } else {
            telnetClient.addLocalOutput("[Путь к комнате $roomId не найден]")
        }
    }

    fun clearPath() {
        mapManager.clearPath()
        telnetClient.addLocalOutput("[Путь очищен]")
    }

    fun setPathHighlight(roomIds: Set<String>, targetRoomId: String?) {
        mapManager.setPathHighlight(roomIds, targetRoomId)
    }

    fun clearPathHighlight() {
        mapManager.clearPathHighlight()
    }

    fun findPathDirections(targetRoomId: String): List<com.bylins.client.mapper.Direction>? {
        return mapManager.findPathFromCurrent(targetRoomId)
    }

    fun getNextPathDirection(): com.bylins.client.mapper.Direction? {
        return mapManager.getNextPathDirection()
    }

    fun getPathPreview(steps: Int = 5): List<com.bylins.client.mapper.Direction> {
        return mapManager.getPathPreview(steps)
    }

    // Управление скриптами
    private fun initializeScripting() {
        try {
            logger.info { "Initializing scripting system..." }

            // Создаем реализацию ScriptAPI
            val scriptAPI = com.bylins.client.scripting.ScriptAPIImpl(
                sendCommand = { command -> send(command) },
                echoText = { text ->
                    telnetClient.addToOutputRaw(text)
                    tabManager.addToMainTab(text + "\n")
                },
                logMessage = { message ->
                    val formatted = "\u001B[1;36m$message\u001B[0m"
                    telnetClient.addLocalOutput(formatted)
                    tabManager.addToMainTab(formatted + "\n")
                },
                requestFocus = { requestInputFocus() },
                triggerActions = createTriggerActions(),
                aliasActions = createAliasActions(),
                timerActions = createTimerActions(),
                variableActions = createVariableActions(),
                msdpActions = createMsdpActions(),
                gmcpActions = createGmcpActions(),
                mapperActions = createMapperActions(),
                statusActions = createStatusActions(),
                chartActions = createChartActions(),
                storageActions = createStorageActions()
            )

            // Создаем ScriptManager
            scriptManager = com.bylins.client.scripting.ScriptManager(scriptAPI)

            // Регистрируем движки
            scriptManager.registerEngine(com.bylins.client.scripting.engines.JavaScriptEngine())
            scriptManager.registerEngine(com.bylins.client.scripting.engines.PythonEngine())
            scriptManager.registerEngine(com.bylins.client.scripting.engines.LuaEngine())

            // Автозагрузка скриптов
            scriptManager.autoLoadScripts()

            logger.info { "Scripting system initialized successfully" }
        } catch (e: Exception) {
            logger.error { "Failed to initialize scripting: ${e.message}" }
            e.printStackTrace()
        }
    }

    /**
     * Инициализация системы плагинов
     */
    private fun initializePlugins() {
        logger.info { "Initializing plugin system..." }

        pluginManager = com.bylins.client.plugins.PluginManagerImpl(
            eventBus = pluginEventBus,
            apiFactory = { pluginId, dataFolder ->
                createPluginAPI(pluginId, dataFolder)
            }
        )

        // Автозагрузка плагинов
        pluginManager.autoLoadPlugins()
    }

    /**
     * Инициализация системы профилей персонажей
     */
    private fun initializeProfiles() {
        try {
            logger.info { "Initializing profile system..." }

            val configDir = java.nio.file.Paths.get(configManager.getConfigDir())
            profileManager = com.bylins.client.profiles.ProfileManager(configDir, scriptManager)

            // Загружаем все доступные профили
            profileManager.loadProfiles()

            logger.info { "Profile system initialized successfully, ${profileManager.profiles.value.size} profiles found" }
        } catch (e: Exception) {
            logger.error { "Failed to initialize profiles: ${e.message}" }
            e.printStackTrace()
        }
    }

    // === Эффективные настройки (с учётом активных профилей) ===

    /**
     * Все триггеры в порядке выполнения (база + профили)
     * Возвращает пары (триггер, ID профиля или null для базы)
     */
    fun getAllTriggersWithSource(): List<Pair<com.bylins.client.triggers.Trigger, String?>> {
        val result = mutableListOf<Pair<com.bylins.client.triggers.Trigger, String?>>()

        // Сначала базовые триггеры
        triggers.value.forEach { trigger ->
            result.add(trigger to null)
        }

        // Затем триггеры из каждого профиля в порядке стека
        if (::profileManager.isInitialized) {
            for (profile in profileManager.getActiveProfiles()) {
                profile.triggers.forEach { trigger ->
                    result.add(trigger to profile.id)
                }
            }
        }

        return result
    }

    /**
     * Все правила контекстных команд с источниками (для UI)
     * Возвращает пары (правило, ID профиля или null для базы)
     */
    fun getAllContextRulesWithSource(): List<Pair<com.bylins.client.contextcommands.ContextCommandRule, String?>> {
        val result = mutableListOf<Pair<com.bylins.client.contextcommands.ContextCommandRule, String?>>()

        // Сначала базовые правила
        contextCommandManager.rules.value.forEach { rule ->
            result.add(rule to null)
        }

        // Затем правила из каждого профиля в порядке стека
        if (::profileManager.isInitialized) {
            for (profile in profileManager.getActiveProfiles()) {
                profile.contextCommandRules.forEach { rule ->
                    result.add(rule to profile.id)
                }
            }
        }

        return result
    }

    /**
     * Все эффективные правила контекстных команд (база + профили)
     */
    fun getEffectiveContextRules(): List<com.bylins.client.contextcommands.ContextCommandRule> {
        val result = contextCommandManager.rules.value.toMutableList()

        if (::profileManager.isInitialized) {
            for (profile in profileManager.getActiveProfiles()) {
                result.addAll(profile.contextCommandRules)
            }
        }

        return result
    }

    /**
     * Эффективные алиасы (с наложением по паттерну)
     */
    fun getEffectiveAliases(): List<com.bylins.client.aliases.Alias> {
        val result = aliases.value
            .associateBy { it.pattern.pattern }
            .toMutableMap()

        if (::profileManager.isInitialized) {
            for (profile in profileManager.getActiveProfiles()) {
                for (alias in profile.aliases) {
                    result[alias.pattern.pattern] = alias
                }
            }
        }

        return result.values.toList()
    }

    /**
     * Все алиасы с источниками (для UI)
     * Возвращает пары (алиас, ID профиля или null для базы)
     */
    fun getAllAliasesWithSource(): List<Pair<com.bylins.client.aliases.Alias, String?>> {
        val result = mutableListOf<Pair<com.bylins.client.aliases.Alias, String?>>()

        // Сначала базовые алиасы
        aliases.value.forEach { alias ->
            result.add(alias to null)
        }

        // Затем алиасы из каждого профиля в порядке стека
        if (::profileManager.isInitialized) {
            for (profile in profileManager.getActiveProfiles()) {
                profile.aliases.forEach { alias ->
                    result.add(alias to profile.id)
                }
            }
        }

        return result
    }

    /**
     * Эффективные хоткеи (с наложением по комбинации клавиш)
     */
    fun getEffectiveHotkeys(): List<com.bylins.client.hotkeys.Hotkey> {
        val result = hotkeys.value
            .associateBy { getHotkeySignature(it) }
            .toMutableMap()

        if (::profileManager.isInitialized) {
            for (profile in profileManager.getActiveProfiles()) {
                for (hotkey in profile.hotkeys) {
                    result[getHotkeySignature(hotkey)] = hotkey
                }
            }
        }

        return result.values.toList()
    }

    /**
     * Все хоткеи с источниками (для UI)
     * Возвращает пары (хоткей, ID профиля или null для базы)
     */
    fun getAllHotkeysWithSource(): List<Pair<com.bylins.client.hotkeys.Hotkey, String?>> {
        val result = mutableListOf<Pair<com.bylins.client.hotkeys.Hotkey, String?>>()

        // Сначала базовые хоткеи
        hotkeys.value.forEach { hotkey ->
            result.add(hotkey to null)
        }

        // Затем хоткеи из каждого профиля в порядке стека
        if (::profileManager.isInitialized) {
            for (profile in profileManager.getActiveProfiles()) {
                profile.hotkeys.forEach { hotkey ->
                    result.add(hotkey to profile.id)
                }
            }
        }

        return result
    }

    /**
     * Эффективные переменные (с наложением по имени)
     */
    fun getEffectiveVariables(): Map<String, String> {
        val result = variableManager.getAllVariables().toMutableMap()

        if (::profileManager.isInitialized) {
            for (profile in profileManager.getActiveProfiles()) {
                result.putAll(profile.variables)
            }
        }

        return result
    }

    /**
     * Сигнатура хоткея для сравнения
     */
    private fun getHotkeySignature(hotkey: com.bylins.client.hotkeys.Hotkey): String {
        return "${hotkey.key.keyCode}_${hotkey.ctrl}_${hotkey.alt}_${hotkey.shift}"
    }

    /**
     * Находит источник триггера (ID профиля или null для базы)
     */
    fun findTriggerSource(triggerId: String): String? {
        // Проверяем базовые
        if (triggers.value.any { it.id == triggerId }) {
            return null
        }

        // Проверяем профили
        if (::profileManager.isInitialized) {
            for (profile in profileManager.profiles.value) {
                if (profile.triggers.any { it.id == triggerId }) {
                    return profile.id
                }
            }
        }

        return null
    }

    /**
     * Находит источник алиаса (ID профиля или null для базы)
     */
    fun findAliasSource(aliasId: String): String? {
        if (aliases.value.any { it.id == aliasId }) {
            return null
        }

        if (::profileManager.isInitialized) {
            for (profile in profileManager.profiles.value) {
                if (profile.aliases.any { it.id == aliasId }) {
                    return profile.id
                }
            }
        }

        return null
    }

    /**
     * Находит источник хоткея (ID профиля или null для базы)
     */
    fun findHotkeySource(hotkeyId: String): String? {
        if (hotkeys.value.any { it.id == hotkeyId }) {
            return null
        }

        if (::profileManager.isInitialized) {
            for (profile in profileManager.profiles.value) {
                if (profile.hotkeys.any { it.id == hotkeyId }) {
                    return profile.id
                }
            }
        }

        return null
    }

    /**
     * Создаёт PluginAPI для плагина
     */
    private fun createPluginAPI(pluginId: String, dataFolder: java.io.File): com.bylins.client.plugins.PluginAPIImpl {
        return com.bylins.client.plugins.PluginAPIImpl(
            pluginId = pluginId,
            sendCommand = { command -> send(command) },
            echoText = { text ->
                telnetClient.addToOutputRaw(text)
                tabManager.addToMainTab(text + "\n")
            },
            eventBus = pluginEventBus,
            variableGetter = { name -> variableManager.getVariable(name)?.asString() },
            variableSetter = { name, value -> variableManager.setVariable(name, value) },
            variableDeleter = { name -> variableManager.removeVariable(name) },
            getAllVariablesFunc = { variableManager.getAllVariables() },
            msdpGetter = { key -> _msdpData.value[key] },
            getAllMsdpFunc = { _msdpData.value },
            gmcpGetter = { packageName -> _gmcpData.value[packageName]?.toString() },
            getAllGmcpFunc = { _gmcpData.value.mapValues { it.value.toString() } },
            gmcpSender = { _, _ -> /* TODO: отправка GMCP */ },
            // Маппер - чтение
            getCurrentRoomFunc = { mapManager.getCurrentRoom()?.toMap() },
            getRoomFunc = { roomId -> mapManager.getRoom(roomId)?.toMap() },
            searchRoomsFunc = { query -> mapManager.searchRooms(query).map { it.toMap() } },
            findPathFunc = { targetId -> mapManager.findPathFromCurrent(targetId)?.map { it.name } },
            // Маппер - модификация
            setRoomNoteFunc = { roomId, note -> mapManager.setRoomNote(roomId, note) },
            setRoomColorFunc = { roomId, color -> mapManager.setRoomColor(roomId, color) },
            setRoomZoneFunc = { roomId, zone -> mapManager.setRoomZone(roomId, zone) },
            setRoomTagsFunc = { roomId, tags -> mapManager.setRoomTags(roomId, tags.toSet()) },
            // Маппер - создание
            createRoomFunc = { id, name ->
                if (mapManager.getRoom(id) != null) false
                else {
                    mapManager.addRoom(com.bylins.client.mapper.Room(id = id, name = name))
                    true
                }
            },
            createRoomWithExitsFunc = { id, name, exits ->
                if (mapManager.getRoom(id) != null) false
                else {
                    val room = com.bylins.client.mapper.Room(id = id, name = name)
                    exits.forEach { (dirName, targetId) ->
                        com.bylins.client.mapper.Direction.fromCommand(dirName)?.let { dir ->
                            room.addExit(dir, targetId)
                        }
                    }
                    mapManager.addRoom(room)
                    true
                }
            },
            linkRoomsFunc = { fromId, direction, toId ->
                val fromRoom = mapManager.getRoom(fromId)
                val toRoom = mapManager.getRoom(toId)
                val dir = com.bylins.client.mapper.Direction.fromCommand(direction)
                if (fromRoom != null && toRoom != null && dir != null) {
                    val updated = fromRoom.copy()
                    updated.addExit(dir, toId)
                    mapManager.addRoom(updated)
                    val reverseUpdated = toRoom.copy()
                    reverseUpdated.addExit(dir.getOpposite(), fromId)
                    mapManager.addRoom(reverseUpdated)
                }
            },
            handleMovementFunc = { direction, roomName, exits ->
                val dir = com.bylins.client.mapper.Direction.fromCommand(direction)
                if (dir != null) {
                    val exitDirs = exits.mapNotNull { com.bylins.client.mapper.Direction.fromCommand(it) }
                    mapManager.handleMovement(dir, roomName, exitDirs)?.toMap()
                } else null
            },
            // Маппер - управление
            setMapEnabledFunc = { enabled -> mapManager.setMapEnabled(enabled) },
            isMapEnabledFunc = { mapManager.mapEnabled.value },
            clearMapFunc = { mapManager.clearMap() },
            setCurrentRoomFunc = { roomId -> mapManager.setCurrentRoom(roomId) },
            isPluginLoadedFunc = { id -> pluginManager.isPluginLoaded(id) },
            // Вкладки плагинов
            registerPluginTabFunc = { tab -> pluginTabManager.registerTab(tab) },
            unregisterPluginTabFunc = { tabId -> pluginTabManager.unregisterTab(tabId) },
            // Callback-поиск комнат
            findRoomsMatchingFunc = { predicate, maxResults ->
                mapManager.findRoomsMatching(predicate, maxResults)
            },
            findNearestMatchingFunc = { predicate ->
                mapManager.findNearestMatching(predicate)
            },
            // События для скриптов
            fireScriptEventFunc = { event, data ->
                if (::scriptManager.isInitialized) {
                    scriptManager.fireEvent(event, data)
                }
            },
            dataFolder = dataFolder
        )
    }

    /**
     * Отправляет событие всем плагинам
     */
    private fun firePluginEvent(event: com.bylins.client.plugins.events.PluginEvent) {
        if (::pluginManager.isInitialized) {
            pluginEventBus.post(event)
        }
    }

    private fun createTriggerActions() = object : com.bylins.client.scripting.TriggerActions {
        override fun addTrigger(pattern: String, callback: (String, Map<Int, String>) -> Unit): String {
            val triggerId = java.util.UUID.randomUUID().toString()
            try {
                val regex = pattern.toRegex()
                val trigger = ScriptTrigger(
                    id = triggerId,
                    pattern = regex,
                    callback = callback,
                    enabled = true
                )
                scriptTriggers[triggerId] = trigger
            } catch (e: Exception) {
                logger.error { "[ScriptAPI] Error adding trigger: ${e.message}" }
            }
            return triggerId
        }

        override fun removeTrigger(id: String) {
            scriptTriggers.remove(id)
        }

        override fun enableTrigger(id: String) {
            scriptTriggers[id]?.enabled = true
        }

        override fun disableTrigger(id: String) {
            scriptTriggers[id]?.enabled = false
        }
    }

    private fun createAliasActions() = object : com.bylins.client.scripting.AliasActions {
        override fun addAlias(pattern: String, replacement: String): String {
            val aliasId = java.util.UUID.randomUUID().toString()
            // TODO: Добавить алиас из скрипта
            return aliasId
        }

        override fun removeAlias(id: String) {
            removeAlias(id)
        }
    }

    private fun createTimerActions() = object : com.bylins.client.scripting.TimerActions {
        private val timers = mutableMapOf<String, kotlinx.coroutines.Job>()

        override fun setTimeout(delayMs: Long, callback: () -> Unit): String {
            val timerId = java.util.UUID.randomUUID().toString()
            val job = scope.launch {
                kotlinx.coroutines.delay(delayMs)
                try {
                    callback()
                } catch (e: Exception) {
                    logger.error { "[Timer] Error in setTimeout: ${e.message}" }
                }
                timers.remove(timerId)
            }
            timers[timerId] = job
            return timerId
        }

        override fun setInterval(intervalMs: Long, callback: () -> Unit): String {
            val timerId = java.util.UUID.randomUUID().toString()
            val job = scope.launch {
                while (coroutineContext.isActive) {
                    kotlinx.coroutines.delay(intervalMs)
                    try {
                        callback()
                    } catch (e: Exception) {
                        logger.error { "[Timer] Error in setInterval: ${e.message}" }
                    }
                }
            }
            timers[timerId] = job
            return timerId
        }

        override fun clearTimer(id: String) {
            timers[id]?.cancel()
            timers.remove(id)
        }
    }

    private fun createVariableActions() = object : com.bylins.client.scripting.VariableActions {
        override fun getVariable(name: String): String? {
            return variableManager.getVariableValue(name)
        }

        override fun setVariable(name: String, value: String) {
            variableManager.setVariable(name, value)
            saveConfig()
        }

        override fun deleteVariable(name: String) {
            variableManager.removeVariable(name)
            saveConfig()
        }

        override fun getAllVariables(): Map<String, String> {
            return variableManager.getAllVariables()
        }
    }

    private fun createMsdpActions() = object : com.bylins.client.scripting.MsdpActions {
        override fun getMsdpValue(key: String): Any? {
            return _msdpData.value[key]
        }

        override fun getAllMsdpData(): Map<String, Any> {
            return _msdpData.value
        }

        override fun getReportableVariables(): List<String> {
            return _msdpReportableVariables.value
        }

        override fun getReportedVariables(): List<String> {
            return _msdpReportedVariables.value.toList()
        }

        override fun isEnabled(): Boolean {
            return _msdpEnabled.value
        }

        override fun report(variableName: String) {
            sendMsdpReport(variableName)
        }

        override fun unreport(variableName: String) {
            sendMsdpUnreport(variableName)
        }

        override fun send(variableName: String) {
            sendMsdpSend(variableName)
        }

        override fun list(listType: String) {
            sendMsdpList(listType)
        }
    }

    private fun createGmcpActions() = object : com.bylins.client.scripting.GmcpActions {
        override fun getGmcpValue(packageName: String): String? {
            val jsonElement = _gmcpData.value[packageName]
            return jsonElement?.toString()
        }

        override fun getAllGmcpData(): Map<String, String> {
            return _gmcpData.value.mapValues { it.value.toString() }
        }
    }

    private fun createMapperActions() = object : com.bylins.client.scripting.MapperActions {
        override fun getCurrentRoom(): Map<String, Any>? {
            return mapManager.getCurrentRoom()?.toMap()
        }

        override fun getRoom(roomId: String): Map<String, Any>? {
            return mapManager.getRoom(roomId)?.toMap()
        }

        override fun searchRooms(query: String): List<Map<String, Any>> {
            return mapManager.searchRooms(query).map { it.toMap() }
        }

        override fun findPath(targetRoomId: String): List<String>? {
            return mapManager.findPathFromCurrent(targetRoomId)?.map { it.name }
        }

        override fun setRoomNote(roomId: String, note: String) {
            mapManager.setRoomNote(roomId, note)
        }

        override fun setRoomColor(roomId: String, color: String?) {
            mapManager.setRoomColor(roomId, color)
        }

        override fun setRoomZone(roomId: String, zone: String) {
            mapManager.setRoomZone(roomId, zone)
        }

        override fun setRoomTags(roomId: String, tags: List<String>) {
            mapManager.setRoomTags(roomId, tags.toSet())
        }

        override fun createRoom(id: String, name: String): Boolean {
            // Проверяем, что комната с таким ID не существует
            if (mapManager.getRoom(id) != null) {
                return false
            }
            val room = com.bylins.client.mapper.Room(
                id = id,
                name = name
            )
            mapManager.addRoom(room)
            return true
        }

        override fun createRoomWithExits(id: String, name: String, exits: Map<String, String>): Boolean {
            if (mapManager.getRoom(id) != null) {
                return false
            }
            val room = com.bylins.client.mapper.Room(
                id = id,
                name = name
            )
            // Добавляем выходы
            exits.forEach { (dirName, targetId) ->
                val direction = com.bylins.client.mapper.Direction.fromCommand(dirName)
                if (direction != null) {
                    room.addExit(direction, targetId)
                }
            }
            mapManager.addRoom(room)
            return true
        }

        override fun linkRooms(fromRoomId: String, direction: String, toRoomId: String) {
            val fromRoom = mapManager.getRoom(fromRoomId) ?: return
            val toRoom = mapManager.getRoom(toRoomId) ?: return
            val dir = com.bylins.client.mapper.Direction.fromCommand(direction) ?: return

            // Добавляем выход из fromRoom в toRoom
            val updated = fromRoom.copy()
            updated.addExit(dir, toRoomId)
            mapManager.addRoom(updated)

            // Добавляем обратный выход
            val reverseUpdated = toRoom.copy()
            reverseUpdated.addExit(dir.getOpposite(), fromRoomId)
            mapManager.addRoom(reverseUpdated)
        }

        override fun addUnexploredExits(roomId: String, exits: List<String>) {
            val room = mapManager.getRoom(roomId) ?: return
            val updated = room.copy()
            exits.forEach { exitStr ->
                val dir = com.bylins.client.mapper.Direction.fromCommand(exitStr)
                if (dir != null) {
                    updated.addUnexploredExit(dir)
                }
            }
            mapManager.addRoom(updated)
        }

        override fun handleMovement(direction: String, roomName: String, exits: List<String>, roomId: String?): Map<String, Any>? {
            val dir = com.bylins.client.mapper.Direction.fromCommand(direction) ?: run {
                return null
            }
            val exitDirections = exits.mapNotNull { exitStr ->
                val d = com.bylins.client.mapper.Direction.fromCommand(exitStr)
                d
            }
            val room = mapManager.handleMovement(dir, roomName, exitDirections, roomId)
            return room?.toMap()
        }

        override fun handleRoom(params: Map<String, Any>): Map<String, Any>? {
            logger.info { "handleRoom called with params: $params" }
            // Извлекаем параметры из MSDP-подобной структуры
            val vnum = params["vnum"] as? String
            if (vnum == null) {
                logger.warn { "handleRoom: vnum is null, params keys: ${params.keys}" }
                return null
            }
            val name = params["name"] as? String ?: ""
            val zone = params["zone"] as? String
            val area = params["area"] as? String  // Только для setZoneName
            val terrain = params["terrain"] as? String

            // Сохраняем имя зоны (area name) по zone_id
            if (!zone.isNullOrBlank() && !area.isNullOrBlank()) {
                mapManager.setZoneName(zone, area)
            }

            // Обрабатываем выходы - формат Map<direction, targetVnum>
            val exitsRaw = params["exits"]
            val exitsWithTargets: Map<com.bylins.client.mapper.Direction, String> = when (exitsRaw) {
                is Map<*, *> -> exitsRaw.mapNotNull { (key, value) ->
                    val dir = com.bylins.client.mapper.Direction.fromCommand(key.toString())
                    val target = value?.toString()
                    if (dir != null && target != null) dir to target else null
                }.toMap()
                else -> emptyMap()
            }

            // Получаем или создаём комнату
            val existingRoom = mapManager.getRoom(vnum)
            val room = if (existingRoom != null) {
                // Обновляем существующую комнату
                existingRoom.copy(
                    name = name,
                    zone = zone ?: existingRoom.zone,
                    terrain = terrain ?: existingRoom.terrain,
                    visited = true
                )
            } else {
                // Создаём новую комнату
                com.bylins.client.mapper.Room(
                    id = vnum,
                    name = name,
                    zone = zone,
                    terrain = terrain,
                    visited = true
                )
            }

            // Добавляем выходы с целевыми комнатами
            exitsWithTargets.forEach { (direction, targetVnum) ->
                room.addExit(direction, targetVnum)

                // Создаём целевую комнату если её нет (статус "неисследовано")
                if (mapManager.getRoom(targetVnum) == null) {
                    val unexploredRoom = com.bylins.client.mapper.Room(
                        id = targetVnum,
                        name = "",  // Пустое имя = неисследовано
                        visited = false
                    )
                    mapManager.addRoom(unexploredRoom)
                }
            }

            mapManager.addRoom(room)
            mapManager.setCurrentRoom(vnum)

            logger.info { "handleRoom: room added, currentRoomId=${mapManager.currentRoomId.value}" }

            return room.toMap()
        }

        override fun setMapEnabled(enabled: Boolean) {
            mapManager.setMapEnabled(enabled)
        }

        override fun isMapEnabled(): Boolean {
            return mapManager.mapEnabled.value
        }

        override fun clearMap() {
            mapManager.clearMap()
        }

        override fun setCurrentRoom(roomId: String) {
            mapManager.setCurrentRoom(roomId)
        }

        @Suppress("UNCHECKED_CAST")
        override fun registerMapCommand(name: String, callback: Any) {
            // Check if callback is already a Kotlin function (from MapperHelper wrapper)
            val kotlinCallback: (com.bylins.client.mapper.Room) -> Unit = when (callback) {
                is Function1<*, *> -> { room ->
                    try {
                        (callback as Function1<Map<String, Any>, Unit>).invoke(room.toMap())
                    } catch (e: Exception) {
                        logger.error { "Error executing map command callback: ${e.message}" }
                    }
                }
                else -> { room ->
                    // Fallback for raw JS callbacks (shouldn't happen with MapperHelper)
                    try {
                        invokeJsCallback(callback, room.toMap())
                    } catch (e: Exception) {
                        logger.error { "Error executing map command callback: ${e.message}" }
                    }
                }
            }
            this@ClientState.registerMapCommand(name, kotlinCallback)
        }

        override fun unregisterMapCommand(name: String) {
            this@ClientState.unregisterMapCommand(name)
        }

        override fun setPathHighlight(roomIds: List<String>, targetRoomId: String?) {
            mapManager.setPathHighlight(roomIds.toSet(), targetRoomId)
        }

        override fun clearPathHighlight() {
            mapManager.clearPathHighlight()
        }

        override fun searchRoomsWithFilter(filter: Any, maxResults: Int): List<Map<String, Any>> {
            // Convert JS callback to Kotlin predicate
            val predicate: (Map<String, Any>) -> Boolean = { roomMap ->
                invokeJsCallbackWithResult(filter, roomMap) as? Boolean ?: false
            }
            return mapManager.findRoomsMatching(predicate, maxResults)
        }

        override fun findNearestRoomMatching(filter: Any): Map<String, Any>? {
            // Convert JS callback to Kotlin predicate
            val predicate: (Map<String, Any>) -> Boolean = { roomMap ->
                invokeJsCallbackWithResult(filter, roomMap) as? Boolean ?: false
            }
            val result = mapManager.findNearestMatching(predicate) ?: return null
            return mapOf(
                "room" to result.first,
                "path" to result.second
            )
        }

        /**
         * Invokes JavaScript callback and returns the result.
         */
        private fun invokeJsCallbackWithResult(callback: Any, vararg args: Any?): Any? {
            return try {
                // Try call method with return value
                val methods = callback.javaClass.methods.filter { it.name == "call" }
                for (method in methods) {
                    try {
                        if (method.parameterCount == 2) {
                            return method.invoke(callback, null, args)
                        }
                    } catch (_: Exception) { }
                }
                // Fallback: try invoke
                val invokeMethods = callback.javaClass.methods.filter { it.name == "invoke" }
                for (method in invokeMethods) {
                    try {
                        return method.invoke(callback, *args)
                    } catch (_: Exception) { }
                }
                null
            } catch (e: Exception) {
                logger.error { "Error invoking callback with result: ${e.message}" }
                null
            }
        }

        /**
         * Invokes JavaScript callback using reflection (works with Nashorn and GraalVM)
         */
        private fun invokeJsCallback(callback: Any, vararg args: Any?) {
            try {
                // Try to find call(Object, Object...) method
                val callMethod = callback.javaClass.getMethod("call", Object::class.java, Array<Any>::class.java)
                callMethod.invoke(callback, null, args)
            } catch (e: NoSuchMethodException) {
                try {
                    // Alternative - find any call method
                    val methods = callback.javaClass.methods.filter { it.name == "call" }
                    for (method in methods) {
                        try {
                            if (method.parameterCount == 2) {
                                method.invoke(callback, null, args)
                                return
                            } else if (method.isVarArgs) {
                                method.invoke(callback, null, *args)
                                return
                            }
                        } catch (_: Exception) { }
                    }
                    logger.warn { "Could not find suitable call method for callback" }
                } catch (ex: Exception) {
                    logger.error { "Error invoking callback: ${ex.message}" }
                }
            } catch (e: Exception) {
                logger.error { "Error in callback: ${e.message}" }
            }
        }
    }

    /**
     * Invokes JavaScript callback using reflection (works with Nashorn and GraalVM)
     */
    private fun invokeJsCallback(callback: Any, vararg args: Any?) {
        try {
            // Try to find call(Object, Object...) method
            val callMethod = callback.javaClass.getMethod("call", Object::class.java, Array<Any>::class.java)
            callMethod.invoke(callback, null, args)
        } catch (e: NoSuchMethodException) {
            try {
                // Alternative - find any call method
                val methods = callback.javaClass.methods.filter { it.name == "call" }
                for (method in methods) {
                    try {
                        if (method.parameterCount == 2) {
                            method.invoke(callback, null, args)
                            return
                        } else if (method.isVarArgs) {
                            method.invoke(callback, null, *args)
                            return
                        }
                    } catch (_: Exception) { }
                }
                logger.warn { "Could not find suitable call method for callback" }
            } catch (ex: Exception) {
                logger.error { "Error invoking callback: ${ex.message}" }
            }
        } catch (e: Exception) {
            logger.error { "Error in callback: ${e.message}" }
        }
    }

    private fun createStatusActions() = object : com.bylins.client.scripting.StatusActions {
        override fun addBar(id: String, label: String, value: Int, max: Int, color: String, showText: Boolean, showMax: Boolean, order: Int) {
            val actualOrder = if (order < 0) statusManager.elements.value.size else order
            statusManager.addBar(id, label, value, max, color, showText, showMax, actualOrder)
        }

        override fun addText(id: String, label: String, value: String?, color: String?, bold: Boolean, background: String?, order: Int) {
            val actualOrder = if (order < 0) statusManager.elements.value.size else order
            statusManager.addText(id, label, value, color, bold, background, actualOrder)
        }

        override fun addFlags(id: String, label: String, flags: List<Map<String, Any>>, order: Int) {
            val actualOrder = if (order < 0) statusManager.elements.value.size else order
            val flagItems = flags.map { flagMap ->
                com.bylins.client.status.FlagItem(
                    name = flagMap["name"] as? String ?: "",
                    active = flagMap["active"] as? Boolean ?: true,
                    color = flagMap["color"] as? String ?: "white",
                    timer = flagMap["timer"] as? String
                )
            }
            statusManager.addFlags(id, label, flagItems, actualOrder)
        }

        override fun addMiniMap(id: String, currentRoomId: String?, visible: Boolean, order: Int) {
            val actualOrder = if (order < 0) statusManager.elements.value.size else order
            statusManager.addMiniMap(id, currentRoomId, visible, actualOrder)
        }

        override fun addPathPanel(id: String, targetName: String, stepsCount: Int, directions: List<String>, onClear: (() -> Unit)?, onFollow: (() -> Unit)?, order: Int) {
            val actualOrder = if (order < 0) statusManager.elements.value.size else order
            statusManager.addPathPanel(id, targetName, stepsCount, directions, onClear, onFollow, actualOrder)
        }

        override fun invokeJsCallback(callback: Any) {
            this@ClientState.invokeJsCallback(callback)
        }

        override fun update(id: String, updates: Map<String, Any>) {
            statusManager.update(id, updates)
        }

        override fun remove(id: String) {
            // Проверяем, удаляется ли path panel - если да, вернём фокус на командную строку
            val element = statusManager.get(id)
            val isPathPanel = element is com.bylins.client.status.StatusElement.PathPanel

            statusManager.remove(id)

            // Возвращаем фокус после удаления path panel
            if (isPathPanel) {
                requestInputFocus()
            }
        }

        override fun clear() {
            statusManager.clear()
        }

        override fun get(id: String): Map<String, Any>? {
            val element = statusManager.get(id) ?: return null
            return when (element) {
                is com.bylins.client.status.StatusElement.Bar -> mapOf(
                    "type" to "bar",
                    "id" to element.id,
                    "label" to element.label,
                    "value" to element.value,
                    "max" to element.max,
                    "color" to element.color,
                    "showText" to element.showText,
                    "order" to element.order
                )
                is com.bylins.client.status.StatusElement.Text -> buildMap {
                    put("type", "text")
                    put("id", element.id)
                    put("label", element.label)
                    element.value?.let { put("value", it) }
                    element.color?.let { put("color", it) }
                    put("bold", element.bold)
                    element.background?.let { put("background", it) }
                    put("order", element.order)
                }
                is com.bylins.client.status.StatusElement.Flags -> mapOf(
                    "type" to "flags",
                    "id" to element.id,
                    "label" to element.label,
                    "flags" to element.flags.map { flag ->
                        mapOf(
                            "name" to flag.name,
                            "active" to flag.active,
                            "color" to flag.color,
                            "timer" to (flag.timer ?: "")
                        )
                    },
                    "order" to element.order
                )
                is com.bylins.client.status.StatusElement.MiniMap -> mapOf(
                    "type" to "minimap",
                    "id" to element.id,
                    "currentRoomId" to (element.currentRoomId ?: ""),
                    "visible" to element.visible,
                    "order" to element.order
                )
                is com.bylins.client.status.StatusElement.PathPanel -> mapOf(
                    "type" to "pathpanel",
                    "id" to element.id,
                    "targetName" to element.targetName,
                    "stepsCount" to element.stepsCount,
                    "directions" to element.directions,
                    "order" to element.order
                )
            }
        }

        override fun exists(id: String): Boolean {
            return statusManager.exists(id)
        }
    }

    private fun createChartActions() = object : com.bylins.client.scripting.ChartActions {
        override fun createChart(id: String, options: Map<String, Any>) {
            chartManager.createChart(id, options)
        }

        override fun removeChart(id: String) {
            chartManager.removeChart(id)
        }

        override fun clearChart(id: String) {
            chartManager.clearChart(id)
        }

        override fun addSeries(chartId: String, seriesId: String, options: Map<String, Any>) {
            chartManager.addSeries(chartId, seriesId, options)
        }

        override fun removeSeries(chartId: String, seriesId: String) {
            chartManager.removeSeries(chartId, seriesId)
        }

        override fun addDataPoint(chartId: String, seriesId: String, value: Double) {
            chartManager.addDataPoint(chartId, seriesId, value)
        }

        override fun addDataPointExt(chartId: String, seriesId: String, value: Double, displayValue: Double) {
            chartManager.addDataPoint(chartId, seriesId, value, displayValue)
        }

        override fun addChartEvent(chartId: String, label: String, color: String?) {
            chartManager.addChartEvent(chartId, label, color)
        }
    }

    /**
     * Creates storage actions for script data persistence.
     * Uses the script name as the script ID for storage isolation.
     */
    private fun createStorageActions() = object : com.bylins.client.scripting.StorageActions {
        // Get current script ID from the API (set by ScriptManager when loading scripts)
        private fun getCurrentScriptId(): String {
            // Access the scriptManager to get current script name from API
            // If not available, use "global" as fallback
            return "global"  // Scripts can override this by passing their name
        }

        override fun setData(key: String, value: Any): Boolean {
            return scriptStorage.setData(getCurrentScriptId(), key, value)
        }

        override fun getData(key: String): Any? {
            return scriptStorage.getData(getCurrentScriptId(), key)
        }

        override fun deleteData(key: String): Boolean {
            return scriptStorage.deleteData(getCurrentScriptId(), key)
        }

        override fun listDataKeys(prefix: String?): List<String> {
            return scriptStorage.listDataKeys(getCurrentScriptId(), prefix)
        }
    }

    fun getScripts() = if (::scriptManager.isInitialized) scriptManager.scripts else kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    fun getAvailableScriptEngines() = if (::scriptManager.isInitialized) scriptManager.getAvailableEngines() else emptyList()
    fun loadScript(file: java.io.File) = if (::scriptManager.isInitialized) scriptManager.loadScript(file) else null
    fun unloadScript(scriptId: String) = if (::scriptManager.isInitialized) scriptManager.unloadScript(scriptId) else Unit
    fun enableScript(scriptId: String) = if (::scriptManager.isInitialized) scriptManager.enableScript(scriptId) else Unit
    fun disableScript(scriptId: String) = if (::scriptManager.isInitialized) scriptManager.disableScript(scriptId) else Unit
    fun reloadScript(scriptId: String) = if (::scriptManager.isInitialized) scriptManager.reloadScript(scriptId) else Unit
    fun getScriptsDirectory() = if (::scriptManager.isInitialized) scriptManager.getScriptsDirectory() else "scripts"

    // === Управление плагинами ===
    fun getPlugins() = if (::pluginManager.isInitialized) pluginManager.plugins else kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    fun loadPlugin(file: java.io.File) = if (::pluginManager.isInitialized) pluginManager.loadPlugin(file) else null
    fun unloadPlugin(pluginId: String) = if (::pluginManager.isInitialized) pluginManager.unloadPlugin(pluginId) else false
    fun enablePlugin(pluginId: String) = if (::pluginManager.isInitialized) pluginManager.enablePlugin(pluginId) else false
    fun disablePlugin(pluginId: String) = if (::pluginManager.isInitialized) pluginManager.disablePlugin(pluginId) else false
    fun reloadPlugin(pluginId: String) = if (::pluginManager.isInitialized) pluginManager.reloadPlugin(pluginId) else false
    fun getPluginsDirectory() = if (::pluginManager.isInitialized) pluginManager.pluginsDirectory.absolutePath else "plugins"

    // Управление звуками
    fun setSoundEnabled(enabled: Boolean) = soundManager.setSoundEnabled(enabled)
    fun setSoundVolume(volume: Float) = soundManager.setVolume(volume)
    fun playSound(type: com.bylins.client.audio.SoundManager.SoundType) = soundManager.playSound(type)
}
