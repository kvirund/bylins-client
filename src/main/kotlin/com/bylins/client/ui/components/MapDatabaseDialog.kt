package com.bylins.client.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bylins.client.ClientState
import com.bylins.client.mapper.MapInfo
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MapDatabaseDialog(
    clientState: ClientState,
    onDismiss: () -> Unit
) {
    var mapList by remember { mutableStateOf<List<MapInfo>>(emptyList()) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showLoadConfirm by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("") }

    // Загружаем список карт при открытии
    LaunchedEffect(Unit) {
        mapList = clientState.listMapsInDatabase()
    }

    fun refreshMapList() {
        mapList = clientState.listMapsInDatabase()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(600.dp)
                .height(500.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Заголовок
                Text(
                    text = "База данных карт",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Сообщение о статусе
                if (statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Кнопки действий
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showSaveDialog = true }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Сохранить текущую")
                    }

                    Button(
                        onClick = { refreshMapList() }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Обновить")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Список карт
                if (mapList.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Нет сохраненных карт",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(mapList) { mapInfo ->
                            MapInfoCard(
                                mapInfo = mapInfo,
                                onLoad = { showLoadConfirm = mapInfo.name },
                                onDelete = { showDeleteConfirm = mapInfo.name }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Кнопка закрытия
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Закрыть")
                    }
                }
            }
        }
    }

    // Диалог сохранения
    if (showSaveDialog) {
        SaveMapDialog(
            onSave = { name, description ->
                val success = clientState.saveMapToDatabase(name, description)
                statusMessage = if (success) {
                    refreshMapList()
                    "Карта '$name' успешно сохранена"
                } else {
                    "Ошибка сохранения карты"
                }
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    // Подтверждение загрузки
    showLoadConfirm?.let { mapName ->
        AlertDialog(
            onDismissRequest = { showLoadConfirm = null },
            title = { Text("Загрузить карту?") },
            text = { Text("Текущая карта будет заменена картой '$mapName'. Продолжить?") },
            confirmButton = {
                Button(
                    onClick = {
                        val success = clientState.loadMapFromDatabase(mapName)
                        statusMessage = if (success) {
                            "Карта '$mapName' загружена"
                        } else {
                            "Ошибка загрузки карты"
                        }
                        showLoadConfirm = null
                    }
                ) {
                    Text("Загрузить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoadConfirm = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Подтверждение удаления
    showDeleteConfirm?.let { mapName ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Удалить карту?") },
            text = { Text("Вы уверены, что хотите удалить карту '$mapName'? Это действие нельзя отменить.") },
            confirmButton = {
                Button(
                    onClick = {
                        val success = clientState.deleteMapFromDatabase(mapName)
                        statusMessage = if (success) {
                            refreshMapList()
                            "Карта '$mapName' удалена"
                        } else {
                            "Ошибка удаления карты"
                        }
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun MapInfoCard(
    mapInfo: MapInfo,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mapInfo.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (mapInfo.description.isNotEmpty()) {
                        Text(
                            text = mapInfo.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Комнат: ${mapInfo.roomCount} • Обновлена: ${dateFormat.format(Date(mapInfo.updatedAt * 1000))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onLoad) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Загрузить",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Удалить",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SaveMapDialog(
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сохранить карту") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название карты") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание (необязательно)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, description) },
                enabled = name.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
