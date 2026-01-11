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
import com.bylins.client.hotkeys.Hotkey

@Composable
fun HotkeysPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val hotkeys by clientState.hotkeys.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingHotkey by remember { mutableStateOf<Hotkey?>(null) }

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
                text = "Горячие клавиши",
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )

            Button(
                onClick = {
                    showAddDialog = true
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF4CAF50)
                )
            ) {
                Text("+ Добавить", color = Color.White)
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

        Divider(color = Color.Gray, thickness = 1.dp)

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
            modifier = Modifier.fillMaxWidth(),
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
                    color = Color.Gray,
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
                // Название и комбинация клавиш
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hotkey.name,
                        color = if (hotkey.enabled) Color.White else Color.Gray,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = hotkey.getKeyCombo(),
                        color = Color(0xFF00FF00),
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
                            checkedThumbColor = Color(0xFF4CAF50),
                            uncheckedThumbColor = Color.Gray
                        )
                    )

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

                    // Кнопка редактирования
                    Button(
                        onClick = { onEdit(hotkey) },
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

                    // Кнопка удаления
                    Button(
                        onClick = { onDelete(hotkey.id) },
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF888888),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(120.dp)
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
