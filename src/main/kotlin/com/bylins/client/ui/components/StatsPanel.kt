package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import com.bylins.client.ui.theme.LocalAppColorScheme
import kotlinx.coroutines.delay

@Composable
fun StatsPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val stats by clientState.stats.collectAsState()
    val isConnected by clientState.isConnected.collectAsState()
    val scrollState = rememberScrollState()
    val colorScheme = LocalAppColorScheme.current

    // Обновляем длительность каждую секунду
    var duration by remember { mutableStateOf("00:00:00") }

    LaunchedEffect(isConnected) {
        while (isConnected) {
            duration = clientState.getSessionDuration()
            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Заголовок
        Text(
            text = "Статистика сессии",
            color = colorScheme.onSurface,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Divider(color = colorScheme.divider, thickness = 1.dp)

        if (!isConnected || stats.sessionStart == null) {
            // Нет активной сессии
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colorScheme.surface,
                elevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Нет активной сессии",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Подключитесь к серверу для начала сбора статистики",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            // Карточка с общей информацией
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colorScheme.surface,
                elevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Информация о сессии",
                        color = colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Divider(color = colorScheme.divider, thickness = 1.dp)

                    StatRow("Начало сессии:", stats.getStartTimeFormatted())
                    StatRow("Длительность:", duration, colorScheme.success)
                }
            }

            // Карточка с сетевой статистикой
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colorScheme.surface,
                elevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Сетевая активность",
                        color = colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Divider(color = colorScheme.divider, thickness = 1.dp)

                    StatRow("Отправлено команд:", stats.commandsSent.toString(), colorScheme.primary)
                    StatRow("Получено данных:", clientState.getFormattedBytes(), colorScheme.warning)
                }
            }

            // Карточка с автоматизацией
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colorScheme.surface,
                elevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Автоматизация",
                        color = colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Divider(color = colorScheme.divider, thickness = 1.dp)

                    StatRow("Сработало триггеров:", stats.triggersActivated.toString(), colorScheme.error)
                    StatRow("Выполнено алиасов:", stats.aliasesExecuted.toString(), colorScheme.warning)
                    StatRow("Использовано хоткеев:", stats.hotkeysUsed.toString(), colorScheme.secondary)
                }
            }

            // Карточка с дополнительной информацией
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colorScheme.surface,
                elevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Средние показатели",
                        color = colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Divider(color = colorScheme.divider, thickness = 1.dp)

                    // Команд в минуту
                    val durationSeconds = clientState.getSessionDuration()
                        .split(":")
                        .let { parts ->
                            (parts[0].toIntOrNull()?.times(3600) ?: 0) +
                                    (parts[1].toIntOrNull()?.times(60) ?: 0) +
                                    (parts[2].toIntOrNull() ?: 0)
                        }
                    val commandsPerMinute = if (durationSeconds > 0) {
                        (stats.commandsSent.toDouble() / durationSeconds * 60).toInt()
                    } else {
                        0
                    }

                    StatRow("Команд в минуту:", commandsPerMinute.toString(), colorScheme.secondary)

                    // Байт в секунду
                    val bytesPerSecond = if (durationSeconds > 0) {
                        (stats.bytesReceived.toDouble() / durationSeconds).toInt()
                    } else {
                        0
                    }
                    StatRow("Байт/сек:", bytesPerSecond.toString(), colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color? = null) {
    val colorScheme = LocalAppColorScheme.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = valueColor ?: colorScheme.onSurface,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
