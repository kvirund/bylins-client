package com.bylins.client.ui.components

import mu.KotlinLogging
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
import com.bylins.client.ui.theme.LocalAppColorScheme
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val logger = KotlinLogging.logger("TriggersPanel")
@Composable
fun TriggersPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val triggers by clientState.triggers.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingTrigger by remember { mutableStateOf<Trigger?>(null) }
    val colorScheme = LocalAppColorScheme.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(8.dp)
    ) {
        // Заголовок с кнопками
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Триггеры",
                color = colorScheme.onBackground,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        // Экспорт триггеров
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")
                        fileChooser.selectedFile = File("triggers.json")

                        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val triggerIds = triggers.map { it.id }
                                val json = clientState.exportTriggers(triggerIds)
                                fileChooser.selectedFile.writeText(json)
                                logger.info { "Triggers exported to ${fileChooser.selectedFile.absolutePath}" }
                            } catch (e: Exception) {
                                logger.error { "Export error: ${e.message}" }
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorScheme.primary
                    )
                ) {
                    Text("Экспорт", color = colorScheme.onBackground)
                }

                Button(
                    onClick = {
                        // Импорт триггеров
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")

                        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val json = fileChooser.selectedFile.readText()
                                val count = clientState.importTriggers(json, merge = true)
                                logger.info { "Imported $count triggers from ${fileChooser.selectedFile.absolutePath}" }
                            } catch (e: Exception) {
                                logger.error { "Import error: ${e.message}" }
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorScheme.warning
                    )
                ) {
                    Text("Импорт", color = colorScheme.onBackground)
                }

                Button(
                    onClick = {
                        editingTrigger = null
                        showDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorScheme.success
                    )
                ) {
                    Text("+ Добавить", color = colorScheme.onBackground)
                }
            }
        }

        Divider(color = colorScheme.divider, thickness = 1.dp)

        // Список триггеров
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
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
                    color = colorScheme.divider,
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
    val colorScheme = LocalAppColorScheme.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        backgroundColor = colorScheme.surface,
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
                        color = if (trigger.enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "ID: ${trigger.id}",
                        color = colorScheme.onSurfaceVariant,
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
                            checkedThumbColor = colorScheme.success,
                            uncheckedThumbColor = colorScheme.onSurfaceVariant
                        )
                    )

                    // Кнопка редактирования
                    Button(
                        onClick = { onEdit(trigger) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.primary
                        ),
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "✎",
                            color = colorScheme.onBackground,
                            fontSize = 14.sp
                        )
                    }

                    // Кнопка информации
                    Button(
                        onClick = { expanded = !expanded },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (expanded) "▲" else "▼",
                            color = colorScheme.onBackground,
                            fontSize = 10.sp
                        )
                    }

                    // Кнопка удаления
                    Button(
                        onClick = { onDelete(trigger.id) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.error
                        ),
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "✕",
                            color = colorScheme.onBackground,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Развернутая информация
            if (expanded) {
                Divider(
                    color = colorScheme.divider,
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
    val colorScheme = LocalAppColorScheme.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            color = colorScheme.onBackground,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}
