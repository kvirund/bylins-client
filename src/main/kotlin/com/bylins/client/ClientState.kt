package com.bylins.client

import com.bylins.client.aliases.AliasManager
import com.bylins.client.network.TelnetClient
import com.bylins.client.triggers.TriggerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ClientState {
    private val scope = CoroutineScope(Dispatchers.Main)

    // AliasManager и TriggerManager инициализируются первыми
    private val aliasManager = AliasManager { command ->
        // Callback для отправки команд из алиасов (без рекурсии)
        sendRaw(command)
    }

    private val triggerManager = TriggerManager { command ->
        // Callback для отправки команд из триггеров
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

    init {
        // Загружаем стандартные триггеры и алиасы
        loadDefaultAliases()
        loadDefaultTriggers()
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
    }

    fun removeTrigger(id: String) {
        triggerManager.removeTrigger(id)
    }

    fun enableTrigger(id: String) {
        triggerManager.enableTrigger(id)
    }

    fun disableTrigger(id: String) {
        triggerManager.disableTrigger(id)
    }

    // Управление алиасами
    fun addAlias(alias: com.bylins.client.aliases.Alias) {
        aliasManager.addAlias(alias)
    }

    fun removeAlias(id: String) {
        aliasManager.removeAlias(id)
    }

    fun enableAlias(id: String) {
        aliasManager.enableAlias(id)
    }

    fun disableAlias(id: String) {
        aliasManager.disableAlias(id)
    }
}
