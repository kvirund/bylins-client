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

    Column(modifier = modifier) {
        // Вкладки
        if (tabs.size > 1) {
            TabRow(
                selectedTabIndex = tabs.indexOfFirst { it.id == activeTabId }.takeIf { it >= 0 } ?: 0,
                containerColor = Color(0xFF1E1E1E),
                contentColor = Color.White
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = tab.id == activeTabId,
                        onClick = { clientState.setActiveTab(tab.id) },
                        text = { Text(tab.name) }
                    )
                }
            }
        }

        // Рендерим все вкладки одновременно, показываем только активную
        Box(modifier = Modifier.fillMaxSize()) {
            tabs.forEach { tab ->
                val isActive = tab.id == activeTabId
                TabContent(
                    tab = tab,
                    receivedData = receivedData,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (isActive) 1f else 0f)
                        .alpha(if (isActive) 1f else 0f)
                )
            }
        }
    }
}

@Composable
fun TabContent(
    tab: com.bylins.client.tabs.Tab,
    receivedData: String, // Для главной вкладки
    modifier: Modifier = Modifier
) {
    // Просто rememberScrollState - компонент не уничтожается, поэтому состояние сохраняется
    val scrollState = rememberScrollState()
    val ansiParser = remember { AnsiParser() }

    // Получаем содержимое вкладки
    val tabContent by tab.content.collectAsState()

    // Определяем текст для отображения
    val displayText = if (tab.id == "main") receivedData else tabContent

    val outputText: AnnotatedString = remember(displayText) {
        if (displayText.isEmpty()) {
            if (tab.id == "main") {
                AnnotatedString("Добро пожаловать в Bylins MUD Client!\nПодключитесь к серверу для начала игры.\n\n")
            } else {
                AnnotatedString("${tab.name}: пусто\n")
            }
        } else {
            ansiParser.parse(displayText)
        }
    }

    // Запоминаем предыдущее значение текста
    val prevText = remember { mutableStateOf(displayText) }

    // Автопрокрутка вниз ТОЛЬКО при добавлении нового текста (не при переключении вкладок)
    LaunchedEffect(displayText) {
        // Прокручиваем только если текст изменился (добавился новый), а не просто переключили вкладку
        if (displayText != prevText.value && displayText.startsWith(prevText.value)) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
        prevText.value = displayText
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
