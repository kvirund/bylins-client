package com.bylins.client.network

import com.bylins.client.ClientState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class TelnetClient(
    private val clientState: ClientState? = null,
    encoding: String = "UTF-8"
) {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _receivedData = MutableStateFlow("")
    val receivedData: StateFlow<String> = _receivedData

    private val telnetParser = TelnetParser(encoding)
    private val msdpParser = MsdpParser()

    /**
     * Устанавливает кодировку для telnet соединения
     */
    fun setEncoding(encoding: String) {
        telnetParser.setEncoding(encoding)
        println("Кодировка telnet изменена на: $encoding")
    }

    // Ограничение на размер буфера вывода (1 МБ)
    // Уменьшено для экономии памяти - вкладки хранят свою историю отдельно
    private val MAX_BUFFER_SIZE = 1 * 1024 * 1024 // 1 MB

    suspend fun connect(host: String, port: Int) = withContext(Dispatchers.IO) {
        try {
            // Убеждаемся что предыдущее соединение закрыто
            if (_isConnected.value) {
                disconnect()
            }

            socket = Socket(host, port)
            inputStream = socket?.getInputStream()
            outputStream = socket?.getOutputStream()
            _isConnected.value = true

            // Уведомляем пользователя об успешном подключении
            appendToBuffer("\u001B[1;32m[Подключено к $host:$port]\u001B[0m\n\n")

            // Отправляем поддерживаемые опции Telnet
            sendTelnetNegotiation()

            // Запускаем чтение данных
            startReading()
        } catch (e: IOException) {
            disconnect()
            throw e
        }
    }

    fun disconnect() {
        try {
            readJob?.cancel()
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Обнуляем ресурсы
            readJob = null
            inputStream = null
            outputStream = null
            socket = null

            // Меняем статус подключения
            val wasConnected = _isConnected.value
            _isConnected.value = false

            // Уведомляем пользователя о разрыве соединения (только если были подключены)
            if (wasConnected) {
                appendToBuffer("\u001B[1;31m[Соединение разорвано]\u001B[0m\n")
            }
        }
    }

    suspend fun send(command: String) = withContext(Dispatchers.IO) {
        try {
            outputStream?.write("$command\r\n".toByteArray(Charsets.UTF_8))
            outputStream?.flush()
        } catch (e: IOException) {
            disconnect()
            throw e
        }
    }

    /**
     * Добавляет текст в лог (для эхо команд)
     */
    fun echoCommand(command: String) {
        appendToBuffer("\u001B[1;36m$command\u001B[0m\n")
    }

    /**
     * Добавляет произвольный текст в output (для системных сообщений)
     */
    fun addToOutput(text: String) {
        val textWithNewline = text + "\n"
        // Обрабатываем текст триггерами и получаем модифицированную версию с colorize
        val modifiedText = clientState?.processIncomingText(textWithNewline) ?: textWithNewline
        appendToBuffer(modifiedText)
    }

    /**
     * Добавляет текст в буфер с ограничением размера
     */
    private fun appendToBuffer(text: String) {
        var newValue = _receivedData.value + text

        // Если буфер превышает лимит, обрезаем старые строки
        if (newValue.length > MAX_BUFFER_SIZE) {
            // Находим позицию, с которой начинаем хранить данные (оставляем последние 80% буфера)
            val keepSize = (MAX_BUFFER_SIZE * 0.8).toInt()
            val cutPosition = newValue.length - keepSize

            // Ищем ближайший перенос строки после позиции обрезки, чтобы не обрезать строку посередине
            val nextNewline = newValue.indexOf('\n', cutPosition)
            if (nextNewline != -1) {
                newValue = newValue.substring(nextNewline + 1)
            } else {
                newValue = newValue.substring(cutPosition)
            }

            // Добавляем сообщение о том, что буфер был обрезан
            newValue = "\u001B[1;33m[Буфер вывода был очищен для экономии памяти]\u001B[0m\n\n" + newValue
        }

        _receivedData.value = newValue
    }

    private fun startReading() {
        readJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val buffer = ByteArray(4096)
                while (isActive && _isConnected.value) {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead == -1) {
                        // Соединение закрыто сервером
                        disconnect()
                        break
                    }

                    val data = buffer.copyOf(bytesRead)
                    val (text, telnetCommands) = telnetParser.parse(data)

                    if (text.isNotEmpty()) {
                        // Обрабатываем текст триггерами и получаем модифицированную версию с colorize
                        val modifiedText = clientState?.processIncomingText(text) ?: text
                        appendToBuffer(modifiedText)
                    }

                    // Обработка telnet команд
                    telnetCommands.forEach { handleTelnetCommand(it) }
                }
            } catch (e: IOException) {
                // Ошибка чтения - разрываем соединение
                if (isActive) {
                    disconnect()
                }
            } finally {
                // Убеждаемся что соединение закрыто
                if (_isConnected.value) {
                    disconnect()
                }
            }
        }
    }

    private fun sendTelnetNegotiation() {
        // IAC WILL TERMINAL_TYPE
        sendTelnetCommand(byteArrayOf(IAC, WILL, TERMINAL_TYPE))

        // IAC DO NAWS (Negotiate About Window Size)
        sendTelnetCommand(byteArrayOf(IAC, DO, NAWS))

        // IAC WILL MSDP
        sendTelnetCommand(byteArrayOf(IAC, WILL, MSDP))

        // IAC DO GMCP (для расширенных данных)
        sendTelnetCommand(byteArrayOf(IAC, DO, GMCP))
    }

    private fun sendTelnetCommand(command: ByteArray) {
        try {
            outputStream?.write(command)
            outputStream?.flush()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun handleTelnetCommand(command: TelnetCommand) {
        when (command.type) {
            TelnetCommandType.DO -> {
                // Сервер просит нас включить опцию
                when (command.option) {
                    TERMINAL_TYPE -> sendTelnetCommand(byteArrayOf(IAC, WILL, TERMINAL_TYPE))
                    MSDP -> sendTelnetCommand(byteArrayOf(IAC, WILL, MSDP))
                }
            }
            TelnetCommandType.WILL -> {
                // Сервер сообщает, что будет использовать опцию
                when (command.option) {
                    MSDP -> sendTelnetCommand(byteArrayOf(IAC, DO, MSDP))
                    GMCP -> sendTelnetCommand(byteArrayOf(IAC, DO, GMCP))
                }
            }
            TelnetCommandType.SUBNEGOTIATION -> {
                handleSubnegotiation(command.option, command.data)
            }
            else -> { /* Ignore other commands for now */ }
        }
    }

    private fun handleSubnegotiation(option: Byte, data: ByteArray) {
        when (option) {
            TERMINAL_TYPE -> {
                // Отправляем тип терминала
                val termType = "xterm-256color".toByteArray()
                val response = byteArrayOf(IAC, SB, TERMINAL_TYPE, 0) + termType + byteArrayOf(IAC, SE)
                sendTelnetCommand(response)
            }
            MSDP -> {
                // Обработка MSDP данных
                parseMSDP(data)
            }
            GMCP -> {
                // Обработка GMCP данных
                parseGMCP(data)
            }
        }
    }

    private fun parseMSDP(data: ByteArray) {
        try {
            val msdpData = msdpParser.parse(data)
            clientState?.updateMsdpData(msdpData)

            // Debug output
            println("MSDP Data: $msdpData")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseGMCP(data: ByteArray) {
        // TODO: Implement GMCP parsing
        // GMCP формат: JSON
        val json = String(data, Charsets.UTF_8)
        println("GMCP: $json")
    }

    companion object {
        // Telnet команды
        const val IAC: Byte = 255.toByte()    // Interpret As Command
        const val DONT: Byte = 254.toByte()
        const val DO: Byte = 253.toByte()
        const val WONT: Byte = 252.toByte()
        const val WILL: Byte = 251.toByte()
        const val SB: Byte = 250.toByte()     // Subnegotiation Begin
        const val SE: Byte = 240.toByte()     // Subnegotiation End

        // Telnet опции
        const val TERMINAL_TYPE: Byte = 24
        const val NAWS: Byte = 31            // Negotiate About Window Size
        const val MSDP: Byte = 69            // MUD Server Data Protocol
        const val GMCP: Byte = 201.toByte()  // Generic MUD Communication Protocol
    }
}
