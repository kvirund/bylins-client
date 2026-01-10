package com.bylins.client.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bylins.client.hotkeys.Hotkey

@Composable
fun HotkeyDialog(
    hotkey: Hotkey? = null, // null для создания нового, не-null для редактирования
    onDismiss: () -> Unit,
    onSave: (Hotkey) -> Unit
) {
    val isNewHotkey = hotkey == null

    var id by remember { mutableStateOf(hotkey?.id ?: "") }
    var name by remember { mutableStateOf(hotkey?.name ?: "") }
    var selectedKey by remember { mutableStateOf(hotkey?.key ?: Key.F1) }
    var ctrl by remember { mutableStateOf(hotkey?.ctrl ?: false) }
    var alt by remember { mutableStateOf(hotkey?.alt ?: false) }
    var shift by remember { mutableStateOf(hotkey?.shift ?: false) }
    var commands by remember { mutableStateOf(hotkey?.commands?.joinToString("\n") ?: "") }
    var enabled by remember { mutableStateOf(hotkey?.enabled ?: true) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var keyDropdownExpanded by remember { mutableStateOf(false) }

    // Список доступных клавиш
    val availableKeys = remember {
        listOf(
            Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6,
            Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12,
            Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
            Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(600.dp)
                .heightIn(max = 600.dp),
            backgroundColor = Color(0xFF2D2D2D),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                // Заголовок
                Text(
                    text = if (isNewHotkey) "Новая горячая клавиша" else "Редактирование горячей клавиши",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Форма
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ID
                    FormField(
                        label = "ID",
                        value = id,
                        onValueChange = { id = it },
                        enabled = isNewHotkey,
                        placeholder = "unique-id"
                    )

                    // Name
                    FormField(
                        label = "Название",
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Описательное название"
                    )

                    // Key selection
                    Text(
                        text = "Клавиша",
                        color = Color(0xFFBBBBBB),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Box {
                        OutlinedButton(
                            onClick = { keyDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = Color(0xFF1E1E1E),
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = Hotkey.getKeyName(selectedKey),
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        MaterialTheme(
                            colors = darkColors(
                                surface = Color(0xFF2D2D2D),
                                onSurface = Color.White,
                                background = Color(0xFF2D2D2D)
                            )
                        ) {
                            DropdownMenu(
                                expanded = keyDropdownExpanded,
                                onDismissRequest = { keyDropdownExpanded = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .heightIn(max = 300.dp)
                            ) {
                                availableKeys.forEach { key ->
                                    DropdownMenuItem(
                                        onClick = {
                                            selectedKey = key
                                            keyDropdownExpanded = false
                                        }
                                    ) {
                                        Text(
                                            text = Hotkey.getKeyName(key),
                                            fontFamily = FontFamily.Monospace,
                                            color = if (key == selectedKey) Color(0xFF00FF00) else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Modifiers
                    Text(
                        text = "Модификаторы",
                        color = Color(0xFFBBBBBB),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Checkbox(
                                checked = ctrl,
                                onCheckedChange = { ctrl = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF4CAF50),
                                    uncheckedColor = Color.Gray
                                )
                            )
                            Text(
                                text = "Ctrl",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Checkbox(
                                checked = alt,
                                onCheckedChange = { alt = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF4CAF50),
                                    uncheckedColor = Color.Gray
                                )
                            )
                            Text(
                                text = "Alt",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Checkbox(
                                checked = shift,
                                onCheckedChange = { shift = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF4CAF50),
                                    uncheckedColor = Color.Gray
                                )
                            )
                            Text(
                                text = "Shift",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Preview комбинации
                    val comboPreview = buildString {
                        if (ctrl) append("Ctrl+")
                        if (alt) append("Alt+")
                        if (shift) append("Shift+")
                        append(Hotkey.getKeyName(selectedKey))
                    }
                    Text(
                        text = "Комбинация: $comboPreview",
                        color = Color(0xFF00FF00),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    // Commands
                    Text(
                        text = "Команды (каждая с новой строки)",
                        color = Color(0xFFBBBBBB),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = commands,
                        onValueChange = { commands = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = Color.White,
                            backgroundColor = Color(0xFF1E1E1E),
                            cursorColor = Color.White,
                            focusedBorderColor = Color(0xFF4CAF50),
                            unfocusedBorderColor = Color.Gray
                        ),
                        placeholder = {
                            Text(
                                "score\ninfo",
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    )

                    // Enabled
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Checkbox(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF4CAF50),
                                uncheckedColor = Color.Gray
                            )
                        )
                        Text(
                            text = "Включен",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Error message (вне формы)
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFFF5555),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Кнопки
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF424242)
                        )
                    ) {
                        Text("Отмена", color = Color.White)
                    }

                    Button(
                        onClick = {
                            // Валидация
                            if (id.isBlank()) {
                                errorMessage = "ID не может быть пустым"
                                return@Button
                            }
                            if (name.isBlank()) {
                                errorMessage = "Название не может быть пустым"
                                return@Button
                            }

                            val commandList = commands.split("\n")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }

                            if (commandList.isEmpty()) {
                                errorMessage = "Должна быть хотя бы одна команда"
                                return@Button
                            }

                            // Создаём хоткей
                            val newHotkey = Hotkey(
                                id = id.trim(),
                                name = name.trim(),
                                key = selectedKey,
                                ctrl = ctrl,
                                alt = alt,
                                shift = shift,
                                commands = commandList,
                                enabled = enabled
                            )

                            onSave(newHotkey)
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("Сохранить", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    placeholder: String = ""
) {
    Column {
        Text(
            text = label,
            color = Color(0xFFBBBBBB),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = Color.White,
                disabledTextColor = Color.Gray,
                backgroundColor = Color(0xFF1E1E1E),
                cursorColor = Color.White,
                focusedBorderColor = Color(0xFF4CAF50),
                unfocusedBorderColor = Color.Gray,
                disabledBorderColor = Color.DarkGray
            ),
            placeholder = {
                Text(placeholder, color = Color.Gray, fontFamily = FontFamily.Monospace)
            },
            singleLine = true
        )
    }
}
