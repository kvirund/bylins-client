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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import com.bylins.client.triggers.Trigger

@Composable
fun TriggersPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val triggers by clientState.triggers.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingTrigger by remember { mutableStateOf<Trigger?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(8.dp)
    ) {
        // Заголовок с кнопкой добавления
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Триггеры",
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )

            Button(
                onClick = {
                    editingTrigger = null
                    showDialog = true
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF4CAF50)
                )
            ) {
                Text("+ Добавить", color = Color.White)
            }
        }

        Divider(color = Color.Gray, thickness = 1.dp)

        // Список триггеров
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(triggers) { trigger ->
                TriggerItem(
                    trigger = trigger,
                    onToggle = { id, enabled ->
                        if (enabled) {
                            clientState.enableTrigger(id)
                        } else {
                            clientState.disableTrigger(id)
                        }
                    },
                    onEdit = { trigger ->
                        editingTrigger = trigger
                        showDialog = true
                    },
                    onDelete = { id ->
                        clientState.removeTrigger(id)
                    }
                )
            }
        }

        if (triggers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет триггеров",
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    // Диалог добавления/редактирования
    if (showDialog) {
        TriggerDialog(
            trigger = editingTrigger,
            onDismiss = {
                showDialog = false
                editingTrigger = null
            },
            onSave = { trigger ->
                if (editingTrigger == null) {
                    clientState.addTrigger(trigger)
                } else {
                    clientState.updateTrigger(trigger)
                }
                showDialog = false
                editingTrigger = null
            }
        )
    }
}

@Composable
private fun TriggerItem(
    trigger: Trigger,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (Trigger) -> Unit,
    onDelete: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        backgroundColor = Color(0xFF2D2D2D),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Основная строка
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Название и ID
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trigger.name,
                        color = if (trigger.enabled) Color.White else Color.Gray,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "ID: ${trigger.id}",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Кнопки управления
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка включения/выключения
                    Switch(
                        checked = trigger.enabled,
                        onCheckedChange = { enabled ->
                            onToggle(trigger.id, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4CAF50),
                            uncheckedThumbColor = Color.Gray
                        )
                    )

                    // Кнопка редактирования
                    Button(
                        onClick = { onEdit(trigger) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF2196F3)
                        ),
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "✎",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }

                    // Кнопка информации
                    Button(
                        onClick = { expanded = !expanded },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF424242)
                        ),
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (expanded) "▲" else "▼",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }

                    // Кнопка удаления
                    Button(
                        onClick = { onDelete(trigger.id) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFD32F2F)
                        ),
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "✕",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Развернутая информация
            if (expanded) {
                Divider(
                    color = Color.Gray,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Pattern
                    InfoRow("Pattern:", trigger.pattern.pattern)

                    // Commands
                    if (trigger.commands.isNotEmpty()) {
                        InfoRow("Commands:", trigger.commands.joinToString("; "))
                    }

                    // Priority
                    InfoRow("Priority:", trigger.priority.toString())

                    // Flags
                    val flags = mutableListOf<String>()
                    if (trigger.gag) flags.add("GAG")
                    if (trigger.once) flags.add("ONCE")
                    if (trigger.colorize != null) flags.add("COLOR")
                    if (flags.isNotEmpty()) {
                        InfoRow("Flags:", flags.joinToString(", "))
                    }

                    // Colorize info
                    if (trigger.colorize != null) {
                        val colorInfo = buildString {
                            append("FG: ${trigger.colorize.foreground ?: "default"}")
                            if (trigger.colorize.background != null) {
                                append(", BG: ${trigger.colorize.background}")
                            }
                            if (trigger.colorize.bold) append(", BOLD")
                        }
                        InfoRow("Color:", colorInfo)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF888888),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}
