package com.bylins.client.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.bylins.client.ClientState
import com.bylins.client.ui.AnsiParser

@Composable
fun OutputPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val tabs by clientState.tabs.collectAsState()
    val activeTabId by clientState.activeTabId.collectAsState()
    val receivedData by clientState.receivedData.collectAsState()

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
                    Tab(
                        selected = tab.id == activeTabId,
                        onClick = { clientState.setActiveTab(tab.id) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(tab.name)
                                // Кнопки управления (кроме главной вкладки)
                                if (tab.id != "main") {
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
        if (activeTab != null) {
            TabContent(
                tab = activeTab,
                receivedData = receivedData,
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
            onSave = { name, patterns, captureMode ->
                if (editingTab != null) {
                    clientState.updateTab(editingTab!!.id, name, patterns, captureMode)
                } else {
                    clientState.createTab(name, patterns, captureMode)
                }
                showTabDialog = false
                editingTab = null
            }
        )
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
    receivedData: String, // Для главной вкладки
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val ansiParser = remember(tab.id) { AnsiParser() }

    // Получаем содержимое вкладки
    val tabContent by tab.content.collectAsState()

    // Определяем текст для отображения
    val displayText = if (tab.id == "main") receivedData else tabContent

    // КРИТИЧНО: Ограничиваем отображаемый текст последними 2000 строками
    // для избежания O(n²) сложности при парсинге ANSI кодов
    val limitedText = remember(displayText) {
        if (displayText.length > 100_000) { // Только если текст большой
            getLastLines(displayText, 2000)
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

    Box(
        modifier = modifier
            .background(Color.Black)
            .padding(8.dp)
    ) {
        SelectionContainer {
            Text(
                text = outputText,
                color = Color(0xFFBBBBBB),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            )
        }
    }
}
