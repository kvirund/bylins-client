package com.bylins.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import com.bylins.client.ui.components.*
import com.bylins.client.ui.theme.AppTheme
import com.bylins.client.ui.theme.LocalAppColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import java.awt.Cursor

// Определение вкладки
data class TabDef(val id: String, val name: String)

// Все доступные вкладки
val ALL_TABS = listOf(
    TabDef("main", "Вывод"),
    TabDef("triggers", "Триггеры"),
    TabDef("aliases", "Алиасы"),
    TabDef("hotkeys", "Хоткеи"),
    TabDef("context", "Контекст"),
    TabDef("stats", "Статистика"),
    TabDef("graphs", "Графики"),
    TabDef("map", "Карта"),
    TabDef("scripts", "Скрипты"),
    TabDef("plugins", "Плагины"),
    TabDef("msdp", "MSDP"),
    TabDef("gmcp", "GMCP"),
    TabDef("settings", "Настройки"),
    TabDef("profiles", "Профили")
)

@Composable
fun MainWindow() {
    val clientState = remember { ClientState() }
    var selectedTabId by remember { mutableStateOf("main") }
    val inputFocusRequester = remember { FocusRequester() }
    val isConnected by clientState.isConnected.collectAsState()
    val msdpEnabled by clientState.msdpEnabled.collectAsState()
    val secondaryTextFieldFocused by clientState.secondaryTextFieldFocused.collectAsState()
    val hiddenTabs by clientState.hiddenTabs.collectAsState()

    // Фильтруем видимые вкладки
    val visibleTabs = ALL_TABS.filter { it.id !in hiddenTabs }
    val selectedTabIndex = visibleTabs.indexOfFirst { it.id == selectedTabId }.coerceAtLeast(0)

    // Отслеживаем последний обработанный KeyDown для поглощения KeyUp
    var lastHandledKey by remember { mutableStateOf<androidx.compose.ui.input.key.Key?>(null) }

    // Фокусируем input после подключения
    LaunchedEffect(isConnected) {
        if (isConnected) {
            inputFocusRequester.requestFocus()
        }
    }

    // Фокусируем input по запросу (например, после завершения следования)
    val requestInputFocusTrigger by clientState.requestInputFocus.collectAsState()
    LaunchedEffect(requestInputFocusTrigger) {
        if (requestInputFocusTrigger > 0) {
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
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            // Обрабатываем горячие клавиши
                            val handled = clientState.processHotkey(
                                key = event.key,
                                isCtrlPressed = event.isCtrlPressed,
                                isAltPressed = event.isAltPressed,
                                isShiftPressed = event.isShiftPressed
                            )

                            if (handled) {
                                // Запоминаем клавишу чтобы поглотить KeyUp
                                lastHandledKey = event.key
                            } else if (!event.isCtrlPressed && !event.isAltPressed && !secondaryTextFieldFocused) {
                                // Если hotkey не обработан и не редактируется вторичное поле,
                                // фокусируем input для обычного ввода
                                inputFocusRequester.requestFocus()
                            }

                            handled
                        }
                        KeyEventType.KeyUp -> {
                            // Поглощаем KeyUp если соответствующий KeyDown был обработан
                            if (lastHandledKey == event.key) {
                                lastHandledKey = null
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
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
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = appColorScheme.surface,
                    contentColor = appColorScheme.onSurface,
                    edgePadding = 0.dp
                ) {
                    visibleTabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabId = tab.id },
                            text = {
                                if (tab.id == "msdp") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(
                                                    color = if (msdpEnabled) appColorScheme.success else appColorScheme.error,
                                                    shape = androidx.compose.foundation.shape.CircleShape
                                                )
                                        )
                                        Text(tab.name)
                                    }
                                } else {
                                    Text(tab.name)
                                }
                            }
                        )
                    }
                }

                Divider()

                // Основная область в зависимости от выбранной вкладки
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTabId) {
                        "main" -> {
                            // Вывод от сервера с боковой панелью
                            val miniMapWidth by clientState.miniMapWidth.collectAsState()
                            val density = LocalDensity.current

                            Row(modifier = Modifier.fillMaxSize()) {
                                OutputPanel(
                                    clientState = clientState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )

                                // Draggable divider
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .fillMaxHeight()
                                        .background(appColorScheme.border)
                                        .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val newWidth = (miniMapWidth - dragAmount.x / density.density).toInt()
                                                clientState.setMiniMapWidth(newWidth)
                                            }
                                        }
                                )

                                // Боковая панель со статусом
                                val statusElements by clientState.statusManager.elements.collectAsState()
                                if (statusElements.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .width(miniMapWidth.dp)
                                            .fillMaxHeight()
                                    ) {
                                        StatusPanel(
                                            clientState = clientState,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight()
                                        )
                                    }
                                }
                            }
                        }
                        "triggers" -> {
                            // Панель триггеров
                            TriggersPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "aliases" -> {
                            // Панель алиасов
                            AliasesPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "hotkeys" -> {
                            // Панель горячих клавиш
                            HotkeysPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "context" -> {
                            // Панель контекстных команд
                            ContextCommandsPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "stats" -> {
                            // Панель статистики
                            StatsPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "graphs" -> {
                            // Панель графиков
                            StatsGraphPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "map" -> {
                            // Панель карты
                            MapPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "scripts" -> {
                            // Панель скриптов
                            ScriptsPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "plugins" -> {
                            // Панель плагинов с подвкладками
                            PluginsPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "msdp" -> {
                            // Панель MSDP данных
                            MsdpPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "gmcp" -> {
                            // Панель GMCP данных
                            GmcpPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "settings" -> {
                            // Панель настроек
                            SettingsPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "profiles" -> {
                            // Панель профилей персонажей
                            ProfilesPanel(
                                clientState = clientState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Divider()

                // Поле ввода команд с контекстными командами
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    // Контекстные команды (Alt+1-0) - над полем ввода
                    ContextCommandBar(
                        clientState = clientState,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    InputPanel(
                        clientState = clientState,
                        focusRequester = inputFocusRequester,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        }
    }
}
