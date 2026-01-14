package com.bylins.client.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bylins.client.ClientState

@Composable
fun InputPanel(
    clientState: ClientState,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    val commandHistory = remember { mutableListOf<String>() }
    var historyIndex by remember { mutableStateOf(-1) }
    val isConnected by clientState.isConnected.collectAsState()

    // Максимальное количество команд в истории
    val MAX_HISTORY_SIZE = 500

    // Автоматически фокусируемся при первом рендере
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun sendCommand() {
        val text = inputText.text
        val isLocalCommand = text.startsWith("#")

        // Локальные команды (#vars, #help и т.д.) работают всегда
        // Остальные команды требуют подключения
        if (isLocalCommand || isConnected) {
            // Добавляем в историю только непустые команды
            if (text.isNotBlank()) {
                commandHistory.add(text)

                // Ограничиваем размер истории
                if (commandHistory.size > MAX_HISTORY_SIZE) {
                    commandHistory.removeAt(0) // Удаляем самую старую команду
                }
            }
            historyIndex = -1
            clientState.send(text)
            inputText = TextFieldValue("")
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = { newValue ->
                // Игнорируем ввод сразу после срабатывания хоткея (предотвращает дублирование NumPad цифр)
                if (!clientState.wasHotkeyRecentlyProcessed()) {
                    inputText = newValue
                }
            },
            label = { Text(if (isConnected) "Команда" else "Команда (# для локальных)") },
            enabled = true,  // Всегда включено для локальных команд (#vars, #help и т.д.)
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    when {
                        (event.key == Key.Enter || event.key == Key.NumPadEnter) && event.type == KeyEventType.KeyDown -> {
                            sendCommand()
                            true
                        }
                        event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown -> {
                            if (commandHistory.isNotEmpty()) {
                                historyIndex = (historyIndex + 1).coerceAtMost(commandHistory.size - 1)
                                val command = commandHistory[commandHistory.size - 1 - historyIndex]
                                inputText = TextFieldValue(
                                    text = command,
                                    selection = TextRange(command.length)
                                )
                            }
                            true
                        }
                        event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown -> {
                            if (historyIndex > 0) {
                                historyIndex--
                                val command = commandHistory[commandHistory.size - 1 - historyIndex]
                                inputText = TextFieldValue(
                                    text = command,
                                    selection = TextRange(command.length)
                                )
                            } else {
                                historyIndex = -1
                                inputText = TextFieldValue("")
                            }
                            true
                        }
                        else -> false
                    }
                },
            singleLine = true
        )

        IconButton(
            onClick = { sendCommand() },
            enabled = true  // Всегда включено для локальных команд
        ) {
            Icon(Icons.Default.Send, contentDescription = "Отправить")
        }
    }
}
