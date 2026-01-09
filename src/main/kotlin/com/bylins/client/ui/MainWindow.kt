package com.bylins.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.bylins.client.ClientState
import com.bylins.client.ui.components.*

@Composable
fun MainWindow() {
    val clientState = remember { ClientState() }
    var selectedTab by remember { mutableStateOf(0) }

    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        // Обрабатываем горячие клавиши
                        val handled = clientState.processHotkey(
                            key = event.key,
                            isCtrlPressed = event.isCtrlPressed,
                            isAltPressed = event.isAltPressed,
                            isShiftPressed = event.isShiftPressed
                        )
                        handled
                    } else {
                        false
                    }
                },
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

                // Вкладки
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF2D2D2D),
                    contentColor = Color.White
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Главная") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Триггеры") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Алиасы") }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Хоткеи") }
                    )
                    Tab(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        text = { Text("Настройки") }
                    )
                }

                Divider()

                // Основная область в зависимости от выбранной вкладки
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        0 -> {
                            // Главный вид с выводом текста и боковой панелью
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Область вывода текста
                                OutputPanel(
                                    clientState = clientState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )

                                // Боковая панель статуса персонажа
                                StatusPanel(
                                    clientState = clientState,
                                    modifier = Modifier
                                        .width(250.dp)
                                        .fillMaxHeight()
                                )
                            }
                        }
                        1 -> {
                            // Панель триггеров
                            TriggersPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        2 -> {
                            // Панель алиасов
                            AliasesPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        3 -> {
                            // Панель горячих клавиш
                            HotkeysPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        4 -> {
                            // Панель настроек
                            SettingsPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Divider()

                // Поле ввода команд (доступно всегда)
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
