package com.bylins.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bylins.client.ClientState
import com.bylins.client.ui.components.*
import com.bylins.client.ui.theme.AppTheme
import com.bylins.client.ui.theme.LocalAppColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import java.awt.Cursor

@Composable
fun MainWindow() {
    val clientState = remember { ClientState() }
    var selectedTab by remember { mutableStateOf(0) }
    val inputFocusRequester = remember { FocusRequester() }
    val isConnected by clientState.isConnected.collectAsState()
    val msdpEnabled by clientState.msdpEnabled.collectAsState()

    // Фокусируем input после подключения
    LaunchedEffect(isConnected) {
        if (isConnected) {
            inputFocusRequester.requestFocus()
        }
    }

    // Получаем текущую тему
    val currentThemeName by clientState.currentTheme.collectAsState()
    val appColorScheme = remember(currentThemeName) {
        AppTheme.getColorScheme(currentThemeName)
    }
    val materialColorScheme = remember(appColorScheme, currentThemeName) {
        AppTheme.toMaterialColorScheme(appColorScheme, isDark = currentThemeName != "LIGHT")
    }

    CompositionLocalProvider(LocalAppColorScheme provides appColorScheme) {
        MaterialTheme(
            colorScheme = materialColorScheme
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

                        // Если hotkey не обработан, фокусируем input для обычного ввода
                        if (!handled && !event.isCtrlPressed && !event.isAltPressed) {
                            inputFocusRequester.requestFocus()
                        }

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
                    containerColor = appColorScheme.surface,
                    contentColor = appColorScheme.onSurface
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
                        text = { Text("Статистика") }
                    )
                    Tab(
                        selected = selectedTab == 5,
                        onClick = { selectedTab = 5 },
                        text = { Text("Графики") }
                    )
                    Tab(
                        selected = selectedTab == 6,
                        onClick = { selectedTab = 6 },
                        text = { Text("Карта") }
                    )
                    Tab(
                        selected = selectedTab == 7,
                        onClick = { selectedTab = 7 },
                        text = { Text("Скрипты") }
                    )
                    Tab(
                        selected = selectedTab == 8,
                        onClick = { selectedTab = 8 },
                        text = { Text("Плагины") }
                    )
                    Tab(
                        selected = selectedTab == 9,
                        onClick = { selectedTab = 9 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (msdpEnabled) Color(0xFF4CAF50) else Color(0xFFFF5555),
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                )
                                Text("MSDP")
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 10,
                        onClick = { selectedTab = 10 },
                        text = { Text("GMCP") }
                    )
                    Tab(
                        selected = selectedTab == 11,
                        onClick = { selectedTab = 11 },
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
                            val miniMapWidth by clientState.miniMapWidth.collectAsState()
                            val density = LocalDensity.current

                            Row(modifier = Modifier.fillMaxSize()) {
                                // Область вывода текста
                                OutputPanel(
                                    clientState = clientState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )

                                // Draggable divider (вертикальный разделитель)
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .fillMaxHeight()
                                        .background(Color(0xFF555555))
                                        .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                // Уменьшаем ширину при движении вправо (положительный dragAmount.x)
                                                // и увеличиваем при движении влево (отрицательный dragAmount.x)
                                                val newWidth = (miniMapWidth - dragAmount.x / density.density).toInt()
                                                clientState.setMiniMapWidth(newWidth)
                                            }
                                        }
                                )

                                // Боковая панель с статусом и мини-картой
                                Column(
                                    modifier = Modifier
                                        .width(miniMapWidth.dp)
                                        .fillMaxHeight()
                                ) {
                                    // Статус персонажа
                                    StatusPanel(
                                        clientState = clientState,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(0.5f)
                                    )

                                    Divider()

                                    // Мини-карта
                                    MiniMapPanel(
                                        clientState = clientState,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(0.5f)
                                    )
                                }
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
                            // Панель статистики
                            StatsPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        5 -> {
                            // Панель графиков
                            StatsGraphPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        6 -> {
                            // Панель карты
                            MapPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        7 -> {
                            // Панель скриптов
                            ScriptsPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        8 -> {
                            // Панель плагинов
                            PluginsPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        9 -> {
                            // Панель MSDP данных
                            MsdpPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        10 -> {
                            // Панель GMCP данных
                            GmcpPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        11 -> {
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
                    focusRequester = inputFocusRequester,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
        }
        }
    }
}
