package com.bylins.client

import mu.KotlinLogging
import com.bylins.client.aliases.AliasManager
import com.bylins.client.config.ConfigManager
import com.bylins.client.hotkeys.HotkeyManager
import com.bylins.client.logging.LogManager
import com.bylins.client.network.TelnetClient
import com.bylins.client.stats.SessionStats
import com.bylins.client.tabs.TabManager
import com.bylins.client.triggers.TriggerManager
import com.bylins.client.variables.VariableManager
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

    // Менеджеры инициализируются первыми
    private val aliasManager = AliasManager(
        onCommand = { command ->
            // Callback для отправки команд из алиасов (без рекурсии)
            sendRaw(command)
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
    private val soundManager = com.bylins.client.audio.SoundManager()
    private val variableManager = VariableManager()
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
    private val mapManager = com.bylins.client.mapper.MapManager(
        onRoomEnter = { room ->
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
        }
    )
    private val roomParser = com.bylins.client.mapper.RoomParser()

    private var lastCommand: String? = null

    // Кодировка для telnet (конфигурируется пользователем)
    private var _encoding = "UTF-8"
    val encoding: String
        get() = _encoding

    // Ширина боковой панели с миникартой
    private val _miniMapWidth = MutableStateFlow(250)
    val miniMapWidth: StateFlow<Int> = _miniMapWidth

    // Тема оформления (DARK, LIGHT, DARK_BLUE, SOLARIZED_DARK, MONOKAI)
    private val _currentTheme = MutableStateFlow("DARK")
    val currentTheme: StateFlow<String> = _currentTheme

    // Настройки шрифта
    private val _fontFamily = MutableStateFlow("MONOSPACE")
    val fontFamily: StateFlow<String> = _fontFamily

    private val _fontSize = MutableStateFlow(14)
    val fontSize: StateFlow<Int> = _fontSize

    private val telnetClient = TelnetClient(this, _encoding)

    // Скриптинг - инициализируется позже
    private lateinit var scriptManager: com.bylins.client.scripting.ScriptManager

    // Плагины - инициализируются после скриптинга
    private lateinit var pluginManager: com.bylins.client.plugins.PluginManager
    private val pluginEventBus = com.bylins.client.plugins.events.EventBus()

    val isConnected: StateFlow<Boolean> = telnetClient.isConnected
    val receivedData: StateFlow<String> = telnetClient.receivedData

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _msdpData = MutableStateFlow<Map<String, Any>>(emptyMap())
    val msdpData: StateFlow<Map<String, Any>> = _msdpData

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

    // Доступ к звукам
    val soundEnabled = soundManager.soundEnabled
    val soundVolume = soundManager.volume

    // Доступ к переменным
    val variables = variableManager.variables

    // Доступ к вкладкам
    val tabs = tabManager.tabs
    val activeTabId = tabManager.activeTabId

    // Доступ к карте
    val mapRooms = mapManager.rooms
    val currentRoomId = mapManager.currentRoomId
    val mapEnabled = mapManager.mapEnabled

    init {
        // Регистрируем shutdown hook для корректного завершения
        Runtime.getRuntime().addShutdownHook(Thread {
            shutdown()
        })

        // Инициализируем скриптинг
        initializeScripting()

        // Инициализируем плагины
        initializePlugins()

        // Продолжаем стандартную инициализацию
        // Пытаемся загрузить сохранённую конфигурацию
        val configData = configManager.loadConfig()

        // Загружаем кодировку из конфига
        _encoding = configData.encoding
        telnetClient.setEncoding(_encoding)

        // Загружаем ширину миникарты из конфига
        _miniMapWidth.value = configData.miniMapWidth

        // Загружаем тему из конфига
        _currentTheme.value = configData.theme

        // Загружаем настройки шрифта из конфига
        _fontFamily.value = configData.fontFamily
        _fontSize.value = configData.fontSize

        // Загружаем профили подключений из конфига
        _connectionProfiles.value = configData.connectionProfiles
        _currentProfileId.value = configData.currentProfileId

        if (configData.triggers.isEmpty() && configData.aliases.isEmpty() && configData.hotkeys.isEmpty() && configData.tabs.isEmpty()) {
            // Если конфига нет, загружаем стандартные триггеры, алиасы, хоткеи и вкладки
            loadDefaultAliases()
            loadDefaultTriggers()
            loadDefaultHotkeys()
            loadDefaultTabs()
        } else {
            // Загружаем сохранённую конфигурацию
            configData.triggers.forEach { addTrigger(it) }
            configData.aliases.forEach { addAlias(it) }
            configData.hotkeys.forEach { addHotkey(it) }
            variableManager.loadVariables(configData.variables)
            tabManager.loadTabs(configData.tabs)
        }

        // Завершаем инициализацию и сохраняем конфиг один раз
        isInitializing = false
        saveConfig()

        // Мониторинг состояния соединения для автосохранения карты при разрыве
        scope.launch {
            var wasConnected = false
            isConnected.collect { connected ->
                if (wasConnected && !connected) {
                    // Соединение было разорвано - сохраняем снапшот карты
                    if (mapManager.rooms.value.isNotEmpty()) {
                        logger.info { "Connection lost, auto-saving map snapshot..." }
                        mapManager.saveSnapshot()
                    }
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

    private fun loadDefaultAliases() {
        // Алиас для recall (r -> cast 'word of recall')
        addAlias(
            com.bylins.client.aliases.Alias(
                id = "recall",
                name = "Recall",
                pattern = "^r$".toRegex(),
                commands = listOf("cast 'word of recall'"),
                enabled = true,
                priority = 10
            )
        )

        // Алиас для tell (t <name> <text> -> tell <name> <text>)
        addAlias(
            com.bylins.client.aliases.Alias(
                id = "tell-short",
                name = "Tell Shortcut",
                pattern = "^t (\\w+) (.+)$".toRegex(),
                commands = listOf("tell $1 $2"),
                enabled = true,
                priority = 10
            )
        )

        // Алиас для buff (buff -> cast armor, bless, shield)
        addAlias(
            com.bylins.client.aliases.Alias(
                id = "buff",
                name = "Buff",
                pattern = "^buff$".toRegex(),
                commands = listOf(
                    "cast 'armor'",
                    "cast 'bless'",
                    "cast 'shield'"
                ),
                enabled = false, // Выключен по умолчанию
                priority = 5
            )
        )

        // Алиас для cast (c 'spell' target -> cast 'spell' target)
        addAlias(
            com.bylins.client.aliases.Alias(
                id = "cast-short",
                name = "Cast Shortcut",
                pattern = "^c '(.+)'( (.+))?$".toRegex(),
                commands = listOf("cast '$1'$2"),
                enabled = true,
                priority = 10
            )
        )
    }

    private fun loadDefaultTriggers() {
        // Триггер для подсветки tells со звуком
        addTrigger(
            com.bylins.client.triggers.Trigger(
                id = "tell-notify",
                name = "Tell Notification",
                pattern = "^(.+) говорит вам:".toRegex(),
                commands = listOf("#sound tell"),
                enabled = true,
                priority = 10,
                colorize = com.bylins.client.triggers.TriggerColorize(
                    foreground = "#00FF00",
                    bold = true
                )
            )
        )

        // Триггер для подсветки шепота со звуком
        addTrigger(
            com.bylins.client.triggers.Trigger(
                id = "whisper-notify",
                name = "Whisper Notification",
                pattern = "^(.+) шепчет вам:".toRegex(),
                commands = listOf("#sound whisper"),
                enabled = true,
                priority = 10,
                colorize = com.bylins.client.triggers.TriggerColorize(
                    foreground = "#FFFF00",
                    bold = true
                )
            )
        )

        // Пример сохранения переменной из триггера (выключен по умолчанию)
        addTrigger(
            com.bylins.client.triggers.Trigger(
                id = "capture-target",
                name = "Capture Target",
                pattern = "Вы атакуете (.+)!".toRegex(),
                commands = listOf("#var target $1"),
                enabled = false, // Выключен по умолчанию - пример
                priority = 10
            )
        )

        // Триггер для gag болталки (выключен по умолчанию)
        addTrigger(
            com.bylins.client.triggers.Trigger(
                id = "gag-gossip",
                name = "Gag Gossip",
                pattern = "^\\[Болталка\\]".toRegex(),
                commands = emptyList(),
                enabled = false, // Выключен по умолчанию
                priority = 5,
                gag = true
            )
        )

        // Пример auto-heal триггера (выключен по умолчанию)
        addTrigger(
            com.bylins.client.triggers.Trigger(
                id = "auto-heal",
                name = "Auto Heal",
                pattern = "HP: (\\d+)/(\\d+)".toRegex(),
                commands = listOf("cast 'cure serious'"),
                enabled = false, // Выключен по умолчанию - опасно!
                priority = 15
            )
        )
    }

    private fun loadDefaultHotkeys() {
        // F1 - info
        addHotkey(
            com.bylins.client.hotkeys.Hotkey(
                id = "f1-info",
                name = "Info",
                key = androidx.compose.ui.input.key.Key.F1,
                commands = listOf("info"),
                enabled = true
            )
        )

        // F2 - score
        addHotkey(
            com.bylins.client.hotkeys.Hotkey(
                id = "f2-score",
                name = "Score",
                key = androidx.compose.ui.input.key.Key.F2,
                commands = listOf("score"),
                enabled = true
            )
        )

        // F3 - inventory
        addHotkey(
            com.bylins.client.hotkeys.Hotkey(
                id = "f3-inventory",
                name = "Inventory",
                key = androidx.compose.ui.input.key.Key.F3,
                commands = listOf("inventory"),
                enabled = true
            )
        )

        // F4 - look
        addHotkey(
            com.bylins.client.hotkeys.Hotkey(
                id = "f4-look",
                name = "Look",
                key = androidx.compose.ui.input.key.Key.F4,
                commands = listOf("look"),
                enabled = true
            )
        )
    }

    private fun loadDefaultTabs() {
        // Вкладка для каналов связи
        addTab(
            com.bylins.client.tabs.Tab(
                id = "channels",
                name = "Каналы",
                filters = listOf(
                    com.bylins.client.tabs.TabFilter("^.+ говорит вам:".toRegex()),
                    com.bylins.client.tabs.TabFilter("^.+ шепчет вам:".toRegex()),
                    com.bylins.client.tabs.TabFilter("^\\[Болталка\\]".toRegex())
                ),
                captureMode = com.bylins.client.tabs.CaptureMode.COPY,
                maxLines = 5000
            )
        )
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
                variableManager.setVariable("host", host)
                variableManager.setVariable("port", port.toString())
                variableManager.setVariable("connected", "1")
                // Автозагрузка карты при подключении
                mapManager.loadFromFile()

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
        // Автосохранение карты перед отключением (снапшот)
        if (mapManager.rooms.value.isNotEmpty()) {
            mapManager.saveSnapshot()
        }

        telnetClient.disconnect()
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
        // Сохраняем конфигурацию
        saveConfig()
        // Завершаем работу маппера (сохраняет снапшот)
        mapManager.shutdown()
        // Выгружаем плагины
        pluginManager.shutdown()
        logger.info { "Shutdown complete" }
    }

    fun send(command: String) {
        // Сначала проверяем команды управления переменными
        val varHandled = variableManager.processCommand(command) { message ->
            // Выводим сообщения от VariableManager в лог
            telnetClient.addToOutput(message)
        }
        if (varHandled) {
            return
        }

        // Проверяем команды навигации по карте
        val navHandled = processNavigationCommand(command)
        if (navHandled) {
            return
        }

        // Подставляем переменные в команду
        val commandWithVars = variableManager.substituteVariables(command)

        // Проверяем алиасы
        val handled = aliasManager.processCommand(commandWithVars)
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

        // Эхо команды в лог
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
     * Обрабатывает команды навигации по карте
     * Возвращает true если команда была обработана
     */
    private fun processNavigationCommand(command: String): Boolean {
        when {
            command == "#help" -> {
                showHelp()
                return true
            }

            command.startsWith("#sound ") -> {
                val soundType = command.substring(7).trim().lowercase()
                val type = when (soundType) {
                    "tell" -> com.bylins.client.audio.SoundManager.SoundType.TELL
                    "whisper" -> com.bylins.client.audio.SoundManager.SoundType.WHISPER
                    "lowhp" -> com.bylins.client.audio.SoundManager.SoundType.LOW_HP
                    "levelup" -> com.bylins.client.audio.SoundManager.SoundType.LEVEL_UP
                    "death" -> com.bylins.client.audio.SoundManager.SoundType.DEATH
                    "combat" -> com.bylins.client.audio.SoundManager.SoundType.COMBAT
                    "alert" -> com.bylins.client.audio.SoundManager.SoundType.ALERT
                    "beep" -> {
                        soundManager.playBeep()
                        return true
                    }
                    else -> {
                        telnetClient.addToOutput("\u001B[1;33m[#sound] Неизвестный тип звука: $soundType\u001B[0m")
                        return true
                    }
                }
                soundManager.playSound(type)
                return true
            }

            command.startsWith("#goto ") -> {
                val roomId = command.substring(6).trim()
                if (roomId.isEmpty()) {
                    telnetClient.addToOutput("\u001B[1;33m[#goto] Использование: #goto <room_id>\u001B[0m")
                    return true
                }

                // Находим путь к комнате
                val path = mapManager.findPathFromCurrent(roomId)
                if (path == null) {
                    telnetClient.addToOutput("\u001B[1;31m[#goto] Путь к комнате '$roomId' не найден\u001B[0m")
                    return true
                }

                if (path.isEmpty()) {
                    telnetClient.addToOutput("\u001B[1;33m[#goto] Вы уже в этой комнате\u001B[0m")
                    return true
                }

                // Запускаем автоматическое перемещение
                val directions = path.joinToString(", ") { it.shortName }
                telnetClient.addToOutput("\u001B[1;32m[#goto] Путь найден (${path.size} шагов): $directions\u001B[0m")

                scope.launch {
                    walkPath(path)
                }
                return true
            }

            command == "#run" -> {
                // Находим путь к ближайшей непосещенной комнате
                val path = mapManager.findNearestUnvisited()
                if (path == null) {
                    telnetClient.addToOutput("\u001B[1;33m[#run] Не найдено непосещенных комнат\u001B[0m")
                    return true
                }

                if (path.isEmpty()) {
                    telnetClient.addToOutput("\u001B[1;33m[#run] Уже в непосещенной комнате\u001B[0m")
                    return true
                }

                // Запускаем автоматическое перемещение
                val directions = path.joinToString(", ") { it.shortName }
                telnetClient.addToOutput("\u001B[1;32m[#run] Путь к непосещенной комнате (${path.size} шагов): $directions\u001B[0m")

                scope.launch {
                    walkPath(path)
                }
                return true
            }

            command.startsWith("#find ") -> {
                val query = command.substring(6).trim()
                if (query.isEmpty()) {
                    telnetClient.addToOutput("\u001B[1;33m[#find] Использование: #find <название комнаты>\u001B[0m")
                    return true
                }

                // Ищем комнаты по названию
                val foundRooms = mapManager.searchRooms(query, searchInDescription = false)

                if (foundRooms.isEmpty()) {
                    telnetClient.addToOutput("\u001B[1;31m[#find] Комнаты с названием '$query' не найдены\u001B[0m")
                    return true
                }

                if (foundRooms.size == 1) {
                    // Если найдена одна комната, сразу идём к ней
                    val room = foundRooms.first()
                    val path = mapManager.findPathFromCurrent(room.id)

                    if (path == null) {
                        telnetClient.addToOutput("\u001B[1;31m[#find] Путь к комнате '${room.name}' не найден\u001B[0m")
                        return true
                    }

                    if (path.isEmpty()) {
                        telnetClient.addToOutput("\u001B[1;33m[#find] Вы уже в комнате '${room.name}'\u001B[0m")
                        return true
                    }

                    val directions = path.joinToString(", ") { it.shortName }
                    telnetClient.addToOutput("\u001B[1;32m[#find] Путь к '${room.name}' (${path.size} шагов): $directions\u001B[0m")

                    scope.launch {
                        walkPath(path)
                    }
                } else {
                    // Если найдено несколько комнат, показываем список
                    val sb = StringBuilder()
                    sb.append("\u001B[1;32m[#find] Найдено комнат: ${foundRooms.size}\u001B[0m\n")

                    // Сортируем по расстоянию и показываем первые 10
                    val sortedRooms = foundRooms.take(10)
                    sortedRooms.forEachIndexed { index, room ->
                        val path = mapManager.findPathFromCurrent(room.id)
                        val distance = path?.size ?: -1
                        val distanceStr = if (distance >= 0) "$distance шагов" else "недоступна"
                        sb.append("\u001B[1;33m${index + 1}.\u001B[0m ${room.name} (ID: ${room.id}, $distanceStr)\n")
                    }

                    if (foundRooms.size > 10) {
                        sb.append("\u001B[1;33m... и ещё ${foundRooms.size - 10} комнат\u001B[0m\n")
                    }

                    sb.append("\u001B[1;33mИспользуйте #goto <room_id> для перехода\u001B[0m")
                    telnetClient.addToOutput(sb.toString())
                }
                return true
            }

            command.startsWith("#zone") -> {
                val args = command.substring(5).trim()

                when {
                    // #zone - показать текущую зону
                    args.isEmpty() -> {
                        val currentRoom = mapManager.getCurrentRoom()
                        if (currentRoom == null) {
                            telnetClient.addToOutput("\u001B[1;31m[#zone] Текущая комната не определена\u001B[0m")
                        } else if (currentRoom.zone.isEmpty()) {
                            telnetClient.addToOutput("\u001B[1;33m[#zone] Текущая комната не принадлежит ни одной зоне\u001B[0m")
                        } else {
                            telnetClient.addToOutput("\u001B[1;32m[#zone] Текущая зона: ${currentRoom.zone}\u001B[0m")
                        }
                    }

                    // #zone list - список всех зон
                    args == "list" -> {
                        val zones = getAllZones()
                        if (zones.isEmpty()) {
                            telnetClient.addToOutput("\u001B[1;33m[#zone] Зоны не определены. Используйте #zone detect\u001B[0m")
                        } else {
                            val stats = getZoneStatistics()
                            val sb = StringBuilder()
                            sb.append("\u001B[1;32m[#zone] Список зон (${stats.size}):\u001B[0m\n")
                            stats.forEach { (zone, count) ->
                                sb.append("\u001B[1;33m- $zone\u001B[0m ($count комнат)\n")
                            }
                            telnetClient.addToOutput(sb.toString())
                        }
                    }

                    // #zone detect - автоматическая детекция
                    args == "detect" -> {
                        detectAndAssignZones()
                        val stats = getZoneStatistics()
                        telnetClient.addToOutput("\u001B[1;32m[#zone] Детектировано зон: ${stats.size}\u001B[0m")
                    }

                    // #zone clear - очистить все зоны
                    args == "clear" -> {
                        clearAllZones()
                        telnetClient.addToOutput("\u001B[1;32m[#zone] Все зоны очищены\u001B[0m")
                    }

                    else -> {
                        val sb = StringBuilder()
                        sb.append("\u001B[1;33m[#zone] Использование:\u001B[0m\n")
                        sb.append("  #zone - показать текущую зону\n")
                        sb.append("  #zone list - список всех зон\n")
                        sb.append("  #zone detect - автоматическая детекция зон\n")
                        sb.append("  #zone clear - очистить все зоны")
                        telnetClient.addToOutput(sb.toString())
                    }
                }
                return true
            }

            // Speedwalk: распознаём паттерн типа 5n2e3w
            command.matches(Regex("^[0-9]*[nsewud]{1,2}([0-9]+[nsewud]{1,2})*$", RegexOption.IGNORE_CASE)) -> {
                val directions = parseSpeedwalk(command)
                if (directions.isEmpty()) {
                    return false
                }

                telnetClient.addToOutput("\u001B[1;32m[Speedwalk] ${directions.size} шагов: ${directions.joinToString(", ")}\u001B[0m")

                scope.launch {
                    walkPath(directions)
                }
                return true
            }

            else -> return false
        }
    }

    /**
     * Парсит строку speedwalk (например, "5n2e3w") в список направлений
     */
    private fun parseSpeedwalk(text: String): List<com.bylins.client.mapper.Direction> {
        val directions = mutableListOf<com.bylins.client.mapper.Direction>()
        var i = 0

        while (i < text.length) {
            // Читаем число (если есть)
            var numStr = ""
            while (i < text.length && text[i].isDigit()) {
                numStr += text[i]
                i++
            }
            val count = if (numStr.isEmpty()) 1 else numStr.toInt()

            // Читаем направление (1-2 символа)
            if (i >= text.length) break

            var dirStr = text[i].toString()
            i++

            // Проверяем двухбуквенные направления (ne, nw, se, sw)
            if (i < text.length) {
                val twoChar = dirStr + text[i]
                if (twoChar.lowercase() in listOf("ne", "nw", "se", "sw")) {
                    dirStr = twoChar
                    i++
                }
            }

            // Конвертируем в Direction
            val direction = when (dirStr.lowercase()) {
                "n" -> com.bylins.client.mapper.Direction.NORTH
                "s" -> com.bylins.client.mapper.Direction.SOUTH
                "e" -> com.bylins.client.mapper.Direction.EAST
                "w" -> com.bylins.client.mapper.Direction.WEST
                "ne" -> com.bylins.client.mapper.Direction.NORTHEAST
                "nw" -> com.bylins.client.mapper.Direction.NORTHWEST
                "se" -> com.bylins.client.mapper.Direction.SOUTHEAST
                "sw" -> com.bylins.client.mapper.Direction.SOUTHWEST
                "u" -> com.bylins.client.mapper.Direction.UP
                "d" -> com.bylins.client.mapper.Direction.DOWN
                else -> return emptyList() // Неверное направление
            }

            // Добавляем count раз
            repeat(count) {
                directions.add(direction)
            }
        }

        return directions
    }

    /**
     * Показывает справку по доступным командам
     */
    private fun showHelp() {
        val help = """
            |═══════════════════════════════════════════════════════════════
            |  СПРАВКА ПО КОМАНДАМ КЛИЕНТА
            |═══════════════════════════════════════════════════════════════
            |
            |📍 НАВИГАЦИЯ И АВТОМАППЕР:
            |  #goto <room_id>        - Переход к указанной комнате
            |  #run                   - Переход к ближайшей непосещенной комнате
            |  #find <название>       - Поиск комнат по названию
            |  #zone                  - Информация о зонах
            |  #zone list             - Список всех зон на карте
            |  #zone detect           - Автоматическая детекция зон
            |  #zone clear            - Очистить все зоны
            |  Speedwalk: 5n, 3n2e, 10sw - Быстрое перемещение
            |
            |💾 ПЕРЕМЕННЫЕ:
            |  #var <имя> <значение>  - Установить переменную
            |  #unvar <имя>           - Удалить переменную
            |  #vars                  - Показать все переменные
            |  Использование: @имя или ${'$'}{имя}
            |
            |📝 ЛОГИРОВАНИЕ:
            |  #log start             - Начать логирование
            |  #log stop              - Остановить логирование
            |  #log clear             - Очистить старые логи
            |
            |📊 СТАТИСТИКА:
            |  #stats                 - Показать статистику сессии
            |  #stats reset           - Сбросить статистику
            |
            |🗂️ ВКЛАДКИ:
            |  UI в правой панели для управления вкладками
            |
            |⚡ ТРИГГЕРЫ И АЛИАСЫ:
            |  UI в правой панели для управления
            |
            |⌨️ ГОРЯЧИЕ КЛАВИШИ:
            |  F1-F12, Numpad 0-9, модификаторы Ctrl/Alt/Shift
            |  UI в правой панели для управления
            |
            |🎨 СКРИПТЫ:
            |  Поддержка JavaScript, Python (Jython), Lua (LuaJ)
            |  Размещайте скрипты в директории: scripts/
            |  UI в правой панели для управления
            |
            |═══════════════════════════════════════════════════════════════
        """.trimMargin()

        telnetClient.addToOutput(help)
    }

    /**
     * Выполняет автоматическое перемещение по пути
     */
    private suspend fun walkPath(path: List<com.bylins.client.mapper.Direction>) {
        for (direction in path) {
            if (!coroutineContext.isActive) break

            // Отправляем команду движения
            sendRaw(direction.shortName)

            // Задержка между командами (можно сделать настраиваемой)
            delay(500)
        }
    }

    /**
     * Устанавливает статус MSDP (вызывается из TelnetClient при согласовании)
     */
    fun setMsdpEnabled(enabled: Boolean) {
        val wasEnabled = _msdpEnabled.value
        _msdpEnabled.value = enabled
        if (enabled && !wasEnabled) {
            logger.info { "MSDP протокол включён" }
            // Автоматически запрашиваем список reportable переменных
            scope.launch {
                delay(100) // Небольшая задержка для завершения handshake
                sendMsdpList("REPORTABLE_VARIABLES")
            }
        }
    }

    /**
     * Отправляет MSDP команду LIST для запроса списка
     * listType: "COMMANDS", "LISTS", "REPORTABLE_VARIABLES", "CONFIGURABLE_VARIABLES", "REPORTED_VARIABLES"
     */
    fun sendMsdpList(listType: String) {
        if (!_msdpEnabled.value) {
            logger.warn { "MSDP не включён, команда LIST проигнорирована" }
            return
        }
        telnetClient.sendMsdpCommand("LIST", listType)
        logger.debug { "MSDP LIST $listType отправлен" }
    }

    /**
     * Включает REPORT для переменной (автоматические обновления)
     */
    fun sendMsdpReport(variableName: String) {
        if (!_msdpEnabled.value) {
            logger.warn { "MSDP не включён, команда REPORT проигнорирована" }
            return
        }
        telnetClient.sendMsdpCommand("REPORT", variableName)
        _msdpReportedVariables.value = _msdpReportedVariables.value + variableName
        logger.debug { "MSDP REPORT $variableName отправлен" }
    }

    /**
     * Выключает REPORT для переменной
     */
    fun sendMsdpUnreport(variableName: String) {
        if (!_msdpEnabled.value) {
            logger.warn { "MSDP не включён, команда UNREPORT проигнорирована" }
            return
        }
        telnetClient.sendMsdpCommand("UNREPORT", variableName)
        _msdpReportedVariables.value = _msdpReportedVariables.value - variableName
        logger.debug { "MSDP UNREPORT $variableName отправлен" }
    }

    /**
     * Запрашивает текущее значение переменной (разовый запрос)
     */
    fun sendMsdpSend(variableName: String) {
        if (!_msdpEnabled.value) {
            logger.warn { "MSDP не включён, команда SEND проигнорирована" }
            return
        }
        telnetClient.sendMsdpCommand("SEND", variableName)
        logger.debug { "MSDP SEND $variableName отправлен" }
    }

    fun updateMsdpData(data: Map<String, Any>) {
        _msdpData.value = _msdpData.value + data

        // Проверяем специальные переменные (ответы на LIST)
        data["REPORTABLE_VARIABLES"]?.let { value ->
            if (value is List<*>) {
                _msdpReportableVariables.value = value.filterIsInstance<String>()
                logger.info { "Получен список REPORTABLE_VARIABLES: ${_msdpReportableVariables.value.size} переменных" }
            }
        }

        // Автоматически обновляем переменные из MSDP
        data.forEach { (key, value) ->
            variableManager.setVariable(key.lowercase(), value.toString())
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

            // Увеличиваем счетчик на количество сработавших триггеров
            if (matches.isNotEmpty()) {
                sessionStats.incrementTriggersActivated()

                // Применяем colorize от первого сработавшего триггера с colorize
                val triggerWithColor = matches.firstOrNull { it.trigger.colorize != null }
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
        val handled = hotkeyManager.processKeyPress(key, isCtrlPressed, isAltPressed, isShiftPressed)
        if (handled) {
            sessionStats.incrementHotkeysUsed()
        }
        return handled
    }

    // Управление вкладками
    fun addTab(tab: com.bylins.client.tabs.Tab) {
        tabManager.addTab(tab)
        saveConfig()
    }

    fun createTab(name: String, patterns: List<String>, captureMode: com.bylins.client.tabs.CaptureMode) {
        val filters = patterns.map { pattern ->
            com.bylins.client.tabs.TabFilter(
                pattern = pattern.toRegex(),
                includeMatched = true
            )
        }
        val tab = com.bylins.client.tabs.Tab(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            filters = filters,
            captureMode = captureMode
        )
        addTab(tab)
    }

    fun updateTab(id: String, name: String, patterns: List<String>, captureMode: com.bylins.client.tabs.CaptureMode) {
        val filters = patterns.map { pattern ->
            com.bylins.client.tabs.TabFilter(
                pattern = pattern.toRegex(),
                includeMatched = true
            )
        }
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
        configManager.saveConfig(
            triggers.value,
            aliases.value,
            hotkeys.value,
            variableManager.getAllVariables(),
            tabManager.getTabsForSave(),
            _encoding,
            _miniMapWidth.value,
            _currentTheme.value,
            _fontFamily.value,
            _fontSize.value,
            _connectionProfiles.value,
            _currentProfileId.value
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
        val clampedWidth = width.coerceIn(150, 500)
        _miniMapWidth.value = clampedWidth
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
        _currentProfileId.value = profileId
        // При выборе профиля обновляем кодировку
        profileId?.let { id ->
            val profile = _connectionProfiles.value.find { it.id == id }
            profile?.let {
                setEncoding(it.encoding)
            }
        }
        saveConfig()
        logger.info { "Set current profile: $profileId" }
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

        // Загружаем ширину миникарты
        _miniMapWidth.value = configData.miniMapWidth

        // Загружаем тему
        _currentTheme.value = configData.theme

        // Загружаем настройки шрифта
        _fontFamily.value = configData.fontFamily
        _fontSize.value = configData.fontSize

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

    fun setRoomTags(roomId: String, tags: Set<String>) {
        mapManager.setRoomTags(roomId, tags)
    }

    fun exportMap(): Map<String, com.bylins.client.mapper.Room> {
        return mapManager.exportMap()
    }

    fun importMap(rooms: Map<String, com.bylins.client.mapper.Room>) {
        mapManager.importMap(rooms)
    }

    fun saveMapToFile(filePath: String? = null): Boolean {
        return mapManager.saveToFile(filePath)
    }

    fun loadMapFromFile(filePath: String? = null): Boolean {
        return mapManager.loadFromFile(filePath)
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

    // Работа с базой данных карт
    fun saveMapToDatabase(name: String, description: String = ""): Boolean {
        return mapManager.saveMapToDatabase(name, description)
    }

    fun loadMapFromDatabase(name: String): Boolean {
        return mapManager.loadMapFromDatabase(name)
    }

    fun listMapsInDatabase(): List<com.bylins.client.mapper.MapInfo> {
        return mapManager.listMapsInDatabase()
    }

    fun deleteMapFromDatabase(name: String): Boolean {
        return mapManager.deleteMapFromDatabase(name)
    }

    // Управление скриптами
    private fun initializeScripting() {
        // Создаем реализацию ScriptAPI
        val scriptAPI = com.bylins.client.scripting.ScriptAPIImpl(
            sendCommand = { command -> send(command) },
            echoText = { text -> telnetClient.addToOutputRaw(text) },  // Raw чтобы избежать рекурсии триггеров
            logMessage = { message -> logger.info { message } },
            triggerActions = createTriggerActions(),
            aliasActions = createAliasActions(),
            timerActions = createTimerActions(),
            variableActions = createVariableActions(),
            msdpActions = createMsdpActions(),
            gmcpActions = createGmcpActions(),
            mapperActions = createMapperActions()
        )

        // Создаем ScriptManager
        scriptManager = com.bylins.client.scripting.ScriptManager(scriptAPI)

        // Регистрируем движки
        scriptManager.registerEngine(com.bylins.client.scripting.engines.JavaScriptEngine())
        scriptManager.registerEngine(com.bylins.client.scripting.engines.PythonEngine())
        scriptManager.registerEngine(com.bylins.client.scripting.engines.LuaEngine())
        scriptManager.registerEngine(com.bylins.client.scripting.engines.PerlEngine())

        // Автозагрузка скриптов
        scriptManager.autoLoadScripts()
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
     * Создаёт PluginAPI для плагина
     */
    private fun createPluginAPI(pluginId: String, dataFolder: java.io.File): com.bylins.client.plugins.PluginAPIImpl {
        return com.bylins.client.plugins.PluginAPIImpl(
            pluginId = pluginId,
            sendCommand = { command -> send(command) },
            echoText = { text -> telnetClient.addToOutputRaw(text) },  // Raw чтобы избежать рекурсии триггеров
            eventBus = pluginEventBus,
            variableGetter = { name -> variableManager.getVariable(name) },
            variableSetter = { name, value -> variableManager.setVariable(name, value) },
            variableDeleter = { name -> variableManager.removeVariable(name) },
            getAllVariablesFunc = { variableManager.getAllVariables() },
            msdpGetter = { key -> _msdpData.value[key] },
            getAllMsdpFunc = { _msdpData.value },
            gmcpGetter = { packageName -> _gmcpData.value[packageName]?.toString() },
            getAllGmcpFunc = { _gmcpData.value.mapValues { it.value.toString() } },
            gmcpSender = { packageName, data -> /* TODO: отправка GMCP */ },
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
            // DEBUG: выводим байты паттерна
            val patternBytes = pattern.toByteArray(Charsets.UTF_8).joinToString(" ") { "%02X".format(it) }
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
            return variableManager.getVariable(name)
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
