package com.bylins.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bylins.client.ClientState
import com.bylins.client.ui.components.ConnectionPanel
import com.bylins.client.ui.components.OutputPanel
import com.bylins.client.ui.components.InputPanel
import com.bylins.client.ui.components.StatusPanel

@Composable
fun MainWindow() {
    val clientState = remember { ClientState() }

    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Верхняя панель подключения
                ConnectionPanel(
                    clientState = clientState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )

                Divider()

                // Основная область с выводом текста и боковой панелью
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Область вывода текста
                    OutputPanel(
                        clientState = clientState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    // Боковая панель статуса персонажа
                    StatusPanel(
                        modifier = Modifier
                            .width(250.dp)
                            .fillMaxHeight()
                    )
                }

                Divider()

                // Поле ввода команд
                InputPanel(
                    clientState = clientState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
        }
    }
}
