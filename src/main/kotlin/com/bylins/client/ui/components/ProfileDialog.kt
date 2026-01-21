package com.bylins.client.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bylins.client.connection.ConnectionProfile

/**
 * Диалог создания/редактирования профиля подключения
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDialog(
    profile: ConnectionProfile? = null,
    existingMapFiles: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (ConnectionProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var host by remember { mutableStateOf(profile?.host ?: "") }
    var port by remember { mutableStateOf(profile?.port?.toString() ?: "4000") }
    var encoding by remember { mutableStateOf(profile?.encoding ?: "UTF-8") }
    var mapFile by remember { mutableStateOf(profile?.mapFile ?: "maps.db") }
    var encodingMenuExpanded by remember { mutableStateOf(false) }
    var mapFileMenuExpanded by remember { mutableStateOf(false) }

    val availableEncodings = listOf("UTF-8", "windows-1251", "KOI8-R", "ISO-8859-1")

    // Список файлов карт: существующие + текущий (если он не в списке) + maps.db (default)
    val mapFileOptions = remember(existingMapFiles, mapFile) {
        val options = mutableSetOf("maps.db")
        options.addAll(existingMapFiles)
        if (mapFile.isNotBlank() && mapFile !in options) {
            options.add(mapFile)
        }
        options.toList().sorted()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(450.dp)
                .heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (profile == null) "Новый профиль" else "Редактировать профиль",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Прокручиваемый контент
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Dropdown для выбора кодировки
                ExposedDropdownMenuBox(
                    expanded = encodingMenuExpanded,
                    onExpandedChange = { encodingMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = encoding,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Кодировка") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = encodingMenuExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = encodingMenuExpanded,
                        onDismissRequest = { encodingMenuExpanded = false }
                    ) {
                        availableEncodings.forEach { enc ->
                            DropdownMenuItem(
                                text = { Text(enc) },
                                onClick = {
                                    encoding = enc
                                    encodingMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Поле для файла карты с возможностью выбора из существующих или ввода нового
                Box {
                    OutlinedTextField(
                        value = mapFile,
                        onValueChange = { mapFile = it },
                        label = { Text("Файл карты") },
                        supportingText = { Text("Введите имя или выберите из списка") },
                        trailingIcon = {
                            IconButton(onClick = { mapFileMenuExpanded = !mapFileMenuExpanded }) {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Выбрать из списка"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    DropdownMenu(
                        expanded = mapFileMenuExpanded,
                        onDismissRequest = { mapFileMenuExpanded = false }
                    ) {
                        mapFileOptions.forEach { file ->
                            DropdownMenuItem(
                                text = { Text(file) },
                                onClick = {
                                    mapFile = file
                                    mapFileMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                } // Конец прокручиваемого контента

                // Разделитель
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // Кнопки (sticky)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Отмена")
                    }

                    Button(
                        onClick = {
                            val portInt = port.toIntOrNull() ?: 4000
                            // Добавляем .db если нет расширения
                            val finalMapFile = if (mapFile.isNotBlank() && !mapFile.endsWith(".db")) {
                                "$mapFile.db"
                            } else if (mapFile.isBlank()) {
                                "maps.db"
                            } else {
                                mapFile
                            }
                            val newProfile = if (profile != null) {
                                profile.copy(
                                    name = name,
                                    host = host,
                                    port = portInt,
                                    encoding = encoding,
                                    mapFile = finalMapFile
                                )
                            } else {
                                ConnectionProfile(
                                    name = name,
                                    host = host,
                                    port = portInt,
                                    encoding = encoding,
                                    mapFile = finalMapFile
                                )
                            }
                            onSave(newProfile)
                        },
                        enabled = name.isNotBlank() && host.isNotBlank() && port.toIntOrNull() != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}
