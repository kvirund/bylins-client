package com.bylins.client

import com.bylins.client.network.TelnetClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ClientState {
    private val telnetClient = TelnetClient()
    private val scope = CoroutineScope(Dispatchers.Main)

    val isConnected: StateFlow<Boolean> = telnetClient.isConnected
    val receivedData: StateFlow<String> = telnetClient.receivedData

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

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
        scope.launch {
            try {
                telnetClient.send(command)
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка отправки: ${e.message}"
                e.printStackTrace()
            }
        }
    }
}
