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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
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
    private val statsHistory = com.bylins.client.stats.StatsHistory()
    private val soundManager = com.bylins.client.audio.SoundManager()
    private val variableManager = VariableManager()
    private val tabManager = TabManager()

    // Для throttling звуковых уведомлений
    private var lastLowHpSoundTime = 0L
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

    private val telnetClient = TelnetClient(this, _encoding)

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
        // Инициализируем скриптинг
        initializeScripting()

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
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка подключения: ${e.message}"
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        // Автосохранение карты перед отключением
        if (mapManager.rooms.value.isNotEmpty()) {
            mapManager.saveToFile()
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

    fun updateMsdpData(data: Map<String, Any>) {
        _msdpData.value = _msdpData.value + data

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

        configManager.saveConfig(
            triggers.value,
            aliases.value,
            hotkeys.value,
            variableManager.getAllVariables(),
            tabManager.getTabsForSave(),
            _encoding,
            _miniMapWidth.value,
            _currentTheme.value
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
        println("[ClientState] Theme changed to: $themeName")
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
            _currentTheme.value
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

    fun setRoomTags(roomId: String, tags: Set<String>) {
        mapManager.setRoomTags(roomId, tags)
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

    // Управление звуками
    fun setSoundEnabled(enabled: Boolean) = soundManager.setSoundEnabled(enabled)
    fun setSoundVolume(volume: Float) = soundManager.setVolume(volume)
    fun playSound(type: com.bylins.client.audio.SoundManager.SoundType) = soundManager.playSound(type)
}
