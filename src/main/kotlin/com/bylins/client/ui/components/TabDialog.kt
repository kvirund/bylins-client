package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.bylins.client.tabs.CaptureMode
import com.bylins.client.tabs.TabFilter

/**
 * Представляет один паттерн в редакторе
 */
private data class PatternEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val pattern: String,
    val replacement: String = "",  // пустая строка = копировать как есть
    val matchWithColors: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabDialog(
    tab: com.bylins.client.tabs.Tab? = null,
    onDismiss: () -> Unit,
    onSave: (String, List<TabFilter>, CaptureMode) -> Unit
) {
    var name by remember { mutableStateOf(tab?.name ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var captureMode by remember { mutableStateOf(tab?.captureMode ?: CaptureMode.COPY) }

    // Инициализируем паттерны из существующей вкладки или пустым списком с одним элементом
    val patterns = remember {
        mutableStateListOf<PatternEntry>().apply {
            if (tab != null && tab.filters.isNotEmpty()) {
                addAll(tab.filters.map {
                    PatternEntry(
                        pattern = it.pattern.pattern,
                        replacement = it.replacement ?: "",
                        matchWithColors = it.matchWithColors
                    )
                })
            } else {
                add(PatternEntry(pattern = ""))
            }
        }
    }

    val dialogState = rememberDialogState(size = DpSize(650.dp, 600.dp))

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "",
        resizable = false,
        undecorated = true
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Заголовок
                Text(
                    text = if (tab == null) "Создать вкладку" else "Редактировать вкладку",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Название и режим в одну строку
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Название
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameError = null
                        },
                        label = { Text("Название") },
                        placeholder = { Text("Чат, Бой...") },
                        isError = nameError != null,
                        supportingText = nameError?.let { { Text(it) } },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    // Режим захвата - dropdown
                    var modeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = modeExpanded,
                        onExpandedChange = { modeExpanded = it },
                        modifier = Modifier.width(180.dp)
                    ) {
                        OutlinedTextField(
                            value = when (captureMode) {
                                CaptureMode.COPY -> "Копировать"
                                CaptureMode.MOVE -> "Перемещать"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Режим") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                            modifier = Modifier.menuAnchor(),
                            singleLine = true
                        )

                        ExposedDropdownMenu(
                            expanded = modeExpanded,
                            onDismissRequest = { modeExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Копировать") },
                                onClick = {
                                    captureMode = CaptureMode.COPY
                                    modeExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Перемещать") },
                                onClick = {
                                    captureMode = CaptureMode.MOVE
                                    modeExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Заголовок паттернов
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Regex паттерны",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(
                        onClick = { patterns.add(PatternEntry(pattern = "")) }
                    ) {
                        Text("+ Добавить")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Список паттернов
                val patternsScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 300.dp)
                        .verticalScroll(patternsScrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    patterns.forEachIndexed { index, entry ->
                        key(entry.id) {
                        PatternRow(
                            entry = entry,
                            onPatternChange = { newPattern ->
                                patterns[index] = entry.copy(pattern = newPattern, error = null)
                            },
                            onReplacementChange = { newReplacement ->
                                patterns[index] = entry.copy(replacement = newReplacement)
                            },
                            onMatchWithColorsChange = { matchWithColors ->
                                patterns[index] = entry.copy(matchWithColors = matchWithColors)
                            },
                            onDelete = {
                                if (patterns.size > 1) {
                                    patterns.removeAt(index)
                                }
                            },
                            canDelete = patterns.size > 1
                        )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Подсказка с примерами
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Примеры паттернов (без цветов):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ExampleRow("говорит вам", "Вася говорит вам: привет")
                        ExampleRow("\\[Болталка\\]", "[Болталка] Кто-то: текст")
                        ExampleRow("нанес.*урон", "нанес колоссальный урон")
                        ExampleRow("^Вы убили", "Вы убили монстра (только в начале)")

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "С цветами (включить чекбокс):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ExampleRow("\\x1b\\[31m", "красный текст")
                        ExampleRow("\\x1b\\[1;32m", "яркий зелёный (1=bold)")
                        ExampleRow("\\x1b\\[\\d+m", "любой цвет")
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            """Цвета: 30=чёрный, 31=красный, 32=зелёный, 33=жёлтый
       34=синий, 35=пурпурный, 36=голубой, 37=белый
Яркие: 90-97 или 1;30-1;37""",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "Замена:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            """\$0 = вся строка, \$1, \$2... = группы захвата ()
Пример: ^(.+) говорит вам: (.+)\$  →  \$1: \$2
  "Вася говорит вам: привет" станет "Вася: привет"""",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "^ = начало строки, \$ = конец строки",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Кнопки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            // Валидация
                            var hasErrors = false

                            if (name.isBlank()) {
                                nameError = "Введите название"
                                hasErrors = true
                            }

                            // Проверяем паттерны
                            val validPatterns = mutableListOf<PatternEntry>()
                            for (i in patterns.indices) {
                                val entry = patterns[i]
                                val trimmed = entry.pattern.trim()
                                if (trimmed.isEmpty()) {
                                    continue // Пропускаем пустые
                                }
                                try {
                                    trimmed.toRegex()
                                    validPatterns.add(entry.copy(pattern = trimmed))
                                } catch (e: Exception) {
                                    patterns[i] = entry.copy(error = "Неверный regex")
                                    hasErrors = true
                                }
                            }

                            if (validPatterns.isEmpty()) {
                                // Помечаем первый паттерн ошибкой
                                if (patterns.isNotEmpty()) {
                                    patterns[0] = patterns[0].copy(error = "Введите хотя бы один паттерн")
                                }
                                hasErrors = true
                            }

                            if (!hasErrors) {
                                val filters = validPatterns.map { entry ->
                                    TabFilter(
                                        pattern = entry.pattern.toRegex(),
                                        replacement = entry.replacement.ifBlank { null },
                                        matchWithColors = entry.matchWithColors
                                    )
                                }
                                onSave(name.trim(), filters, captureMode)
                            }
                        }
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExampleRow(pattern: String, example: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Паттерн в рамке
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = pattern,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Text(
            text = " → ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Пример что ловит
        Text(
            text = example,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun PatternRow(
    entry: PatternEntry,
    onPatternChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onMatchWithColorsChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (entry.error != null)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Паттерн и замена в одну строку
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Поле ввода паттерна
            OutlinedTextField(
                value = entry.pattern,
                onValueChange = onPatternChange,
                label = { Text("Паттерн", maxLines = 1) },
                placeholder = { Text("Regex") },
                isError = entry.error != null,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            )

            // Стрелка
            Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Поле замены
            OutlinedTextField(
                value = entry.replacement,
                onValueChange = onReplacementChange,
                label = { Text("Замена", maxLines = 1) },
                placeholder = { Text("\$1: \$2") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            )

            // Кнопка удаления
            if (canDelete) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("✕", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
            }
        }

        // Чекбокс и ошибка
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = entry.matchWithColors,
                onCheckedChange = onMatchWithColorsChange,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "С ANSI-цветами",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Ошибка справа
            if (entry.error != null) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    entry.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
