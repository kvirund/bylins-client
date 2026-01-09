package com.bylins.client.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.bylins.client.ClientState

@Composable
fun InputPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val commandHistory = remember { mutableListOf<String>() }
    var historyIndex by remember { mutableStateOf(-1) }
    val isConnected by clientState.isConnected.collectAsState()

    fun sendCommand() {
        if (inputText.isNotBlank() && isConnected) {
            commandHistory.add(inputText)
            historyIndex = -1
            clientState.send(inputText)
            inputText = ""
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Команда") },
            enabled = isConnected,
            modifier = Modifier
                .weight(1f)
                .onPreviewKeyEvent { event ->
                    when {
                        event.key == Key.Enter && event.type == KeyEventType.KeyDown -> {
                            sendCommand()
                            true
                        }
                        event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown -> {
                            if (commandHistory.isNotEmpty()) {
                                historyIndex = (historyIndex + 1).coerceAtMost(commandHistory.size - 1)
                                inputText = commandHistory[commandHistory.size - 1 - historyIndex]
                            }
                            true
                        }
                        event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown -> {
                            if (historyIndex > 0) {
                                historyIndex--
                                inputText = commandHistory[commandHistory.size - 1 - historyIndex]
                            } else {
                                historyIndex = -1
                                inputText = ""
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
            enabled = isConnected && inputText.isNotBlank()
        ) {
            Icon(Icons.Default.Send, contentDescription = "Отправить")
        }
    }
}
