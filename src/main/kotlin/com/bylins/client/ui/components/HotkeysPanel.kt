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
import com.bylins.client.hotkeys.Hotkey
import com.bylins.client.ui.theme.LocalAppColorScheme
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val logger = KotlinLogging.logger("HotkeysPanel")
@Composable
fun HotkeysPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val hotkeys by clientState.hotkeys.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingHotkey by remember { mutableStateOf<Hotkey?>(null) }
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
                text = "Горячие клавиши",
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
                        // Экспорт хоткеев
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")
                        fileChooser.selectedFile = File("hotkeys.json")

                        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val hotkeyIds = hotkeys.map { it.id }
                                val json = clientState.exportHotkeys(hotkeyIds)
                                fileChooser.selectedFile.writeText(json)
                                logger.info { "Hotkeys exported to ${fileChooser.selectedFile.absolutePath}" }
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
                    Text("Экспорт", color = Color.White)
                }

                Button(
                    onClick = {
                        // Импорт хоткеев
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")

                        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val json = fileChooser.selectedFile.readText()
                                val count = clientState.importHotkeys(json, merge = true)
                                logger.info { "Imported $count hotkeys from ${fileChooser.selectedFile.absolutePath}" }
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
                    Text("Импорт", color = Color.White)
                }

                Button(
                    onClick = {
                        showAddDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorScheme.success
                    )
                ) {
                    Text("+ Добавить", color = Color.White)
                }
            }
        }

        // Диалог добавления хоткея
        if (showAddDialog) {
            HotkeyDialog(
                onDismiss = { showAddDialog = false },
                onSave = { hotkey ->
                    clientState.addHotkey(hotkey)
                    showAddDialog = false
                }
            )
        }

        // Диалог редактирования хоткея
        if (editingHotkey != null) {
            HotkeyDialog(
                hotkey = editingHotkey,
                onDismiss = { editingHotkey = null },
                onSave = { hotkey ->
                    clientState.updateHotkey(hotkey)
                    editingHotkey = null
                }
            )
        }

        Divider(color = colorScheme.divider, thickness = 1.dp)

        // Список хоткеев (отсортированный)
        val sortedHotkeys = remember(hotkeys) {
            hotkeys.sortedWith(
                compareBy<Hotkey> { !it.ctrl && !it.alt && !it.shift } // Сначала без модификаторов
                    .thenBy { it.ctrl } // Затем с Ctrl
                    .thenBy { it.alt } // Затем с Alt
                    .thenBy { it.shift } // Затем с Shift
                    .thenBy { it.getKeyCombo() } // И наконец по комбинации клавиш
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(sortedHotkeys) { hotkey ->
                HotkeyItem(
                    hotkey = hotkey,
                    onToggle = { id, enabled ->
                        if (enabled) {
                            clientState.enableHotkey(id)
                        } else {
                            clientState.disableHotkey(id)
                        }
                    },
                    onEdit = { editHotkey ->
                        editingHotkey = editHotkey
                    },
                    onDelete = { id ->
                        clientState.removeHotkey(id)
                    }
                )
            }
        }

        if (sortedHotkeys.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет горячих клавиш",
                    color = colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun HotkeyItem(
    hotkey: Hotkey,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (Hotkey) -> Unit,
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
                // Название и комбинация клавиш
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hotkey.name,
                        color = if (hotkey.enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = hotkey.getKeyCombo(),
                        color = colorScheme.success,
                        fontSize = 12.sp,
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
                        checked = hotkey.enabled,
                        onCheckedChange = { enabled ->
                            onToggle(hotkey.id, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colorScheme.success,
                            uncheckedThumbColor = colorScheme.onSurfaceVariant
                        )
                    )

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
                            color = colorScheme.onSurface,
                            fontSize = 10.sp
                        )
                    }

                    // Кнопка редактирования
                    Button(
                        onClick = { onEdit(hotkey) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.primary
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

                    // Кнопка удаления
                    Button(
                        onClick = { onDelete(hotkey.id) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.error
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
                    color = colorScheme.divider,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // ID
                    InfoRow("ID:", hotkey.id)

                    // Key combo
                    InfoRow("Комбинация:", hotkey.getKeyCombo())

                    // Commands
                    if (hotkey.commands.isNotEmpty()) {
                        InfoRow("Команды:", hotkey.commands.joinToString("; "))
                    }

                    // Modifiers
                    val modifiers = mutableListOf<String>()
                    if (hotkey.ctrl) modifiers.add("Ctrl")
                    if (hotkey.alt) modifiers.add("Alt")
                    if (hotkey.shift) modifiers.add("Shift")
                    if (modifiers.isNotEmpty()) {
                        InfoRow("Модификаторы:", modifiers.joinToString(", "))
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
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            color = colorScheme.onSurface,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}
