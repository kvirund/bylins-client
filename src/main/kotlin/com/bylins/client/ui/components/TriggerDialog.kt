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
import androidx.compose.ui.window.Dialog
import com.bylins.client.triggers.Trigger
import com.bylins.client.triggers.TriggerColorize

@Composable
fun TriggerDialog(
    trigger: Trigger? = null, // null для создания нового, не-null для редактирования
    onDismiss: () -> Unit,
    onSave: (Trigger) -> Unit
) {
    val isNewTrigger = trigger == null

    var id by remember { mutableStateOf(trigger?.id ?: "") }
    var name by remember { mutableStateOf(trigger?.name ?: "") }
    var pattern by remember { mutableStateOf(trigger?.pattern?.pattern ?: "") }
    var commands by remember { mutableStateOf(trigger?.commands?.joinToString("\n") ?: "") }
    var enabled by remember { mutableStateOf(trigger?.enabled ?: true) }
    var priority by remember { mutableStateOf(trigger?.priority?.toString() ?: "0") }
    var gag by remember { mutableStateOf(trigger?.gag ?: false) }
    var once by remember { mutableStateOf(trigger?.once ?: false) }

    var useColorize by remember { mutableStateOf(trigger?.colorize != null) }
    var foreground by remember { mutableStateOf(trigger?.colorize?.foreground ?: "#FFFFFF") }
    var background by remember { mutableStateOf(trigger?.colorize?.background ?: "") }
    var bold by remember { mutableStateOf(trigger?.colorize?.bold ?: false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(600.dp)
                .heightIn(max = 700.dp),
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
                    text = if (isNewTrigger) "Новый триггер" else "Редактирование триггера",
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
                        enabled = isNewTrigger,
                        placeholder = "unique-id"
                    )

                    // Name
                    FormField(
                        label = "Название",
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Описательное название"
                    )

                    // Pattern
                    FormField(
                        label = "Pattern (regex)",
                        value = pattern,
                        onValueChange = { pattern = it },
                        placeholder = "^(.+) говорит вам:"
                    )

                    // Commands
                    Text(
                        text = "Команды (каждая с новой строки)",
                        color = Color(0xFFBBBBBB),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
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
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        placeholder = {
                            Text(
                                text = "say Привет, \$1!\ntell \$1 Как дела?",
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    )

                    // Priority
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FormField(
                            label = "Priority",
                            value = priority,
                            onValueChange = { priority = it.filter { c -> c.isDigit() || c == '-' } },
                            placeholder = "0",
                            modifier = Modifier.weight(1f)
                        )

                        // Checkbox Enabled
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = enabled,
                                onCheckedChange = { enabled = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF4CAF50)
                                )
                            )
                            Text(
                                text = "Enabled",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Flags
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Checkbox Gag
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = gag,
                                onCheckedChange = { gag = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF4CAF50)
                                )
                            )
                            Text(
                                text = "Gag",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }

                        // Checkbox Once
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = once,
                                onCheckedChange = { once = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF4CAF50)
                                )
                            )
                            Text(
                                text = "Once",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }

                        // Checkbox Colorize
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = useColorize,
                                onCheckedChange = { useColorize = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF4CAF50)
                                )
                            )
                            Text(
                                text = "Colorize",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Colorize настройки
                    if (useColorize) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = Color(0xFF1E1E1E),
                            elevation = 2.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FormField(
                                    label = "Foreground (#RRGGBB)",
                                    value = foreground,
                                    onValueChange = { foreground = it },
                                    placeholder = "#00FF00"
                                )
                                FormField(
                                    label = "Background (#RRGGBB, опционально)",
                                    value = background,
                                    onValueChange = { background = it },
                                    placeholder = "#000000"
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = bold,
                                        onCheckedChange = { bold = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color(0xFF4CAF50)
                                        )
                                    )
                                    Text(
                                        text = "Bold",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Сообщение об ошибке
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFFF5555),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // Кнопки
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF424242)
                        )
                    ) {
                        Text("Отмена", color = Color.White)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            // Валидация
                            when {
                                id.isBlank() -> errorMessage = "ID не может быть пустым"
                                name.isBlank() -> errorMessage = "Название не может быть пустым"
                                pattern.isBlank() -> errorMessage = "Pattern не может быть пустым"
                                else -> {
                                    try {
                                        // Пробуем создать regex
                                        val regex = pattern.toRegex()

                                        // Парсим priority
                                        val priorityInt = priority.toIntOrNull() ?: 0

                                        // Парсим команды
                                        val commandsList = commands.split("\n")
                                            .map { it.trim() }
                                            .filter { it.isNotEmpty() }

                                        // Создаём colorize если нужно
                                        val colorize = if (useColorize) {
                                            TriggerColorize(
                                                foreground = foreground.ifBlank { null },
                                                background = background.ifBlank { null },
                                                bold = bold
                                            )
                                        } else null

                                        // Создаём триггер
                                        val newTrigger = Trigger(
                                            id = id,
                                            name = name,
                                            pattern = regex,
                                            commands = commandsList,
                                            enabled = enabled,
                                            priority = priorityInt,
                                            gag = gag,
                                            once = once,
                                            colorize = colorize
                                        )

                                        onSave(newTrigger)
                                    } catch (e: Exception) {
                                        errorMessage = "Ошибка: ${e.message}"
                                    }
                                }
                            }
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
    placeholder: String = "",
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = Color(0xFFBBBBBB),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = Color.White,
                disabledTextColor = Color.Gray,
                backgroundColor = Color(0xFF1E1E1E),
                cursorColor = Color.White,
                focusedBorderColor = Color(0xFF4CAF50),
                unfocusedBorderColor = Color.Gray,
                disabledBorderColor = Color.DarkGray
            ),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            placeholder = {
                Text(
                    text = placeholder,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            },
            singleLine = true
        )
    }
}
