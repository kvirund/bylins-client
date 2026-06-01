package com.bylins.client.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState

private val outputPanelLogger = mu.KotlinLogging.logger("OutputPanel")

@Composable
fun OutputPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val tabs by clientState.tabs.collectAsState()
    val activeTabId by clientState.activeTabId.collectAsState()

    // Логируем вкладки при изменении
    LaunchedEffect(tabs) {
        outputPanelLogger.info { "Tabs updated: ${tabs.map { "${it.id}(${it.name})" }}" }
    }

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
                                // Кнопки управления (только для пользовательских вкладок, не для системных)
                                if (tab.id != "main" && tab.id != "logs" && !tab.isPluginTab) {
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
            // Снимок буфера: для главной вкладки — из TelnetClient, для прочих — из Tab
            val mainSnapshot by clientState.mainOutputSnapshot.collectAsState()
            val tabSnapshot by activeTab.snapshot.collectAsState()
            val snapshot = if (activeTab.id == "main") mainSnapshot else tabSnapshot

            val holder = remember(activeTab.id) { clientState.outputViewHolder(activeTab.id) }

            val placeholder = remember(activeTab.id, activeTab.name) {
                if (activeTab.id == "main") {
                    AnnotatedString("Добро пожаловать в Bylins MUD Client!\nПодключитесь к серверу для начала игры.\n\n")
                } else {
                    AnnotatedString("${activeTab.name}: пусто\n")
                }
            }

            // Доля разделителя — своя на каждую вкладку (хранится в holder)
            val splitFraction = holder.splitFraction

            // key по id вкладки: при переключении внутренних вкладок создаётся свежее
            // поддерево (иначе переиспользование по тому же месту показывало старую вкладку)
            key(activeTab.id) {
                com.bylins.client.ui.components.output.ScrollbackOutputView(
                    snapshot = snapshot,
                    holder = holder,
                    splitFraction = splitFraction,
                    onSplitFractionChange = { holder.splitFraction = it },
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    emptyPlaceholder = placeholder,
                    modifier = Modifier.fillMaxSize()
                )
            }
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
