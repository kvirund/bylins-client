package com.bylins.client.network

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

enum class TelnetCommandType {
    DO, DONT, WILL, WONT, SUBNEGOTIATION
}

data class TelnetCommand(
    val type: TelnetCommandType,
    val option: Byte,
    val data: ByteArray = byteArrayOf()
)

class TelnetParser(
    private var encoding: String = "UTF-8"  // Конфигурируемая кодировка
) {
    private enum class State {
        NORMAL, IAC, COMMAND, SUBNEGOTIATION, SUBNEG_IAC
    }

    private var state = State.NORMAL
    private var currentCommand: Byte = 0
    private var currentOption: Byte = 0
    private val subnegBuffer = ByteArrayOutputStream()

    private val textBuffer = ByteArrayOutputStream()
    private val commands = mutableListOf<TelnetCommand>()

    /**
     * Устанавливает кодировку для парсинга текста
     */
    fun setEncoding(newEncoding: String) {
        encoding = newEncoding
    }

    fun parse(data: ByteArray): Pair<String, List<TelnetCommand>> {
        textBuffer.reset()
        commands.clear()

        for (byte in data) {
            when (state) {
                State.NORMAL -> {
                    if (byte == TelnetClient.IAC) {
                        state = State.IAC
                    } else {
                        textBuffer.write(byte.toInt())
                    }
                }

                State.IAC -> {
                    when (byte) {
                        TelnetClient.DO, TelnetClient.DONT,
                        TelnetClient.WILL, TelnetClient.WONT -> {
                            currentCommand = byte
                            state = State.COMMAND
                        }
                        TelnetClient.SB -> {
                            subnegBuffer.reset()
                            state = State.SUBNEGOTIATION
                        }
                        TelnetClient.IAC -> {
                            // Escaped IAC (255)
                            textBuffer.write(TelnetClient.IAC.toInt())
                            state = State.NORMAL
                        }
                        else -> {
                            // Unknown command, ignore
                            state = State.NORMAL
                        }
                    }
                }

                State.COMMAND -> {
                    currentOption = byte
                    val commandType = when (currentCommand) {
                        TelnetClient.DO -> TelnetCommandType.DO
                        TelnetClient.DONT -> TelnetCommandType.DONT
                        TelnetClient.WILL -> TelnetCommandType.WILL
                        TelnetClient.WONT -> TelnetCommandType.WONT
                        else -> null
                    }

                    if (commandType != null) {
                        commands.add(TelnetCommand(commandType, currentOption))
                    }
                    state = State.NORMAL
                }

                State.SUBNEGOTIATION -> {
                    if (byte == TelnetClient.IAC) {
                        state = State.SUBNEG_IAC
                    } else {
                        if (currentOption == 0.toByte() && subnegBuffer.size() == 0) {
                            // Первый байт - это опция
                            currentOption = byte
                        } else {
                            subnegBuffer.write(byte.toInt())
                        }
                    }
                }

                State.SUBNEG_IAC -> {
                    when (byte) {
                        TelnetClient.SE -> {
                            // Конец subnegotiation
                            commands.add(
                                TelnetCommand(
                                    TelnetCommandType.SUBNEGOTIATION,
                                    currentOption,
                                    subnegBuffer.toByteArray()
                                )
                            )
                            currentOption = 0
                            state = State.NORMAL
                        }
                        TelnetClient.IAC -> {
                            // Escaped IAC внутри subnegotiation
                            subnegBuffer.write(TelnetClient.IAC.toInt())
                            state = State.SUBNEGOTIATION
                        }
                        else -> {
                            // Некорректная последовательность
                            state = State.NORMAL
                        }
                    }
                }
            }
        }

        // Используем конфигурируемую кодировку (по умолчанию UTF-8)
        val charset = try {
            Charset.forName(encoding)
        } catch (e: Exception) {
            println("Неподдерживаемая кодировка: $encoding, используется UTF-8")
            Charsets.UTF_8
        }
        val text = textBuffer.toString(charset)
        return Pair(text, commands.toList())
    }
}
