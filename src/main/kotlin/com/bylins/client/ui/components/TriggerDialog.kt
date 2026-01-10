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
                                    checkedColor = Color(0xFF4CAF50),
                                    uncheckedColor = Color.Gray
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
                                    checkedColor = Color(0xFF4CAF50),
                                    uncheckedColor = Color.Gray
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
                                    checkedColor = Color(0xFF4CAF50),
                                    uncheckedColor = Color.Gray
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
                                    checkedColor = Color(0xFF4CAF50),
                                    uncheckedColor = Color.Gray
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
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Цветовая подсветка",
                                    color = Color(0xFFBBBBBB),
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )

                                // Foreground color
                                ColorPicker(
                                    label = "Цвет текста",
                                    value = foreground,
                                    onValueChange = { foreground = it }
                                )

                                // Background color
                                ColorPicker(
                                    label = "Цвет фона (опционально)",
                                    value = background,
                                    onValueChange = { background = it },
                                    allowEmpty = true
                                )

                                // Bold checkbox
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = bold,
                                        onCheckedChange = { bold = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color(0xFF4CAF50),
                                            uncheckedColor = Color.Gray
                                        )
                                    )
                                    Text(
                                        text = "Жирный шрифт (Bold)",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                }

                                // Preview
                                ColorPreview(
                                    foreground = foreground,
                                    background = background,
                                    bold = bold
                                )
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
private fun ColorPicker(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    allowEmpty: Boolean = false
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Популярные цвета
    val commonColors = listOf(
        "" to "Нет" to null,
        "#FFFFFF" to "Белый" to Color(0xFFFFFFFF),
        "#FF0000" to "Красный" to Color(0xFFFF0000),
        "#00FF00" to "Зелёный" to Color(0xFF00FF00),
        "#0000FF" to "Синий" to Color(0xFF0000FF),
        "#FFFF00" to "Жёлтый" to Color(0xFFFFFF00),
        "#FF00FF" to "Пурпурный" to Color(0xFFFF00FF),
        "#00FFFF" to "Голубой" to Color(0xFF00FFFF),
        "#FFA500" to "Оранжевый" to Color(0xFFFFA500),
        "#800080" to "Фиолетовый" to Color(0xFF800080),
        "#808080" to "Серый" to Color(0xFF808080),
        "#000000" to "Чёрный" to Color(0xFF000000)
    ).let { if (allowEmpty) it else it.drop(1) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = Color(0xFFBBBBBB),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Dropdown с популярными цветами
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { dropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        backgroundColor = Color(0xFF2D2D2D),
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = commonColors.find { it.first.first == value }?.first?.second ?: "Свой цвет",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                        Text("▼", fontSize = 10.sp)
                    }
                }

                MaterialTheme(
                    colors = darkColors(
                        surface = Color(0xFF2D2D2D),
                        onSurface = Color.White,
                        background = Color(0xFF2D2D2D)
                    )
                ) {
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        commonColors.forEach { (colorHex, colorName, colorPreview) ->
                            DropdownMenuItem(
                                onClick = {
                                    onValueChange(colorHex)
                                    dropdownExpanded = false
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = colorName,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = if (colorHex == value) Color(0xFF00FF00) else Color.White
                                    )
                                    if (colorPreview != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(colorPreview)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Поле для ручного ввода
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    backgroundColor = Color(0xFF2D2D2D),
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
                        text = "#RRGGBB",
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                },
                singleLine = true
            )
        }
    }
}

@Composable
private fun ColorPreview(
    foreground: String,
    background: String,
    bold: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Предпросмотр:",
            color = Color(0xFFBBBBBB),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )

        val fgColor = try {
            if (foreground.isNotBlank()) Color(android.graphics.Color.parseColor(foreground))
            else Color.White
        } catch (e: Exception) {
            Color.White
        }

        val bgColor = try {
            if (background.isNotBlank()) Color(android.graphics.Color.parseColor(background))
            else Color.Black
        } catch (e: Exception) {
            Color.Black
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .padding(8.dp)
        ) {
            Text(
                text = "Пример текста триггера",
                color = fgColor,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
            )
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
