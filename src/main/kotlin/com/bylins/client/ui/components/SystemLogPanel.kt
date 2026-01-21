package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.logging.LogEntry
import com.bylins.client.logging.UiLogBuffer
import com.bylins.client.ui.theme.LocalAppColorScheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Панель системных логов - простой текстовый вывод как OutputPanel
 */
@Composable
fun SystemLogPanel(
    modifier: Modifier = Modifier
) {
    val colorScheme = LocalAppColorScheme.current
    val logEntries by UiLogBuffer.entries.collectAsState()
    val listState = rememberLazyListState()

    // Авто-прокрутка к последнему сообщению
    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.lastIndex)
        }
    }

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(logEntries, key = { "${it.timestamp.toEpochMilli()}_${it.message.hashCode()}" }) { entry ->
            val levelColor = when (entry.level) {
                "ERROR" -> Color(0xFFFF4444)
                "WARN" -> Color(0xFFFFAA00)
                "INFO" -> Color(0xFF88FF88)
                "DEBUG" -> Color(0xFF88AAFF)
                "TRACE" -> Color(0xFF888888)
                else -> colorScheme.onSurface
            }

            Text(
                text = "[${timeFormatter.format(entry.timestamp)}] [${entry.level}] [${entry.logger}] ${entry.message}",
                color = levelColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth()
            )

            if (entry.throwable != null) {
                Text(
                    text = "  ${entry.throwable}",
                    color = Color(0xFFFF6666),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp)
                )
            }
        }
    }
}
