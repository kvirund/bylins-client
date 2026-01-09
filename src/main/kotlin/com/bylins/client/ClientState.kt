package com.bylins.client

import com.bylins.client.aliases.AliasManager
import com.bylins.client.config.ConfigManager
import com.bylins.client.hotkeys.HotkeyManager
import com.bylins.client.network.TelnetClient
import com.bylins.client.triggers.TriggerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class ClientState {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val configManager = ConfigManager()

    // Менеджеры инициализируются первыми
    private val aliasManager = AliasManager { command ->
        // Callback для отправки команд из алиасов (без рекурсии)
        sendRaw(command)
    }

    private val triggerManager = TriggerManager { command ->
        // Callback для отправки команд из триггеров
        send(command)
    }

    private val hotkeyManager = HotkeyManager { command ->
        // Callback для отправки команд из хоткеев
        send(command)
    }

    private val telnetClient = TelnetClient(this)

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

    init {
        // Пытаемся загрузить сохранённую конфигурацию
        val (loadedTriggers, loadedAliases, loadedHotkeys) = configManager.loadConfig()

        if (loadedTriggers.isEmpty() && loadedAliases.isEmpty() && loadedHotkeys.isEmpty()) {
            // Если конфига нет, загружаем стандартные триггеры, алиасы и хоткеи
            loadDefaultAliases()
            loadDefaultTriggers()
            loadDefaultHotkeys()
        } else {
            // Загружаем сохранённую конфигурацию
            loadedTriggers.forEach { addTrigger(it) }
            loadedAliases.forEach { addAlias(it) }
            loadedHotkeys.forEach { addHotkey(it) }
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

    fun connect(host: String, port: Int) {
        scope.launch {
            try {
                _errorMessage.value = null
                telnetClient.connect(host, port)
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка подключения: ${e.message}"
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        telnetClient.disconnect()
    }

    fun send(command: String) {
        // Проверяем алиасы
        val handled = aliasManager.processCommand(command)
        if (!handled) {
            // Алиас не сработал, отправляем команду как есть
            sendRaw(command)
        }
    }

    private fun sendRaw(command: String) {
        // Эхо команды в лог
        telnetClient.echoCommand(command)

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
    }

    /**
     * Обрабатывает входящую строку текста (вызывается из TelnetClient)
     */
    fun processIncomingText(text: String) {
        // Разбиваем на строки и обрабатываем каждую триггерами
        val lines = text.split("\n")
        for (line in lines) {
            if (line.isNotEmpty()) {
                triggerManager.processLine(line)
            }
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
        return hotkeyManager.processKeyPress(key, isCtrlPressed, isAltPressed, isShiftPressed)
    }

    // Управление конфигурацией
    fun saveConfig() {
        configManager.saveConfig(triggers.value, aliases.value, hotkeys.value)
    }

    fun exportConfig(file: File) {
        configManager.exportConfig(file, triggers.value, aliases.value, hotkeys.value)
    }

    fun importConfig(file: File) {
        val (importedTriggers, importedAliases, importedHotkeys) = configManager.importConfig(file)

        // Очищаем текущие триггеры, алиасы и хоткеи
        triggerManager.clear()
        aliasManager.clear()
        hotkeyManager.clear()

        // Загружаем импортированные
        importedTriggers.forEach { addTrigger(it) }
        importedAliases.forEach { addAlias(it) }
        importedHotkeys.forEach { addHotkey(it) }

        // Сохраняем в основной конфиг
        saveConfig()
    }

    fun getConfigPath(): String = configManager.getConfigFile()
}
