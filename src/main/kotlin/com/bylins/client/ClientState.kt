package com.bylins.client

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class ClientState {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val configManager = ConfigManager()

    // Флаг для предотвращения множественного сохранения при инициализации
    private var isInitializing = true

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
        }
    )

    private val hotkeyManager = HotkeyManager { command ->
        // Callback для отправки команд из хоткеев
        send(command)
    }

    private val logManager = LogManager()
    private val sessionStats = SessionStats()
    private val variableManager = VariableManager()
    private val tabManager = TabManager()
    private val mapManager = com.bylins.client.mapper.MapManager(
        onRoomEnter = { room ->
            // Уведомляем скрипты о входе в комнату
            if (::scriptManager.isInitialized) {
                scriptManager.fireEvent(com.bylins.client.scripting.ScriptEvent.ON_ROOM_ENTER, room)
            }
        }
    )
    private val roomParser = com.bylins.client.mapper.RoomParser()

    private var lastCommand: String? = null

    private val telnetClient = TelnetClient(this)

    // Скриптинг - инициализируется позже
    private lateinit var scriptManager: com.bylins.client.scripting.ScriptManager

    val isConnected: StateFlow<Boolean> = telnetClient.isConnected
    val receivedData: StateFlow<String> = telnetClient.receivedData

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _msdpData = MutableStateFlow<Map<String, Any>>(emptyMap())
    val msdpData: StateFlow<Map<String, Any>> = _msdpData

    // Доступ к менеджерам
    val triggers = triggerManager.triggers
    val aliases = aliasManager.aliases
    val hotkeys = hotkeyManager.hotkeys

    // Доступ к логированию
    val isLogging = logManager.isLogging
    val currentLogFile = logManager.currentLogFile

    // Доступ к статистике
    val stats = sessionStats.stats

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
        // Инициализируем скриптинг
        initializeScripting()

        // Продолжаем стандартную инициализацию
        // Пытаемся загрузить сохранённую конфигурацию
        val configData = configManager.loadConfig()

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
        // Триггер для подсветки tells
        addTrigger(
            com.bylins.client.triggers.Trigger(
                id = "tell-notify",
                name = "Tell Notification",
                pattern = "^(.+) говорит вам:".toRegex(),
                commands = emptyList(),
                enabled = true,
                priority = 10,
                colorize = com.bylins.client.triggers.TriggerColorize(
                    foreground = "#00FF00",
                    bold = true
                )
            )
        )

        // Триггер для подсветки шепота
        addTrigger(
            com.bylins.client.triggers.Trigger(
                id = "whisper-notify",
                name = "Whisper Notification",
                pattern = "^(.+) шепчет вам:".toRegex(),
                commands = emptyList(),
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
                // Уведомляем скрипты о подключении
                if (::scriptManager.isInitialized) {
                    scriptManager.fireEvent(com.bylins.client.scripting.ScriptEvent.ON_CONNECT)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка подключения: ${e.message}"
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        telnetClient.disconnect()
        // Останавливаем сбор статистики
        sessionStats.stopSession()
        // Останавливаем логирование
        logManager.stopLogging()
        // Уведомляем скрипты
        if (::scriptManager.isInitialized) {
            scriptManager.fireEvent(com.bylins.client.scripting.ScriptEvent.ON_DISCONNECT)
        }
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
        // Сохраняем команду для автомаппера
        lastCommand = command

        // Уведомляем скрипты
        if (::scriptManager.isInitialized) {
            scriptManager.fireEvent(com.bylins.client.scripting.ScriptEvent.ON_COMMAND, command)
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

    fun updateMsdpData(data: Map<String, Any>) {
        _msdpData.value = _msdpData.value + data

        // Автоматически обновляем переменные из MSDP
        data.forEach { (key, value) ->
            variableManager.setVariable(key.lowercase(), value.toString())
        }

        // Уведомляем скрипты
        if (::scriptManager.isInitialized) {
            scriptManager.fireEvent(com.bylins.client.scripting.ScriptEvent.ON_MSDP, data)
        }
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

            // Уведомляем скрипты о новой строке
            if (::scriptManager.isInitialized) {
                scriptManager.fireEvent(com.bylins.client.scripting.ScriptEvent.ON_LINE, cleanLine)
            }

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

        configManager.saveConfig(
            triggers.value,
            aliases.value,
            hotkeys.value,
            variableManager.getAllVariables(),
            tabManager.getTabsForSave()
        )
    }

    fun exportConfig(file: File) {
        configManager.exportConfig(
            file,
            triggers.value,
            aliases.value,
            hotkeys.value,
            variableManager.getAllVariables(),
            tabManager.getTabsForSave()
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

    fun getMapBounds(level: Int): com.bylins.client.mapper.MapBounds? {
        return mapManager.getMapBounds(level)
    }

    fun getRoomsOnLevel(level: Int): List<com.bylins.client.mapper.Room> {
        return mapManager.getRoomsOnLevel(level)
    }

    fun exportMap(): Map<String, com.bylins.client.mapper.Room> {
        return mapManager.exportMap()
    }

    fun importMap(rooms: Map<String, com.bylins.client.mapper.Room>) {
        mapManager.importMap(rooms)
    }

    // Управление скриптами
    private fun initializeScripting() {
        // Создаем реализацию ScriptAPI
        val scriptAPI = com.bylins.client.scripting.ScriptAPIImpl(
            sendCommand = { command -> send(command) },
            echoText = { text -> telnetClient.addToOutput(text) },
            logMessage = { message -> println(message) },
            triggerActions = createTriggerActions(),
            aliasActions = createAliasActions(),
            timerActions = createTimerActions(),
            variableActions = createVariableActions(),
            msdpActions = createMsdpActions(),
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

    private fun createTriggerActions() = object : com.bylins.client.scripting.TriggerActions {
        override fun addTrigger(pattern: String, callback: (String, Map<Int, String>) -> Unit): String {
            val triggerId = java.util.UUID.randomUUID().toString()
            // TODO: Добавить триггер из скрипта
            return triggerId
        }

        override fun removeTrigger(id: String) {
            removeTrigger(id)
        }

        override fun enableTrigger(id: String) {
            enableTrigger(id)
        }

        override fun disableTrigger(id: String) {
            disableTrigger(id)
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
                    println("[Timer] Error in setTimeout: ${e.message}")
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
                        println("[Timer] Error in setInterval: ${e.message}")
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
        }

        override fun deleteVariable(name: String) {
            variableManager.removeVariable(name)
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

    private fun createMapperActions() = object : com.bylins.client.scripting.MapperActions {
        override fun getCurrentRoom(): Map<String, Any>? {
            val room = mapManager.getCurrentRoom() ?: return null
            return mapOf(
                "id" to room.id,
                "name" to room.name,
                "x" to room.x,
                "y" to room.y,
                "z" to room.z,
                "exits" to room.getAvailableDirections().map { it.name },
                "notes" to room.notes
            )
        }

        override fun getRoomAt(x: Int, y: Int, z: Int): Map<String, Any>? {
            val room = mapManager.findRoomAt(x, y, z) ?: return null
            return mapOf(
                "id" to room.id,
                "name" to room.name,
                "x" to room.x,
                "y" to room.y,
                "z" to room.z,
                "exits" to room.getAvailableDirections().map { it.name },
                "notes" to room.notes
            )
        }

        override fun setRoomNote(roomId: String, note: String) {
            mapManager.setRoomNote(roomId, note)
        }

        override fun setRoomColor(roomId: String, color: String?) {
            mapManager.setRoomColor(roomId, color)
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
}
