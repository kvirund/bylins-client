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
