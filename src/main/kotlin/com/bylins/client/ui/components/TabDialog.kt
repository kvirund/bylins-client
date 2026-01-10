package com.bylins.client.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bylins.client.tabs.CaptureMode

@Composable
fun TabDialog(
    tab: com.bylins.client.tabs.Tab? = null,
    onDismiss: () -> Unit,
    onSave: (String, List<String>, CaptureMode) -> Unit
) {
    var name by remember { mutableStateOf(tab?.name ?: "") }
    var patterns by remember { mutableStateOf(tab?.filters?.joinToString("\n") { it.pattern.pattern } ?: "") }
    var captureMode by remember { mutableStateOf(tab?.captureMode ?: CaptureMode.COPY) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var patternsError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(600.dp)
                .heightIn(max = 700.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (tab == null) "Создать вкладку" else "Редактировать вкладку",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Название
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("Название") },
                    placeholder = { Text("Например: Каналы, Бой, Чат") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Режим захвата
                Text(
                    text = "Режим захвата",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = captureMode == CaptureMode.COPY,
                            onClick = { captureMode = CaptureMode.COPY }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("COPY - Копировать")
                            Text(
                                "Строка остается в главной вкладке и копируется в эту",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = captureMode == CaptureMode.MOVE,
                            onClick = { captureMode = CaptureMode.MOVE }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("MOVE - Переместить")
                            Text(
                                "Строка удаляется из главной вкладки и показывается только здесь",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = captureMode == CaptureMode.ONLY,
                            onClick = { captureMode = CaptureMode.ONLY }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("ONLY - Только эта вкладка")
                            Text(
                                "Строка показывается только в этой вкладке",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Паттерны фильтров
                OutlinedTextField(
                    value = patterns,
                    onValueChange = {
                        patterns = it
                        patternsError = null
                    },
                    label = { Text("Regex паттерны (по одному на строку)") },
                    placeholder = { Text("Например:\n^.+ говорит вам:\n^.+ шепчет вам:\n^\\[Болталка\\]") },
                    isError = patternsError != null,
                    supportingText = patternsError?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    maxLines = 10
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Строки, совпадающие с этими паттернами, будут направлены в эту вкладку",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

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

                            val patternList = patterns.lines()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }

                            if (patternList.isEmpty()) {
                                patternsError = "Введите хотя бы один паттерн"
                                hasErrors = true
                            } else {
                                // Проверяем валидность regex
                                for (pattern in patternList) {
                                    try {
                                        pattern.toRegex()
                                    } catch (e: Exception) {
                                        patternsError = "Неверный regex: $pattern"
                                        hasErrors = true
                                        break
                                    }
                                }
                            }

                            if (!hasErrors) {
                                onSave(name.trim(), patternList, captureMode)
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
