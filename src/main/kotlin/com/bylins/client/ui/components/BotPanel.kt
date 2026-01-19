package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import com.bylins.client.bot.BotMode
import com.bylins.client.bot.BotStateType
import com.bylins.client.ui.theme.LocalAppColorScheme

/**
 * Панель управления AI-ботом
 */
@Composable
fun BotPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val colorScheme = LocalAppColorScheme.current
    val botCore = clientState.botManager.botCore
    val botState by botCore.stateMachine.currentState.collectAsState()
    val botConfig by botCore.config.collectAsState()
    val characterState by botCore.characterState.collectAsState()

    var selectedMode by remember { mutableStateOf(BotMode.LEVELING) }
    var modeExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(8.dp)
    ) {
        // Заголовок
        Text(
            text = "AI-Бот",
            color = colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Статус бота
        Card(
            backgroundColor = colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Статус:",
                        color = colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = botState.name,
                        color = when (botState) {
                            BotStateType.IDLE -> colorScheme.onSurface.copy(alpha = 0.6f)
                            BotStateType.STARTING -> colorScheme.primary
                            BotStateType.TRAVELING -> colorScheme.primary
                            BotStateType.COMBAT -> colorScheme.error
                            BotStateType.LOOTING -> colorScheme.success
                            BotStateType.RESTING -> Color(0xFF90CAF9)
                            BotStateType.BUFFING -> Color(0xFF81C784)
                            BotStateType.FLEEING -> Color(0xFFFFB74D)
                            BotStateType.EXPLORING -> colorScheme.primary
                            BotStateType.RETURNING -> Color(0xFF64B5F6)
                            BotStateType.ERROR -> colorScheme.error
                            BotStateType.STOPPING -> colorScheme.onSurface.copy(alpha = 0.5f)
                        },
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (botConfig.enabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Режим:",
                            color = colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = botConfig.mode.name,
                            color = colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Выбор режима
        Text(
            text = "Режим работы:",
            color = colorScheme.onSurface,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Button(
                onClick = { modeExpanded = true },
                colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedMode.name,
                    color = colorScheme.onSurface
                )
            }

            DropdownMenu(
                expanded = modeExpanded,
                onDismissRequest = { modeExpanded = false }
            ) {
                BotMode.values().forEach { mode ->
                    DropdownMenuItem(onClick = {
                        selectedMode = mode
                        modeExpanded = false
                    }) {
                        Column {
                            Text(mode.name, color = colorScheme.onSurface)
                            Text(
                                getModeDescription(mode),
                                color = colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // Кнопки управления
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (botConfig.enabled) {
                        clientState.botManager.botCore.stop()
                    } else {
                        clientState.botManager.botCore.start(selectedMode)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (botConfig.enabled) colorScheme.error else colorScheme.success
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (botConfig.enabled) "Остановить" else "Запустить",
                    color = Color.White
                )
            }

        }

        Spacer(modifier = Modifier.height(16.dp))

        // Состояние персонажа
        characterState?.let { state ->
            Card(
                backgroundColor = colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Персонаж",
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    StatRow("HP", state.hp, state.maxHp, colorScheme.error)
                    StatRow("Мана", state.mana, state.maxMana, colorScheme.primary)
                    StatRow("Движ.", state.move, state.maxMove, colorScheme.success)

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Уровень: ${state.level}", color = colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 11.sp)
                        Text("Позиция: ${state.position}", color = colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Статистика сессии
        Card(
            backgroundColor = colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Статистика сессии",
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val status = clientState.botManager.getBotStatus()
                val session = status["session"] as? Map<*, *> ?: emptyMap<String, Any>()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Мобов убито:", color = colorScheme.onSurface, fontSize = 12.sp)
                    Text("${session["kills"] ?: 0}", color = colorScheme.success, fontSize = 12.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Смертей:", color = colorScheme.onSurface, fontSize = 12.sp)
                    Text("${session["deaths"] ?: 0}", color = colorScheme.error, fontSize = 12.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Опыт:", color = colorScheme.onSurface, fontSize = 12.sp)
                    Text("${session["exp"] ?: 0}", color = colorScheme.primary, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // LLM статус
        Card(
            backgroundColor = colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "LLM Парсер",
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val llmStatus = clientState.botManager.getBotStatus()["llm"] as? Map<*, *> ?: emptyMap<String, Any>()
                val llmAvailable = llmStatus["available"] as? Boolean ?: false

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (llmAvailable) "Подключен" else "Не подключен",
                        color = if (llmAvailable) colorScheme.success else colorScheme.error,
                        fontSize = 12.sp
                    )

                    Button(
                        onClick = {
                            clientState.botManager.initializeLLM()
                        },
                        enabled = !llmAvailable,
                        colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.primary)
                    ) {
                        Text("Подключить", color = Color.White, fontSize = 11.sp)
                    }
                }

                if (llmAvailable) {
                    Text(
                        text = "Модель: ${llmStatus["model"] ?: "unknown"}",
                        color = colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, current: Int, max: Int, color: Color) {
    val colorScheme = LocalAppColorScheme.current
    val percent = if (max > 0) (current.toFloat() / max * 100).toInt() else 0

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            color = colorScheme.onSurface,
            fontSize = 11.sp,
            modifier = Modifier.width(50.dp)
        )

        LinearProgressIndicator(
            progress = if (max > 0) current.toFloat() / max else 0f,
            color = color,
            backgroundColor = color.copy(alpha = 0.2f),
            modifier = Modifier.weight(1f).height(8.dp).padding(horizontal = 8.dp)
        )

        Text(
            text = "$current/$max ($percent%)",
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 10.sp
        )
    }
}

private fun getModeDescription(mode: BotMode): String {
    return when (mode) {
        BotMode.LEVELING -> "Фарм опыта на мобах"
        BotMode.FARMING -> "Фарм предметов"
        BotMode.GATHERING -> "Сбор ресурсов"
        BotMode.TRADING -> "Торговля"
        BotMode.EXPLORING -> "Исследование новых зон"
        BotMode.IDLE -> "Ожидание"
    }
}
