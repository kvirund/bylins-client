package com.bylins.client.ui.components

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
import com.bylins.client.aliases.Alias

@Composable
fun AliasDialog(
    alias: Alias? = null, // null для создания нового, не-null для редактирования
    onDismiss: () -> Unit,
    onSave: (Alias) -> Unit
) {
    val isNewAlias = alias == null

    var id by remember { mutableStateOf(alias?.id ?: "") }
    var name by remember { mutableStateOf(alias?.name ?: "") }
    var pattern by remember { mutableStateOf(alias?.pattern?.pattern ?: "") }
    var commands by remember { mutableStateOf(alias?.commands?.joinToString("\n") ?: "") }
    var enabled by remember { mutableStateOf(alias?.enabled ?: true) }
    var priority by remember { mutableStateOf(alias?.priority?.toString() ?: "0") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                    text = if (isNewAlias) "Новый алиас" else "Редактирование алиаса",
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
                    // ID - только для редактирования (показываем readonly)
                    if (!isNewAlias) {
                        Text(
                            text = "ID: $id",
                            color = Color(0xFF888888),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Name (опционально)
                    FormField(
                        label = "Название (опционально)",
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "По умолчанию: паттерн"
                    )

                    // Pattern
                    FormField(
                        label = "Pattern (regex)",
                        value = pattern,
                        onValueChange = { pattern = it },
                        placeholder = "^t (\\w+) (.+)$"
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
                                text = "tell \$1 \$2",
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    )

                    // Priority и Enabled
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

                                        // ID: для нового - генерируем UUID, для существующего - сохраняем
                                        val aliasId = if (isNewAlias) {
                                            "al-${java.util.UUID.randomUUID().toString().take(8)}"
                                        } else {
                                            id
                                        }

                                        // Name: если пустое - используем pattern
                                        val aliasName = name.ifBlank { pattern }

                                        // Создаём алиас
                                        val newAlias = Alias(
                                            id = aliasId,
                                            name = aliasName,
                                            pattern = regex,
                                            commands = commandsList,
                                            enabled = enabled,
                                            priority = priorityInt
                                        )

                                        onSave(newAlias)
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
