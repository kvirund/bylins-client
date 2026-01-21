package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    availableProfiles: List<Pair<String?, String>> = emptyList(), // (id или null для базы, имя)
    initialTargetProfileId: String? = null, // Последний использованный профиль
    onDismiss: () -> Unit,
    onSave: (Trigger, String?) -> Unit // Trigger + targetProfileId
) {
    val isNewTrigger = trigger == null
    var selectedTargetProfileId by remember { mutableStateOf(initialTargetProfileId) }

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

                // Форма (прокручиваемая)
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Выбор целевого профиля (только для нового)
                    if (isNewTrigger && availableProfiles.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Добавить в:",
                                color = Color(0xFFBBBBBB),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            var targetExpanded by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(
                                    onClick = { targetExpanded = true },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        backgroundColor = Color(0xFF3D3D3D),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = availableProfiles.find { it.first == selectedTargetProfileId }?.second ?: "База",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(" ▼", fontSize = 10.sp)
                                }
                                DropdownMenu(
                                    expanded = targetExpanded,
                                    onDismissRequest = { targetExpanded = false }
                                ) {
                                    availableProfiles.forEach { (profileId, profileName) ->
                                        DropdownMenuItem(onClick = {
                                            selectedTargetProfileId = profileId
                                            targetExpanded = false
                                        }) {
                                            Text(profileName, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ID - только для редактирования (показываем readonly)
                    if (!isNewTrigger) {
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

                // Разделитель
                Divider(
                    color = Color(0xFF444444),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Кнопки (sticky, не скроллятся)
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

                                        // Создаём colorize если нужно
                                        val colorize = if (useColorize) {
                                            TriggerColorize(
                                                foreground = foreground.ifBlank { null },
                                                background = background.ifBlank { null },
                                                bold = bold
                                            )
                                        } else null

                                        // ID: для нового - генерируем UUID, для существующего - сохраняем
                                        val triggerId = if (isNewTrigger) {
                                            "tr-${java.util.UUID.randomUUID().toString().take(8)}"
                                        } else {
                                            id
                                        }

                                        // Name: если пустое - используем pattern
                                        val triggerName = name.ifBlank { pattern }

                                        // Создаём триггер
                                        val newTrigger = Trigger(
                                            id = triggerId,
                                            name = triggerName,
                                            pattern = regex,
                                            commands = commandsList,
                                            enabled = enabled,
                                            priority = priorityInt,
                                            gag = gag,
                                            once = once,
                                            colorize = colorize
                                        )

                                        onSave(newTrigger, selectedTargetProfileId)
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

data class ColorOption(
    val hex: String,
    val name: String,
    val preview: Color?
)

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
        ColorOption("", "Нет", null),
        ColorOption("#FFFFFF", "Белый", Color(0xFFFFFFFF)),
        ColorOption("#FF0000", "Красный", Color(0xFFFF0000)),
        ColorOption("#00FF00", "Зелёный", Color(0xFF00FF00)),
        ColorOption("#0000FF", "Синий", Color(0xFF0000FF)),
        ColorOption("#FFFF00", "Жёлтый", Color(0xFFFFFF00)),
        ColorOption("#FF00FF", "Пурпурный", Color(0xFFFF00FF)),
        ColorOption("#00FFFF", "Голубой", Color(0xFF00FFFF)),
        ColorOption("#FFA500", "Оранжевый", Color(0xFFFFA500)),
        ColorOption("#800080", "Фиолетовый", Color(0xFF800080)),
        ColorOption("#808080", "Серый", Color(0xFF808080)),
        ColorOption("#000000", "Чёрный", Color(0xFF000000))
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
                            text = commonColors.find { it.hex == value }?.name ?: "Свой цвет",
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
                        commonColors.forEach { colorOption ->
                            DropdownMenuItem(
                                onClick = {
                                    onValueChange(colorOption.hex)
                                    dropdownExpanded = false
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = colorOption.name,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = if (colorOption.hex == value) Color(0xFF00FF00) else Color.White
                                    )
                                    if (colorOption.preview != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(colorOption.preview)
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

/**
 * Парсит HEX цвет в Compose Color
 */
private fun parseHexColor(hex: String): Color? {
    return try {
        val cleanHex = hex.trim().removePrefix("#")
        if (cleanHex.length != 6) return null

        val r = cleanHex.substring(0, 2).toInt(16)
        val g = cleanHex.substring(2, 4).toInt(16)
        val b = cleanHex.substring(4, 6).toInt(16)

        Color(r, g, b)
    } catch (e: Exception) {
        null
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

        val fgColor = if (foreground.isNotBlank()) {
            parseHexColor(foreground) ?: Color.White
        } else {
            Color.White
        }

        val bgColor = if (background.isNotBlank()) {
            parseHexColor(background) ?: Color.Black
        } else {
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
