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

@Composable
fun InputPanel(modifier: Modifier = Modifier) {
    var inputText by remember { mutableStateOf("") }
    val commandHistory = remember { mutableListOf<String>() }
    var historyIndex by remember { mutableStateOf(-1) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Команда") },
            modifier = Modifier
                .weight(1f)
                .onPreviewKeyEvent { event ->
                    when {
                        event.key == Key.Enter && event.type == KeyEventType.KeyDown -> {
                            if (inputText.isNotBlank()) {
                                commandHistory.add(inputText)
                                historyIndex = -1
                                // TODO: Send command to server
                                println("Command: $inputText")
                                inputText = ""
                            }
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
            onClick = {
                if (inputText.isNotBlank()) {
                    commandHistory.add(inputText)
                    historyIndex = -1
                    // TODO: Send command to server
                    println("Command: $inputText")
                    inputText = ""
                }
            }
        ) {
            Icon(Icons.Default.Send, contentDescription = "Отправить")
        }
    }
}
