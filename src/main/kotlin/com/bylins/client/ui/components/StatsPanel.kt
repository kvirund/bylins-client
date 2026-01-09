package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import kotlinx.coroutines.delay

@Composable
fun StatsPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val stats by clientState.stats.collectAsState()
    val isConnected by clientState.isConnected.collectAsState()

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
            .background(Color(0xFF1E1E1E))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Заголовок
        Text(
            text = "Статистика сессии",
            color = Color.White,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Divider(color = Color.Gray, thickness = 1.dp)

        if (!isConnected || stats.sessionStart == null) {
            // Нет активной сессии
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF2D2D2D),
                elevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Нет активной сессии",
                        color = Color(0xFF888888),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Подключитесь к серверу для начала сбора статистики",
                        color = Color(0xFF666666),
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
                backgroundColor = Color(0xFF2D2D2D),
                elevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Информация о сессии",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Divider(color = Color.Gray, thickness = 1.dp)

                    StatRow("Начало сессии:", stats.getStartTimeFormatted())
                    StatRow("Длительность:", duration, Color(0xFF00FF00))
                }
            }

            // Карточка с сетевой статистикой
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF2D2D2D),
                elevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Сетевая активность",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Divider(color = Color.Gray, thickness = 1.dp)

                    StatRow("Отправлено команд:", stats.commandsSent.toString(), Color(0xFF2196F3))
                    StatRow("Получено данных:", clientState.getFormattedBytes(), Color(0xFFFF9800))
                }
            }

            // Карточка с автоматизацией
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF2D2D2D),
                elevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Автоматизация",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Divider(color = Color.Gray, thickness = 1.dp)

                    StatRow("Сработало триггеров:", stats.triggersActivated.toString(), Color(0xFFFF5555))
                    StatRow("Выполнено алиасов:", stats.aliasesExecuted.toString(), Color(0xFFFFEB3B))
                    StatRow("Использовано хоткеев:", stats.hotkeysUsed.toString(), Color(0xFF9C27B0))
                }
            }

            // Карточка с дополнительной информацией
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF2D2D2D),
                elevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Средние показатели",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Divider(color = Color.Gray, thickness = 1.dp)

                    // Команд в минуту
                    val durationSeconds = clientState.getSessionDuration()
                        .split(":")
                        .let { parts ->
                            parts[0].toIntOrNull()?.times(3600) ?: 0 +
                                    parts[1].toIntOrNull()?.times(60) ?: 0 +
                                    parts[2].toIntOrNull() ?: 0
                        }
                    val commandsPerMinute = if (durationSeconds > 0) {
                        (stats.commandsSent.toDouble() / durationSeconds * 60).toInt()
                    } else {
                        0
                    }

                    StatRow("Команд в минуту:", commandsPerMinute.toString(), Color(0xFF00BCD4))

                    // Байт в секунду
                    val bytesPerSecond = if (durationSeconds > 0) {
                        (stats.bytesReceived.toDouble() / durationSeconds).toInt()
                    } else {
                        0
                    }
                    StatRow("Байт/сек:", bytesPerSecond.toString(), Color(0xFF00BCD4))
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFFBBBBBB),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
