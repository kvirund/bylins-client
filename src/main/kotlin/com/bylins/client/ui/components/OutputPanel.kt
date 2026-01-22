package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import com.bylins.client.ui.AnsiParser
import kotlinx.coroutines.launch

@Composable
fun OutputPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val tabs by clientState.tabs.collectAsState()
    val activeTabId by clientState.activeTabId.collectAsState()

    // Получаем настройки шрифта
    val fontFamilyName by clientState.fontFamily.collectAsState()
    val fontSize by clientState.fontSize.collectAsState()
    val fontFamily = remember(fontFamilyName) { getFontFamily(fontFamilyName) }

    var showTabDialog by remember { mutableStateOf(false) }
    var editingTab by remember { mutableStateOf<com.bylins.client.tabs.Tab?>(null) }

    Column(modifier = modifier) {
        // Вкладки с управлением
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Вкладки
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOfFirst { it.id == activeTabId }.takeIf { it >= 0 } ?: 0,
                containerColor = Color(0xFF1E1E1E),
                contentColor = Color.White,
                edgePadding = 0.dp,
                modifier = Modifier.weight(1f)
            ) {
                tabs.forEach { tab ->
                    // Подписываемся на индикатор непрочитанных сообщений
                    val hasUnread by tab.hasUnreadMessages.collectAsState()

                    Tab(
                        selected = tab.id == activeTabId,
                        onClick = { clientState.setActiveTab(tab.id) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(tab.name)
                                // Индикатор непрочитанных сообщений (оранжевая точка)
                                if (hasUnread && tab.id != activeTabId) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFFFF9800), shape = CircleShape)
                                    )
                                }
                                // Кнопки управления (кроме системных вкладок)
                                if (tab.id != "main" && tab.id != "logs") {
                                    IconButton(
                                        onClick = {
                                            editingTab = tab
                                            showTabDialog = true
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Text("✎", fontSize = 12.sp)
                                    }
                                    IconButton(
                                        onClick = { clientState.removeTab(tab.id) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Text("✕", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // Кнопка добавления новой вкладки
            IconButton(
                onClick = {
                    editingTab = null
                    showTabDialog = true
                }
            ) {
                Text("+", fontSize = 20.sp, color = Color.White)
            }
        }

        // Рендерим только активную вкладку
        val activeTab = tabs.find { it.id == activeTabId }
        val receivedData by clientState.receivedData.collectAsState()
        if (activeTab != null) {
            TabContent(
                tab = activeTab,
                receivedData = receivedData,
                fontFamily = fontFamily,
                fontSize = fontSize,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Диалог создания/редактирования вкладки
    if (showTabDialog) {
        TabDialog(
            tab = editingTab,
            onDismiss = {
                showTabDialog = false
                editingTab = null
            },
            onSave = { name, filters, captureMode ->
                if (editingTab != null) {
                    clientState.updateTab(editingTab!!.id, name, filters, captureMode)
                } else {
                    clientState.createTab(name, filters, captureMode)
                }
                showTabDialog = false
                editingTab = null
            }
        )
    }
}

/**
 * Преобразует строковое название семейства шрифтов в FontFamily
 */
private fun getFontFamily(familyName: String): FontFamily {
    return when (familyName) {
        "MONOSPACE" -> FontFamily.Monospace
        "SERIF" -> FontFamily.Serif
        "SANS_SERIF" -> FontFamily.SansSerif
        "CURSIVE" -> FontFamily.Cursive
        else -> FontFamily.Monospace
    }
}

/**
 * Получает последние N строк из текста для оптимизации рендеринга
 */
private fun getLastLines(text: String, maxLines: Int): String {
    if (text.isEmpty()) return text

    var lineCount = 0
    var position = text.length - 1

    // Считаем с конца
    while (position >= 0 && lineCount < maxLines) {
        if (text[position] == '\n') {
            lineCount++
        }
        position--
    }

    // Если достигли начала текста
    if (position < 0) return text

    // Возвращаем текст с позиции после найденной строки
    return text.substring(position + 1)
}

@Composable
fun TabContent(
    tab: com.bylins.client.tabs.Tab,
    receivedData: String = "",
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontSize: Int = 14
) {
    val scrollState = rememberScrollState()
    val ansiParser = remember(tab.id) { AnsiParser() }

    // Получаем содержимое вкладки
    val tabContent by tab.content.collectAsState()

    // Для главной вкладки используем receivedData (для правильной работы с промптом)
    // Для остальных вкладок - tab.content
    val displayText = if (tab.id == "main") receivedData else tabContent

    // КРИТИЧНО: Ограничиваем отображаемый текст последними 1000 строками
    // для избежания O(n²) сложности при парсинге ANSI кодов
    val limitedText = remember(displayText) {
        if (displayText.length > 100_000) { // Только если текст большой
            getLastLines(displayText, 1000)
        } else {
            displayText
        }
    }

    val outputText: AnnotatedString = remember(limitedText) {
        if (limitedText.isEmpty()) {
            if (tab.id == "main") {
                AnnotatedString("Добро пожаловать в Bylins MUD Client!\nПодключитесь к серверу для начала игры.\n\n")
            } else {
                AnnotatedString("${tab.name}: пусто\n")
            }
        } else {
            ansiParser.parse(limitedText)
        }
    }

    // Автопрокрутка вниз ТОЛЬКО при добавлении нового текста (не при переключении вкладок)
    // Используем limitedText вместо displayText для меньшей частоты вызовов
    LaunchedEffect(limitedText) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    val coroutineScope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    Box(
        modifier = modifier
            .background(Color.Black)
            .padding(8.dp)
    ) {
        // BasicTextField с readOnly для поддержки выделения текста
        BasicTextField(
            value = TextFieldValue(outputText),
            onValueChange = {},
            readOnly = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Color(0xFFBBBBBB),
                fontFamily = fontFamily,
                fontSize = fontSize.sp,
                lineHeight = (fontSize + 4).sp
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 14.dp) // Место для scrollbar
                .verticalScroll(scrollState)
        )

        // Кастомный scrollbar
        val maxScroll = scrollState.maxValue
        val currentScroll = scrollState.value

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(10.dp)
                .fillMaxHeight()
                .onSizeChanged { trackHeightPx = it.height.toFloat() }
                .background(Color(0xFF333333), RoundedCornerShape(5.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val currentMax = scrollState.maxValue
                        if (trackHeightPx > 0 && currentMax > 0) {
                            val currentThumbHeight = (trackHeightPx * (trackHeightPx / (trackHeightPx + currentMax))).coerceAtLeast(30f)
                            val availableTrack = trackHeightPx - currentThumbHeight
                            if (availableTrack > 0) {
                                val dragRatio = dragAmount.y / availableTrack
                                val delta = (dragRatio * currentMax).toInt()
                                val newScroll = (scrollState.value + delta).coerceIn(0, currentMax)
                                coroutineScope.launch {
                                    scrollState.scrollTo(newScroll)
                                }
                            }
                        }
                    }
                }
        ) {
            // Thumb (ползунок) - показываем только если есть что скроллить
            if (maxScroll > 0 && trackHeightPx > 0) {
                val thumbHeightRatio = (trackHeightPx / (trackHeightPx + maxScroll)).coerceIn(0.05f, 1f)
                val thumbHeightPx = (trackHeightPx * thumbHeightRatio).coerceAtLeast(30f)
                val scrollRatio = if (maxScroll > 0) currentScroll.toFloat() / maxScroll.toFloat() else 0f
                val thumbOffsetPx = scrollRatio * (trackHeightPx - thumbHeightPx)

                val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
                val thumbOffsetDp = with(density) { thumbOffsetPx.toDp() }

                Box(
                    modifier = Modifier
                        .offset(y = thumbOffsetDp)
                        .width(10.dp)
                        .height(thumbHeightDp)
                        .background(Color(0xFF888888), RoundedCornerShape(5.dp))
                )
            }
        }
    }
}
